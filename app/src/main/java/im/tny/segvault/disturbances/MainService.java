package im.tny.segvault.disturbances;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.model.Feedback;
import im.tny.segvault.disturbances.model.RStation;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.activity.ReportActivity;
import im.tny.segvault.disturbances.ui.activity.TripCorrectionActivity;
import im.tny.segvault.s2ls.InNetworkState;
import im.tny.segvault.s2ls.NearNetworkState;
import im.tny.segvault.s2ls.OffNetworkState;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.State;
import im.tny.segvault.s2ls.routing.ChangeLineStep;
import im.tny.segvault.s2ls.routing.EnterStep;
import im.tny.segvault.s2ls.routing.ExitStep;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.s2ls.routing.Step;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_BACKGROUND_ID;
import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_REALTIME_HIGH_ID;
import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_REALTIME_ID;

public class MainService extends Service {
    private API api;

    private Date creationDate = null;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private Realm realmForListeners;

    private Handler stateTickHandler = new Handler();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public MainService getService() {
            // Return this instance of MainService so clients can call public methods
            return MainService.this;
        }
    }

    public MainService() {

    }

    @Override
    public void onCreate() {
        creationDate = new Date();
        API.getInstance().setContext(getApplicationContext());
        PreferenceManager.setDefaultValues(this.getApplicationContext(), R.xml.notif_settings, false);
        api = API.getInstance();

        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.registerOnSharedPreferenceChangeListener(generalPrefsListener);
        if (new Date().getTime() - sharedPref.getLong(PreferenceNames.LastAutoTopologyUpdateCheck, 0) > TimeUnit.HOURS.toMillis(2)) {
            SharedPreferences.Editor e = sharedPref.edit();
            e.putLong(PreferenceNames.LastAutoTopologyUpdateCheck, new Date().getTime());
            e.apply();
            Coordinator.get(this).getMapManager().checkForTopologyUpdates();
        }

        realmForListeners = Application.getDefaultRealmInstance(this);
        tripRealmResults = realmForListeners.where(Trip.class).findAll();
        tripRealmResults.addChangeListener(tripRealmChangeListener);
        feedbackRealmResults = realmForListeners.where(Feedback.class).findAll();
        feedbackRealmResults.addChangeListener(feedbackRealmChangeListener);
        favStationsRealmResults = realmForListeners.where(RStation.class).equalTo("favorite", true).findAll();
        favStationsRealmResults.addChangeListener(favStationsRealmChangeListener);

        Coordinator.get(this).registerMainService(this);
    }

    @Override
    public void onDestroy() {
        Coordinator.get(this).unregisterMainService();
        Coordinator.get(this).getWiFiChecker().stopScanning();
        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.unregisterOnSharedPreferenceChangeListener(generalPrefsListener);
        tripRealmResults.removeChangeListener(tripRealmChangeListener);
        feedbackRealmResults.removeChangeListener(feedbackRealmChangeListener);
        favStationsRealmResults.removeChangeListener(favStationsRealmChangeListener);
        realmForListeners.close();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        boolean permanentForeground = sharedPref.getBoolean(PreferenceNames.PermanentForeground, false);

        if (!shouldBeInForeground(Coordinator.get(MainService.this).getS2LS(MapManager.PRIMARY_NETWORK_ID))) {
            if (permanentForeground) {
                startPermanentForeground();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startTemporaryForeground();
            }
        }

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_END_TRIP: {
                    final String network = intent.getStringExtra(EXTRA_TRIP_NETWORK);
                    S2LS loc = Coordinator.get(MainService.this).getS2LS(network);
                    if (loc != null) {
                        loc.endCurrentTrip();
                    }
                    break;
                }
                case ACTION_END_NAVIGATION: {
                    final String network = intent.getStringExtra(EXTRA_NAVIGATION_NETWORK);
                    S2LS loc = Coordinator.get(MainService.this).getS2LS(network);
                    if (loc != null) {
                        loc.setCurrentTargetRoute(null, false);
                    }
                    break;
                }
            }
        }

        Coordinator.get(this).reloadFCMsubscriptions();

        return Service.START_STICKY;
    }

    // DEBUG:
    public String dumpDebugInfo() {
        WiFiChecker wfc = Coordinator.get(MainService.this).getWiFiChecker();

        String s = "Service created on " + creationDate.toString();
        s += (wfc.isScanning() ? String.format(". WFC scanning every %d s", wfc.getScanInterval() / 1000) : ". WFC not scanning") + "\n";
        for (Network n : Coordinator.get(this).getMapManager().getNetworks()) {
            S2LS loc = Coordinator.get(this).getS2LS(n.getId());
            s += "Network " + n.getNames("en")[0] + "\n";
            s += "State machine: " + loc.getState().toString() + "\n";
            s += String.format("\tIn network? %b\n\tNear network? %b\n", loc.inNetwork(), loc.nearNetwork());
            if (loc.getState() instanceof InNetworkState) {
                s += "\tPossible stops:\n";
                for (Stop stop : loc.getLocation().vertexSet()) {
                    s += String.format("\t\t%s (%s)\n", stop.getStation().getName(), stop.getLine().getNames("en")[0]);
                }
                s += "\tPath:\n";
                if (loc.getCurrentTrip() != null) {
                    for (Connection c : loc.getCurrentTrip().getEdgeList()) {
                        s += String.format("\t\t%s -> %s\n", c.getSource().toString(), c.getTarget().toString());
                    }
                    if (loc.getCurrentTargetRoute() != null) {
                        s += String.format("\t\tCurrent path complies? %b\n",
                                loc.getCurrentTargetRoute().checkPathCompliance(loc.getCurrentTrip()));
                    }
                }
            }
        }

        try {
            API.AuthTest authTest = API.getInstance().getAuthTest();
            s += "API auth test result: " + authTest.result + "\n";
            s += "API key as perceived by server: " + authTest.key + "\n";
        } catch (APIException e) {
            e.printStackTrace();
            s += "Error testing API authentication\n";
        }

        Coordinator.get(this).getSynchronizer().asyncSync();

        return s;
    }

    // END OF DEBUG


    public Handler getStateTickHandler() {
        return stateTickHandler;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private static final int PERMANENT_FOREGROUND_NOTIFICATION_ID = -101;
    private static final int ROUTE_NOTIFICATION_ID = -100;
    public static final String ACTION_END_NAVIGATION = "im.tny.segvault.disturbances.action.route.current.end";
    public static final String EXTRA_NAVIGATION_NETWORK = "im.tny.segvault.disturbances.extra.route.current.end.network";
    public static final String ACTION_END_TRIP = "im.tny.segvault.disturbances.action.trip.current.end";
    public static final String EXTRA_TRIP_NETWORK = "im.tny.segvault.disturbances.extra.trip.current.end.network";

    void updateRouteNotification(S2LS loc) {
        updateRouteNotification(loc, false);
    }

    private class RouteNotificationData {
        CharSequence title;
        CharSequence summary;
        List<Integer> actions = new ArrayList<>();
    }

    RouteNotificationData lastRouteNotificationData;

    private boolean shouldUpdateRouteNotification(RouteNotificationData newData) {
        if (lastRouteNotificationData == null) {
            lastRouteNotificationData = newData;
            return true;
        }

        if (!lastRouteNotificationData.summary.equals(newData.summary) ||
                !lastRouteNotificationData.title.equals(newData.title) ||
                !lastRouteNotificationData.actions.containsAll(newData.actions) ||
                !newData.actions.containsAll(lastRouteNotificationData.actions)) {
            lastRouteNotificationData = newData;
            return true;
        }
        return false;
    }

    void updateRouteNotification(S2LS loc, boolean highPriorityNotification) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_INITIAL_FRAGMENT, "nav_home");
        PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(),
                intent, 0);

        Path currentPath = loc.getCurrentTrip();
        Route currentRoute = loc.getCurrentTargetRoute();

        if (currentPath == null && currentRoute == null) {
            Log.e("MainService", "Attempt to create notification when there's no path or planned route");
            return;
        }

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        CharSequence title = "";
        List<CharSequence> statusLines = new ArrayList<>();
        int color = -1;
        if (currentRoute != null) {
            RouteUtil.RouteStepInfo info = RouteUtil.buildRouteStepInfo(this, currentRoute, currentPath);
            inboxStyle.setSummaryText(info.summary);
            title = info.title;
            statusLines.addAll(info.bodyLines);
            color = info.color;
        }

        if (currentPath != null) {
            Stop curStop = currentPath.getCurrentStop();
            if (color == -1) {
                color = curStop.getLine().getColor();
            }
            if (currentRoute != null) {
                statusLines.add(String.format(getString(R.string.notif_route_current_station), curStop.getStation().getName()));
            } else {
                title = curStop.getStation().getName();
            }
            if (currentPath.isWaitingFirstTrain()) {
                statusLines.add(getString(R.string.notif_route_waiting));
            } else {
                Stop direction = currentPath.getDirection();
                Stop next = currentPath.getNextStop();
                if (direction != null && next != null) {
                    statusLines.add(String.format(getString(R.string.notif_route_next_station), next.getStation().getName()));
                    if (currentRoute == null) {
                        statusLines.add(String.format(getString(R.string.notif_route_direction), direction.getStation().getName()));
                    }
                } else {
                    statusLines.add(getString(R.string.notif_route_left_station));
                }
            }
        }

        CharSequence singleLineStatus = "";
        for (CharSequence s : statusLines) {
            inboxStyle.addLine(s);
            singleLineStatus = TextUtils.concat(singleLineStatus, s) + " | ";
        }
        singleLineStatus = singleLineStatus.subSequence(0, singleLineStatus.length() - 3);

        String channel = NOTIF_CHANNEL_REALTIME_ID;
        if (highPriorityNotification) {
            channel = NOTIF_CHANNEL_REALTIME_HIGH_ID;
        }
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channel)
                .setStyle(inboxStyle)
                .setColor(color)
                .setContentTitle(title)
                .setContentText(singleLineStatus)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true);

        if (highPriorityNotification) {
            stopForeground(true);
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
        } else {
            notificationBuilder.setSound(null);
        }

        RouteNotificationData data = new RouteNotificationData();
        data.summary = singleLineStatus;
        data.title = title;

        if (currentRoute != null) {
            notificationBuilder.setSmallIcon(R.drawable.ic_navigation_white_24dp);
            Intent stopIntent = new Intent(this, MainService.class);
            stopIntent.setAction(ACTION_END_NAVIGATION);
            stopIntent.putExtra(EXTRA_NAVIGATION_NETWORK, loc.getNetwork().getId());
            PendingIntent pendingStopIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(),
                    stopIntent, 0);
            notificationBuilder.addAction(R.drawable.ic_close_black_24dp, getString(R.string.notif_route_end_navigation), pendingStopIntent);
            data.actions.add(1);
        } else {
            notificationBuilder.setSmallIcon(R.drawable.ic_trip_notif);
            if (loc.canRequestEndOfTrip()) {
                Intent stopIntent = new Intent(this, MainService.class);
                stopIntent.setAction(ACTION_END_TRIP);
                stopIntent.putExtra(EXTRA_TRIP_NETWORK, loc.getNetwork().getId());
                PendingIntent pendingStopIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(),
                        stopIntent, 0);
                notificationBuilder.addAction(R.drawable.ic_train_off_black_24dp, getString(R.string.notif_route_end_trip), pendingStopIntent);
                data.actions.add(2);
            }
            Intent reportIntent = new Intent(getApplicationContext(), ReportActivity.class);
            reportIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            reportIntent.putExtra(ReportActivity.EXTRA_IS_STANDALONE, true);

            PendingIntent pendingReportIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(),
                    reportIntent, 0);
            notificationBuilder.addAction(R.drawable.ic_menu_announcement, getString(R.string.notif_route_report), pendingReportIntent);
            data.actions.add(3);
        }


        if(highPriorityNotification) {
            lastRouteNotificationData = data;
            proxyStartForeground(ROUTE_NOTIFICATION_ID, notificationBuilder.build());
            Log.d("MainService", "Updated route notification (high priority)");
        } else if(shouldUpdateRouteNotification(data)) {
            proxyStartForeground(ROUTE_NOTIFICATION_ID, notificationBuilder.build());
            Log.d("MainService", "Updated route notification");
        }
    }

    private boolean shouldBeInForeground(S2LS loc) {
        return loc != null && (loc.getCurrentTargetRoute() != null || loc.getState() instanceof InNetworkState);
    }

    boolean checkStopForeground(S2LS loc) {
        if (!shouldBeInForeground(loc)) {
            SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
            boolean permanentForeground = sharedPref.getBoolean(PreferenceNames.PermanentForeground, false);
            if (permanentForeground) {
                startPermanentForeground();
            } else {
                stopForeground(true);
            }
            return true;
        }
        return false;
    }

    private void startPermanentForeground() {
        Intent intent = new Intent(MainService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(MainService.this, 0, intent, 0);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(MainService.this, NOTIF_CHANNEL_BACKGROUND_ID)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_permanent_foreground_description))
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_sleep_white_24dp)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true);
        proxyStartForeground(PERMANENT_FOREGROUND_NOTIFICATION_ID, notificationBuilder.build());
    }

    private boolean isTempForeground = false;
    private Handler tempForegroundHandler = new Handler();

    private void proxyStartForeground(int id, Notification notification) {
        isTempForeground = false;
        startForeground(id, notification);
    }

    private void startTemporaryForeground() {
        Intent intent = new Intent(MainService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(MainService.this, 0, intent, 0);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(MainService.this, NOTIF_CHANNEL_BACKGROUND_ID)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getString(R.string.app_name))
                .setContentText("")
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_sleep_white_24dp)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true);

        isTempForeground = true;
        startForeground(PERMANENT_FOREGROUND_NOTIFICATION_ID, notificationBuilder.build());
        tempForegroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isTempForeground) {
                    stopForeground(true);
                    isTempForeground = false;
                }
            }
        }, 10000);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener generalPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            switch (key) {
                case PreferenceNames.LocationEnable:
                    if (prefs.getBoolean(PreferenceNames.LocationEnable, true))
                        Coordinator.get(MainService.this).getWiFiChecker().startScanningIfWiFiEnabled();
                    else
                        Coordinator.get(MainService.this).getWiFiChecker().stopScanning();
                    break;
                case PreferenceNames.PermanentForeground:
                    checkStopForeground(Coordinator.get(MainService.this).getS2LS(MapManager.PRIMARY_NETWORK_ID));
                    break;
            }
        }
    };

    public static final String ACTION_TRIP_REALM_UPDATED = "im.tny.segvault.disturbances.action.realm.trip.updated";

    private RealmResults<Trip> tripRealmResults;
    private final RealmChangeListener<RealmResults<Trip>> tripRealmChangeListener = new RealmChangeListener<RealmResults<Trip>>() {
        @Override
        public void onChange(RealmResults<Trip> element) {
            Intent intent = new Intent(ACTION_TRIP_REALM_UPDATED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        }
    };

    public static final String ACTION_FEEDBACK_REALM_UPDATED = "im.tny.segvault.disturbances.action.realm.feedback.updated";

    private RealmResults<Feedback> feedbackRealmResults;
    private final RealmChangeListener<RealmResults<Feedback>> feedbackRealmChangeListener = new RealmChangeListener<RealmResults<Feedback>>() {
        @Override
        public void onChange(RealmResults<Feedback> element) {
            Intent intent = new Intent(ACTION_FEEDBACK_REALM_UPDATED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        }
    };

    public static final String ACTION_FAVORITE_STATIONS_UPDATED = "im.tny.segvault.disturbances.action.realm.station.favorite.updated";

    private RealmResults<RStation> favStationsRealmResults;
    private final RealmChangeListener<RealmResults<RStation>> favStationsRealmChangeListener = new RealmChangeListener<RealmResults<RStation>>() {
        @Override
        public void onChange(RealmResults<RStation> element) {
            Intent intent = new Intent(ACTION_FAVORITE_STATIONS_UPDATED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        }
    };

}
