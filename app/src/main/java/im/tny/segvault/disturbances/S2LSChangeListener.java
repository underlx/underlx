package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.ui.activity.TripCorrectionActivity;
import im.tny.segvault.s2ls.InNetworkState;
import im.tny.segvault.s2ls.NearNetworkState;
import im.tny.segvault.s2ls.OffNetworkState;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.State;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.s2ls.routing.Step;
import im.tny.segvault.subway.Station;

import static android.content.Context.MODE_PRIVATE;

public class S2LSChangeListener implements S2LS.EventListener {
    private Context context;
    private MainService mainService;
    private int mqttPartyID;

    public S2LSChangeListener(Context context) {
        this.context = context.getApplicationContext();
    }

    private Handler stateTickHandler = new Handler(Looper.getMainLooper());

    public void setMainService(MainService mainService) {
        this.mainService = mainService;
    }

    @Override
    public void onStateChanged(final S2LS loc) {
        Log.d("onStateChanged", "State changed");

        stateTickHandler.removeCallbacksAndMessages(null);
        if (loc.getState().getPreferredTickIntervalMillis() != 0) {
            doTick(loc.getState());
        }

        WiFiChecker wfc = Coordinator.get(context).getWiFiChecker();

        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        boolean locationEnabled = sharedPref.getBoolean(PreferenceNames.LocationEnable, true);

        if (loc.getState() instanceof InNetworkState) {
            int interval = 10;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // "Each foreground app can scan four times in a 2-minute period."
                interval = 30;
            }
            wfc.setScanInterval(TimeUnit.SECONDS.toMillis(interval));
            if (locationEnabled) {
                wfc.startScanning();
            }
            if (loc.getCurrentTrip() != null) {
                updateRouteNotification(loc);
            }
        } else if (loc.getState() instanceof NearNetworkState) {
            wfc.setScanInterval(TimeUnit.MINUTES.toMillis(1));
            if (locationEnabled)
                wfc.startScanningIfWiFiEnabled();
            checkStopForeground(loc);
        } else if (loc.getState() instanceof OffNetworkState) {
            // wfc.stopScanning(); // TODO only enable this when there are locators besides WiFi
            wfc.setScanInterval(TimeUnit.MINUTES.toMillis(1));
            if (locationEnabled)
                wfc.startScanningIfWiFiEnabled();
            checkStopForeground(loc);
        }

