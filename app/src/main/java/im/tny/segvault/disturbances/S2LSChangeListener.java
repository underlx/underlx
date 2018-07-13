package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.model.Trip;
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
    private Handler stateTickHandler = new Handler();

    public S2LSChangeListener(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setMainService(@Nullable MainService mainService) {
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
            wfc.setScanInterval(TimeUnit.SECONDS.toMillis(10));
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
        path.addPathChangedListener(new Path.OnPathChangedListener() {
            private Station prevEndStation = null;

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
                if (path.getDirection() == null) {
                    new SubmitRealtimeLocationTask().execute(path.getCurrentStop().getStation().getId());
                } else {
                    new SubmitRealtimeLocationTask().execute(
                            path.getCurrentStop().getStation().getId(),
                            path.getDirection().getStation().getId());
                }
            }
        });
        new SubmitRealtimeLocationTask().execute(path.getCurrentStop().getStation().getId());
        updateRouteNotification(s2ls);
    }

    @Override
    public void onTripEnded(S2LS s2ls, Path path) {
        String tripId = Trip.persistConnectionPath(path);
        Intent intent = new Intent(ACTION_CURRENT_TRIP_ENDED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
        bm.sendBroadcast(intent);

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
        stateTickHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doTick(state);
                state.tick();
            }
        }, state.getPreferredTickIntervalMillis());
    }

    private boolean checkStopForeground(S2LS s2ls) {
        if(mainService != null) {
            return mainService.checkStopForeground(s2ls);
        }
        return true;
    }

    private void updateRouteNotification(S2LS s2ls) {
        if(mainService != null) {
            mainService.updateRouteNotification(s2ls);
        }
    }

    private void updateRouteNotification(S2LS s2ls, boolean b) {
        if(mainService != null) {
            mainService.updateRouteNotification(s2ls, b);
        }
    }

    private static class SubmitRealtimeLocationTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            API.RealtimeLocationRequest r = new API.RealtimeLocationRequest();
            r.s = strings[0];
            if (strings.length > 1) {
                r.d = strings[1];
            }
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