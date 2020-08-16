package im.tny.segvault.disturbances;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ServiceLifecycleDispatcher;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.activity.ReportActivity;
import im.tny.segvault.s2ls.InNetworkState;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;

import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_BACKGROUND_ID;
import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_REALTIME_HIGH_ID;
import static im.tny.segvault.disturbances.Coordinator.NOTIF_CHANNEL_REALTIME_ID;

public class MainService extends Service implements LifecycleOwner {
    private Date creationDate = null;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private final ServiceLifecycleDispatcher mDispatcher = new ServiceLifecycleDispatcher(this);

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mDispatcher.getLifecycle();
    }

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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtil.updateResources(base));
    }

    @Override
    public void onCreate() {
        mDispatcher.onServicePreSuperOnCreate();
        super.onCreate();

        LocaleUtil.updateResources(this);
        creationDate = new Date();
        API.getInstance().setContext(getApplicationContext());
        PreferenceManager.setDefaultValues(this.getApplicationContext(), R.xml.notif_settings, false);

        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.registerOnSharedPreferenceChangeListener(generalPrefsListener);
        if (new Date().getTime() - sharedPref.getLong(PreferenceNames.LastAutoTopologyUpdateCheck, 0) > TimeUnit.HOURS.toMillis(2)) {
            SharedPreferences.Editor e = sharedPref.edit();
            e.putLong(PreferenceNames.LastAutoTopologyUpdateCheck, new Date().getTime());
            e.apply();
            Coordinator.get(this).getMapManager().checkForTopologyUpdates();
        }

        AppDatabase db = Coordinator.get(this).getDB();
        db.tripDao().getUnsyncedLive().observe(this, trips -> {
            Intent intent = new Intent(ACTION_TRIP_TABLE_UPDATED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        });
        db.feedbackDao().getUnsyncedLive().observe(this, feedbacks -> {
            Intent intent = new Intent(ACTION_FEEDBACK_TABLE_UPDATED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        });
        db.stationPreferenceDao().getFavoriteLive().observe(this, stationPreferences -> {
            Intent intent = new Intent(ACTION_FAVORITE_STATIONS_UPDATED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        });

        Coordinator.get(this).registerMainService(this);
        Coordinator.get(this).reloadFCMsubscriptions();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MqttManager.ACTION_VEHICLE_ETAS_UPDATED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        Coordinator.get(this).unregisterMainService();
        Coordinator.get(this).getWiFiChecker().stopScanning();
        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.unregisterOnSharedPreferenceChangeListener(generalPrefsListener);
        mDispatcher.onServicePreSuperOnDestroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @CallSuper
    @Override
    public void onStart(Intent intent, int startId) {
        mDispatcher.onServicePreSuperOnStart();
        super.onStart(intent, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        boolean permanentForeground = sharedPref.getBoolean(PreferenceNames.PermanentForeground, false);

        S2LS s2ls = Coordinator.get(MainService.this).getS2LS(MapManager.PRIMARY_NETWORK_ID);
        if (!shouldBeInForeground(s2ls)) {
            if (permanentForeground) {
                startPermanentForeground();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startTemporaryForeground();
            }
        } else {
            updateRouteNotification(s2ls);
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

    @Override
    public IBinder onBind(Intent intent) {
        mDispatcher.onServicePreSuperOnBind();
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
            Log.d("MainService", "Attempt to create notification when there's no path or planned route");
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

            Map<String, List<API.MQTTvehicleETA>> etas = Coordinator.get(this).getMqttManager().getVehicleETAsForStation(curStop.getStation(), 1);

            if (currentPath.isWaitingFirstTrain()) {
                if (etas.size() == 0) {
                    statusLines.add(getString(R.string.notif_route_waiting));
                } else {
                    addETAlines(this, etas, statusLines, curStop, null);
                }
            } else {
                Stop direction = currentPath.getDirection();
                Stop next = currentPath.getNextStop();
                if (direction != null && next != null) {
                    statusLines.add(String.format(getString(R.string.notif_route_next_station), next.getStation().getName()));
                    if (currentRoute == null) {
                        statusLines.add(String.format(getString(R.string.notif_route_direction), direction.getStation().getName()));
                    }
                    if (curStop.getStation().getLines().size() > 1 && etas.size() > 0) {
                        addETAlines(this, etas, statusLines, curStop, direction);
                    }
                } else {
                    if (etas.size() == 0) {
                        statusLines.add(getString(R.string.notif_route_left_station));
                    } else {
                        addETAlines(this, etas, statusLines, curStop, null);
                    }
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


        if (highPriorityNotification) {
            lastRouteNotificationData = data;
            proxyStartForeground(ROUTE_NOTIFICATION_ID, notificationBuilder.build());
            Log.d("MainService", "Updated route notification (high priority)");
        } else if (shouldUpdateRouteNotification(data)) {
            proxyStartForeground(ROUTE_NOTIFICATION_ID, notificationBuilder.build());
            Log.d("MainService", "Updated route notification");
        }
    }

    public static void addETAlines(Context context, Map<String, List<API.MQTTvehicleETA>> etas, List<CharSequence> statusLines, Stop curStop, @Nullable Stop curDirection) {
        statusLines.add(context.getString(R.string.notif_route_vehicle_etas_title));
        statusLines.addAll(getETAlines(context, etas, curStop.getStation(), curDirection));
    }

    public static class ETArow {
        public Station direction;
        public Stop directionStop;
        public SpannableString eta;
        public boolean available;
        public boolean onPlatform;
    }

    public static List<ETArow> getETArows(Context context, Map<String, List<API.MQTTvehicleETA>> etas, Station curStation, @Nullable Stop curDirection, boolean withUnavailable, boolean shortText) {
        List<ETArow> statusRows = new ArrayList<>();
        Set<Stop> directionsSet = new HashSet<>();
        for (List<API.MQTTvehicleETA> lETAs : etas.values()) {
            for (API.MQTTvehicleETA eta : lETAs) {
                Station station = curStation.getNetwork().getStation(eta.direction);
                if (station == null) {
                    continue;
                }
                // get stop for the line of the current station (so it has the right color, etc.)
                for (Stop stop : station.getStops()) {
                    for (Line l : curStation.getLines()) {
                        if (l.equals(stop.getLine())) {
                            directionsSet.add(stop);
                            break;
                        }
                    }
                }
            }
        }

        List<Stop> directions = new ArrayList<>();
        for (Stop s : directionsSet) {
            if (!s.equals(curDirection) && !s.getStation().equals(curStation)) {
                directions.add(s);
            }
        }
        Collections.sort(directions, (stop, t1) -> {
            int lineComp = Integer.compare(stop.getLine().getOrder(), t1.getLine().getOrder());
            if (lineComp == 0) {
                return stop.getStation().getName().compareTo(t1.getStation().getName());
            }
            return lineComp;
        });
        Map<String, LineStatusCache.Status> statuses = Coordinator.get(context).getLineStatusCache().getLineStatus();

        for (Stop direction : directions) {
            ETArow r = new ETArow();
            r.direction = direction.getStation();
            r.directionStop = direction;
            List<API.MQTTvehicleETA> lETAs = etas.get(r.direction.getId());
            r.available = lETAs != null;
            StringBuilder etaBuilder = new StringBuilder();
            int i = 0;
            List<Integer> redColorStartIdx = new ArrayList<>();
            List<Integer> redColorEndIdx = new ArrayList<>();
            for (API.MQTTvehicleETA eta : lETAs) {
                boolean isDelayed = false;
                if (eta.order == 1) {
                    r.onPlatform = vehicleETAonPlatform(eta);
                    LineStatusCache.Status lineStatus = statuses.get(r.directionStop.getLine().getId());
                    isDelayed = lineStatus != null &&
                            eta.type.equals("e") &&
                            eta instanceof API.MQTTvehicleETAsingleValue &&
                            new Date().getTime() - lineStatus.updated.getTime() < java.util.concurrent.TimeUnit.MINUTES.toMillis(5) &&
                            lineStatus.condition.trainFrequency > 0 &&
                            ((API.MQTTvehicleETAsingleValue) eta).value > lineStatus.condition.trainFrequency / 1000;
                }
                String etaString;
                if (shortText || i != 0) {
                    if (i == 1) {
                        etaBuilder.append(String.format("\n\t\t%s ", context.getString(R.string.vehicle_eta_after_next)));
                    } else if (i > 1) {
                        etaBuilder.append(", ");
                    }
                    etaString = vehicleETAtoShortString(context, eta);
                } else {
                    etaString = vehicleETAtoString(context, eta);
                }
                int startIdx = etaBuilder.length();
                etaBuilder.append(etaString);
                if (isDelayed) {
                    redColorStartIdx.add(startIdx);
                    redColorEndIdx.add(startIdx + etaString.length());
                }
                i++;
            }
            SpannableString spannable = new SpannableString(etaBuilder);
            for(i = 0; i < redColorStartIdx.size(); i++) {
                spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorError)),
                        redColorStartIdx.get(i), redColorEndIdx.get(i), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            }
            r.eta = spannable;
            if (r.available || withUnavailable) {
                statusRows.add(r);
            }
        }
        return statusRows;
    }

    public static List<CharSequence> getETAlines(Context context, Map<String, List<API.MQTTvehicleETA>> etas, Station curStation, @Nullable Stop curDirection) {
        List<CharSequence> statusLines = new ArrayList<>();
        List<ETArow> rows = getETArows(context, etas, curStation, curDirection, true, false);
        for (ETArow r : rows) {
            CharSequence line = TextUtils.concat(r.direction.getName(), " ", r.eta);
            int lStart = line.toString().indexOf(r.direction.getName());
            int lEnd = lStart + r.direction.getName().length();
            SpannableString lineSpannable = new SpannableString(line);
            lineSpannable.setSpan(new StyleSpan(Typeface.BOLD), lStart, lEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            lineSpannable.setSpan(new ForegroundColorSpan(r.directionStop.getLine().getColor()), lStart, lEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            int extraVehiclesStart = line.toString().indexOf("\n");
            if (extraVehiclesStart > -1) {
                lineSpannable.setSpan(new RelativeSizeSpan(0.8f), extraVehiclesStart, line.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            //lineSpannable.setSpan(new TabStopSpan.Standard(600), 0, lineSpannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            statusLines.add(lineSpannable);
        }
        return statusLines;
    }

    private static boolean vehicleETAonPlatform(API.MQTTvehicleETA eta) {
        return eta.type.equals("e") && eta instanceof API.MQTTvehicleETAsingleValue &&
                ((API.MQTTvehicleETAsingleValue) eta).value == 0;
    }

    private static String vehicleETAtoString(Context context, API.MQTTvehicleETA eta) {
        if (eta == null) {
            return context.getString(R.string.vehicle_eta_unavailable);
        }
        if (eta instanceof API.MQTTvehicleETAinterval) {
            if (eta.type.equals("i")) {
                switch (eta.units) {
                    case "s":
                        return String.format(context.getString(R.string.vehicle_eta_interval_seconds), ((API.MQTTvehicleETAinterval) eta).lower, ((API.MQTTvehicleETAinterval) eta).upper);
                    case "m":
                        return String.format(context.getString(R.string.vehicle_eta_interval_minutes), ((API.MQTTvehicleETAinterval) eta).lower, ((API.MQTTvehicleETAinterval) eta).upper);
                }
            }
        } else if (eta instanceof API.MQTTvehicleETAsingleValue) {
            switch (eta.type) {
                case "e":
                    switch (eta.units) {
                        case "s":
                            if (((API.MQTTvehicleETAsingleValue) eta).value > 60) {
                                return String.format(context.getString(R.string.vehicle_eta_exact_formatted),
                                        DateUtils.formatElapsedTime(((API.MQTTvehicleETAsingleValue) eta).value));
                            } else if (((API.MQTTvehicleETAsingleValue) eta).value == 0) {
                                return context.getString(R.string.vehicle_eta_exact_at_the_platform);
                            }
                            return String.format(context.getString(R.string.vehicle_eta_exact_seconds), ((API.MQTTvehicleETAsingleValue) eta).value);
                        case "m":
                            return String.format(context.getString(R.string.vehicle_eta_exact_minutes), ((API.MQTTvehicleETAsingleValue) eta).value);
                    }
                    break;
                case "l":
                    switch (eta.units) {
                        case "s":
                            return String.format(context.getString(R.string.vehicle_eta_lessthan_seconds), ((API.MQTTvehicleETAsingleValue) eta).value);
                        case "m":
                            return String.format(context.getString(R.string.vehicle_eta_lessthan_minutes), ((API.MQTTvehicleETAsingleValue) eta).value);
                    }
                    break;
                case "m":
                    switch (eta.units) {
                        case "s":
                            return String.format(context.getString(R.string.vehicle_eta_morethan_seconds), ((API.MQTTvehicleETAsingleValue) eta).value);
                        case "m":
                            return String.format(context.getString(R.string.vehicle_eta_morethan_minutes), ((API.MQTTvehicleETAsingleValue) eta).value);
                    }
                    break;
                case "t":
                    // when it's a timestamp, it's always in seconds
                    String time = DateUtils.formatDateTime(context,
                            ((API.MQTTvehicleETAsingleValue) eta).value * 1000,
                            DateUtils.FORMAT_SHOW_TIME);
                    return String.format(context.getString(R.string.vehicle_eta_timestamp), time);
            }
        }
        return context.getString(R.string.vehicle_eta_unavailable);
    }

    private static String vehicleETAtoShortString(Context context, API.MQTTvehicleETA eta) {
        if (eta == null) {
            return context.getString(R.string.vehicle_eta_unavailable_short);
        }
        if (eta instanceof API.MQTTvehicleETAinterval) {
            if (eta.type.equals("i")) {
                switch (eta.units) {
                    case "s":
                        return String.format(context.getString(R.string.vehicle_eta_interval_seconds_short), ((API.MQTTvehicleETAinterval) eta).lower, ((API.MQTTvehicleETAinterval) eta).upper);
                    case "m":
                        return String.format(context.getString(R.string.vehicle_eta_interval_minutes_short), ((API.MQTTvehicleETAinterval) eta).lower, ((API.MQTTvehicleETAinterval) eta).upper);
                }
            }
        } else if (eta instanceof API.MQTTvehicleETAsingleValue) {
            switch (eta.type) {
                case "e":
                    switch (eta.units) {
                        case "s":
                            if (((API.MQTTvehicleETAsingleValue) eta).value > 60) {
                                return String.format(context.getString(R.string.vehicle_eta_exact_formatted_short),
                                        DateUtils.formatElapsedTime(((API.MQTTvehicleETAsingleValue) eta).value));
                            }
                            return String.format(context.getString(R.string.vehicle_eta_exact_seconds_short), ((API.MQTTvehicleETAsingleValue) eta).value);
                        case "m":
                            return String.format(context.getString(R.string.vehicle_eta_exact_minutes_short), ((API.MQTTvehicleETAsingleValue) eta).value);
                    }
                    break;
                case "l":
                    switch (eta.units) {
                        case "s":
                            return String.format(context.getString(R.string.vehicle_eta_lessthan_seconds_short), ((API.MQTTvehicleETAsingleValue) eta).value);
                        case "m":
                            return String.format(context.getString(R.string.vehicle_eta_lessthan_minutes_short), ((API.MQTTvehicleETAsingleValue) eta).value);
                    }
                    break;
                case "m":
                    switch (eta.units) {
                        case "s":
                            return String.format(context.getString(R.string.vehicle_eta_morethan_seconds_short), ((API.MQTTvehicleETAsingleValue) eta).value);
                        case "m":
                            return String.format(context.getString(R.string.vehicle_eta_morethan_minutes_short), ((API.MQTTvehicleETAsingleValue) eta).value);
                    }
                    break;
                case "t":
                    // when it's a timestamp, it's always in seconds
                    String time = DateUtils.formatDateTime(context,
                            ((API.MQTTvehicleETAsingleValue) eta).value * 1000,
                            DateUtils.FORMAT_SHOW_TIME);
                    return String.format(context.getString(R.string.vehicle_eta_timestamp_short), time);
            }
        }
        return context.getString(R.string.vehicle_eta_unavailable_short);
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
            lastRouteNotificationData = null;
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
        tempForegroundHandler.postDelayed(() -> {
            if (isTempForeground) {
                stopForeground(true);
                isTempForeground = false;
            }
        }, 10000);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener generalPrefsListener = (prefs, key) -> {
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
    };

    public static final String ACTION_TRIP_TABLE_UPDATED = "im.tny.segvault.disturbances.action.table.trip.updated";

    public static final String ACTION_FEEDBACK_TABLE_UPDATED = "im.tny.segvault.disturbances.action.table.feedback.updated";

    public static final String ACTION_FAVORITE_STATIONS_UPDATED = "im.tny.segvault.disturbances.action.table.station.favorite.updated";

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MqttManager.ACTION_VEHICLE_ETAS_UPDATED:
                    updateRouteNotification(Coordinator.get(MainService.this).getS2LS(intent.getStringExtra(MqttManager.EXTRA_VEHICLE_ETAS_NETWORK)));
                    break;
            }
        }
    };

}