        Intent intent = new Intent(ACTION_S2LS_STATUS_CHANGED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);
    }

    @Override
    public void onTripStarted(final S2LS s2ls) {
        Path path = s2ls.getCurrentTrip();
        MqttManager mqtt = Coordinator.get(context).getMqttManager();

        final String origTopic = mqtt.getVehicleETAsTopicForStation(path.getCurrentStop().getStation());
        path.addPathChangedListener(new Path.OnPathChangedListener() {
            private Station prevEndStation = null;
            private String prevSubscribedTopic = origTopic;

            @Override
            public void onPathChanged(Path path) {
                Log.d("onPathChanged", "Path changed");

                Intent intent = new Intent(ACTION_CURRENT_TRIP_UPDATED);
                LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
                bm.sendBroadcast(intent);

                boolean highPrioNotif = false;
                if (s2ls.getCurrentTargetRoute() != null) {
                    Step nextStep = s2ls.getCurrentTargetRoute().getNextStep(path);
                    highPrioNotif = path.getEndVertex().getStation() != prevEndStation &&
                            path.getEndVertex().getStation() == nextStep.getStation();
                }
                prevEndStation = path.getEndVertex().getStation();
                updateRouteNotification(s2ls, highPrioNotif);
            }

            @Override
            public void onNewStationEnteredNow(Path path) {
                MqttManager mqtt = Coordinator.get(context).getMqttManager();
                if (prevSubscribedTopic != null) {
                    mqtt.unsubscribe(mqttPartyID, prevSubscribedTopic);
                }
                prevSubscribedTopic = mqtt.getVehicleETAsTopicForStation(path.getCurrentStop().getStation());
                mqtt.subscribe(mqttPartyID, prevSubscribedTopic);
                new SubmitRealtimeLocationTask(S2LSChangeListener.this,
                        path.getCurrentStop().getStation(),
                        path.getDirection() == null ? null : path.getDirection().getStation())
                        .executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
            }
        });
        new SubmitRealtimeLocationTask(S2LSChangeListener.this, path.getCurrentStop().getStation(), null)
                .executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
        mqttPartyID = mqtt.connect(origTopic);
        updateRouteNotification(s2ls);
    }

    @Override
    public void onTripEnded(S2LS s2ls, Path path) {
        String tripId = Trip.persistConnectionPath(Coordinator.get(context).getDB(), path);
        if (tripId == null) {
            // trip was not saved for some reason (maybe it overlaps with an existing trip)
            return;
        }
        Intent intent = new Intent(ACTION_CURRENT_TRIP_ENDED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);

        Coordinator.get(context).getMqttManager().disconnect(mqttPartyID);
        if (!checkStopForeground(s2ls)) {
            updateRouteNotification(s2ls);
        }

        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        boolean openTripCorrection = sharedPref.getBoolean(PreferenceNames.AutoOpenTripCorrection, false);
        boolean openVisitCorrection = sharedPref.getBoolean(PreferenceNames.AutoOpenVisitCorrection, false);
        if (openTripCorrection && (path.getEdgeList().size() > 0 || openVisitCorrection)) {
            intent = new Intent(context, TripCorrectionActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(TripCorrectionActivity.EXTRA_NETWORK_ID, s2ls.getNetwork().getId());
            intent.putExtra(TripCorrectionActivity.EXTRA_TRIP_ID, tripId);
            intent.putExtra(TripCorrectionActivity.EXTRA_IS_STANDALONE, true);
            context.startActivity(intent);
        }
    }

    @Override
    public void onRouteProgrammed(S2LS s2ls, Route route) {
        updateRouteNotification(s2ls);
    }

    @Override
    public void onRouteStarted(S2LS s2ls, Path path, Route route) {
        Log.d("onRouteStarted", "Route started");
        if (!checkStopForeground(s2ls)) {
            updateRouteNotification(s2ls);
        }
    }

    @Override
    public void onRouteMistake(S2LS s2ls, Path path, Route route) {
        Log.d("onRouteMistake", "Route mistake");
        // recalculate route with current station as origin station
        s2ls.setCurrentTargetRoute(
                Route.calculate(
                        s2ls.getNetwork(),
                        path.getEndVertex().getStation(),
                        route.getTarget()), true);
        if (!checkStopForeground(s2ls)) {
            updateRouteNotification(s2ls);
        }
    }

    @Override
    public void onRouteCancelled(S2LS s2ls, Route route) {
        Log.d("onRouteCancelled", "Route cancelled");
        if (!checkStopForeground(s2ls)) {
            updateRouteNotification(s2ls);
        }
        Intent intent = new Intent(ACTION_NAVIGATION_ENDED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);
    }

    @Override
    public void onRouteCompleted(S2LS s2ls, Path path, Route route) {
        Log.d("onRouteCompleted", "Route completed");
        if (!checkStopForeground(s2ls)) {
            updateRouteNotification(s2ls);
        }
        Intent intent = new Intent(ACTION_NAVIGATION_ENDED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);
    }

    private void doTick(final State state) {
        stateTickHandler.postDelayed(() -> {
            doTick(state);
            state.tick();
        }, state.getPreferredTickIntervalMillis());
    }

    private boolean checkStopForeground(S2LS s2ls) {
        if (mainService != null) {
            return mainService.checkStopForeground(s2ls);
        } else {
            requestStartMainService();
        }
        return true;
    }

    private void updateRouteNotification(S2LS s2ls) {
        if (mainService != null) {
            mainService.updateRouteNotification(s2ls);
        } else {
            requestStartMainService();
        }
    }

    private void updateRouteNotification(S2LS s2ls, boolean b) {
        if (mainService != null) {
            mainService.updateRouteNotification(s2ls, b);
        } else {
            requestStartMainService();
        }
    }

    private void requestStartMainService() {
        Intent startIntent = new Intent(context, MainService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent);
        } else {
            context.startService(startIntent);
        }
    }

    private static class SubmitRealtimeLocationTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<S2LSChangeListener> parentRef;
        private Station station;
        private Station direction;

        SubmitRealtimeLocationTask(S2LSChangeListener parent, Station station, Station direction) {
            parentRef = new WeakReference<>(parent);
            this.station = station;
            this.direction = direction;
        }

        private API.RealtimeLocationRequest r;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            r = new API.RealtimeLocationRequest();
            r.s = station.getId();
            if (direction != null) {
                r.d = direction.getId();
            }

            S2LSChangeListener parent = parentRef.get();
            if (parent == null) {
                return;
            }

            try {
                byte[] payload = API.getInstance().getMapper().writeValueAsBytes(r);
                MqttManager mqttManager = Coordinator.get(parent.context).getMqttManager();
                mqttManager.publish(parent.mqttPartyID, mqttManager.getLocationPublishTopicForNetwork(station.getNetwork()), payload);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... nothing) {
            try {
                API.getInstance().postRealtimeLocation(r);
            } catch (APIException e) {
                // oh well...
            }
            return null;
        }
    }

    public static final String ACTION_CURRENT_TRIP_UPDATED = "im.tny.segvault.disturbances.action.trip.current.updated";
    public static final String ACTION_CURRENT_TRIP_ENDED = "im.tny.segvault.disturbances.action.trip.current.ended";
    public static final String ACTION_S2LS_STATUS_CHANGED = "im.tny.segvault.disturbances.action.s2ls.status.changed";
    public static final String ACTION_NAVIGATION_ENDED = "im.tny.segvault.disturbances.action.navigation.ended";
}