package im.tny.segvault.disturbances;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobRequest;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.exception.CacheException;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.wifi.BSSID;
import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Transfer;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Zone;

public class MainService extends Service {
    private API api;
    private WiFiChecker wfc;

    private final Object lock = new Object();
    private Map<String, Network> networks = new HashMap<>();
    private Map<String, S2LS> locServices = new HashMap<>();

    private Date creationDate = null;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        MainService getService() {
            // Return this instance of MainService so clients can call public methods
            return MainService.this;
        }
    }

    public MainService() {

    }

    private void putNetwork(Network net) {
        synchronized (lock) {
            networks.put("pt-ml", net);
            S2LS loc = new S2LS(net);
            locServices.put("pt-ml", loc);
            WiFiLocator wl = new WiFiLocator(wfc);
            loc.addNetworkDetector(wl);
            loc.addProximityDetector(wl);
            loc.addLocator(wl);
        }
    }

    @Override
    public void onCreate() {
        creationDate = new Date();
        PreferenceManager.setDefaultValues(this.getApplicationContext(), R.xml.settings, false);
        api = API.getInstance();
        wfc = new WiFiChecker(this, (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE));
        wfc.setScanInterval(10000);
        checkForTopologyUpdates();
        //wfc.startScanning();
    }

    @Override
    public void onDestroy() {
        wfc.stopScanning();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadNetworks();
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_CHECK_TOPOLOGY_UPDATES:
                    Log.d("MainService", "onStartCommand CheckUpdates");
                    if(new Date().getTime() - creationDate.getTime() < TimeUnit.SECONDS.toMillis(10)) {
                        // service started less than 10 seconds ago, no need to check again
                        break;
                    }
                    checkForTopologyUpdates(true);
                    Log.d("MainService", "onStartCommand updates checked");
                    break;
                case ACTION_DISTURBANCE_NOTIFICATION:
                    final String network = intent.getStringExtra(EXTRA_DISTURBANCE_NETWORK);
                    final String line = intent.getStringExtra(EXTRA_DISTURBANCE_LINE);
                    final String id = intent.getStringExtra(EXTRA_DISTURBANCE_ID);
                    final String status = intent.getStringExtra(EXTRA_DISTURBANCE_STATUS);
                    final boolean downtime = intent.getBooleanExtra(EXTRA_DISTURBANCE_DOWNTIME, false);
                    handleDisturbanceNotification(network, line, id, status, downtime);
                    break;
            }
        }

        FirebaseMessaging.getInstance().subscribeToTopic("disturbances");

        UpdateTopologyJob.schedule();
        return Service.START_STICKY;
    }

    private void loadNetworks() {
        synchronized (MainService.this.lock) {
            try {
                Network net = TopologyCache.loadNetwork(this, "pt-ml");
                /*for (Line l : net.getLines()) {
                    Log.d("UpdateTopologyTask", "Line: " + l.getName());
                    for (Station s : l.vertexSet()) {
                        Log.d("UpdateTopologyTask", s.toString());
                    }
                }*/
                Log.d("loadNetworks", "INTERCHANGES");
                for (Connection c : net.edgeSet()) {
                    if (c instanceof Transfer) {
                        Log.d("loadNetworks", " INTERCHANGE");
                        for (Station s : c.getStations()) {
                            Log.d("loadNetworks", String.format("  %s", s.toString()));
                        }
                        for (Line l : ((Transfer) c).getLines()) {
                            Log.d("loadNetworks", String.format("  %s", l.toString()));
                        }
                    }
                }

                putNetwork(net);

                S2LS loc = locServices.get("pt-ml");
                Log.d("loadNetworks", String.format("In network? %b", loc.inNetwork()));
                Log.d("loadNetworks", String.format("Near network? %b", loc.nearNetwork()));
                Zone z = loc.getLocation();
                for (Station s : z.vertexSet()) {
                    Log.d("loadNetworks", String.format("May be in station %s", s));
                }
            } catch (CacheException e) {
                // cache invalid, attempt to reload topology
                updateTopology();
            }
        }
    }

    public void updateTopology() {
        cancelTopologyUpdate();
        currentUpdateTopologyTask = new UpdateTopologyTask();
        currentUpdateTopologyTask.execute("pt-ml");
    }

    public void updateTopology(String... network_ids) {
        cancelTopologyUpdate();
        currentUpdateTopologyTask = new UpdateTopologyTask();
        currentUpdateTopologyTask.execute(network_ids);
    }

    public void cancelTopologyUpdate() {
        if (currentUpdateTopologyTask != null) {
            currentUpdateTopologyTask.cancel(true);
        }
    }

    public void checkForTopologyUpdates() {
        currentCheckTopologyUpdatesTask = new CheckTopologyUpdatesTask();
        currentCheckTopologyUpdatesTask.execute(Connectivity.isConnectedWifi(this));
    }

    public void checkForTopologyUpdates(boolean autoUpdate) {
        currentCheckTopologyUpdatesTask = new CheckTopologyUpdatesTask();
        currentCheckTopologyUpdatesTask.execute(autoUpdate);
    }

    public Collection<Network> getNetworks() {
        synchronized (lock) {
            return networks.values();
        }
    }

    public Network getNetwork(String id) {
        synchronized (lock) {
            return networks.get(id);
        }
    }

    public List<Station> getAllStations() {
        List<Station> stations = new ArrayList<>();
        synchronized (lock) {
            for(Network n : networks.values()) {
                stations.addAll(n.vertexSet());
            }
        }
        return stations;
    }

    // DEBUG:
    protected String dumpDebugInfo() {
        String s = "";
        for (Network n : getNetworks()) {
            S2LS loc;
            synchronized (lock) {
                loc = locServices.get(n.getId());
            }
            s += "Network " + n.getName() + "\n";
            s += String.format("\tIn network? %b\n\tNear network? %b\n\tPossible stations:\n", loc.inNetwork(), loc.nearNetwork());
            for (Station station : loc.getLocation().vertexSet()) {
                s += String.format("\t%s (%s)\n", station.getName(), station.getLines().get(0).getName());
            }
        }
        return s;
    }

    protected void startScanning() {
        wfc.startScanning();
    }
    protected void stopScanning() {
        wfc.stopScanning();
    }

    // END OF DEBUG

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private UpdateTopologyTask currentUpdateTopologyTask = null;

    private class UpdateTopologyTask extends AsyncTask<String, Integer, Boolean> {
        protected Boolean doInBackground(String... networkIds) {
            int net_count = networkIds.length;
            publishProgress(0);
            try {
                for (int cur_net = 0; cur_net < net_count; cur_net++) {
                    API.Network n = api.getNetwork(networkIds[cur_net]);
                    int station_count = n.stations.size();
                    int cur_station = 0;
                    Log.d("UpdateTopologyTask", "Updating network " + n.id);
                    Network net = new Network(n.id, n.name);
                    for (String lineid : n.lines) {
                        Log.d("UpdateTopologyTask", " Line: " + lineid);
                        API.Line l = api.getLine(lineid);
                        Line line = new Line(net, new HashSet<Station>(), l.id, l.name);
                        line.setColor(Color.parseColor("#" + l.color));
                        for (String sid : l.stations) {
                            Log.d("UpdateTopologyTask", "  Station: " + sid);
                            API.Station s = api.getStation(sid);
                            Station station = new Station(s.id, s.name);
                            line.addVertex(station);
                            station.addLine(line);

                            // WiFi APs
                            for (API.WiFiAP w : s.wiFiAPs) {
                                // take line affinity into account
                                if (w.line.equals(line.getId())) {
                                    WiFiChecker.addBSSIDforStation(station, new BSSID(w.bssid));
                                }
                            }

                            float netPart = (float) (cur_net + 1) / (float) net_count;
                            publishProgress((int) (((cur_station / (float) (station_count)) * netPart) * 100));
                            if (cur_station < station_count) {
                                cur_station++;
                            }
                            if (isCancelled()) break;
                        }
                        net.addLine(line);
                    }
                    if (isCancelled()) break;

                    // Connections are within stations in the same line
                    for (API.Connection c : api.getConnections()) {
                        List<Station> sFrom = net.getStation(c.from);
                        List<Station> sTo = net.getStation(c.to);
                        Station from = null, to = null;
                        for (Station s : sFrom) {
                            for (Station s2 : sTo) {
                                if (s.getLines().containsAll(s2.getLines())) {
                                    from = s;
                                    to = s2;
                                }
                            }
                        }
                        if (from != null && to != null) {
                            Connection newConnection = net.addEdge(from, to);
                            for(Line l : from.getLines()) {
                                if(to.getLines().contains(l)) {
                                    l.addEdge(from, to);
                                }
                            }
                            net.setEdgeWeight(newConnection, c.typS + 1); // TODO remove constant
                        }
                    }

                    for (API.Transfer t : api.getTransfers()) {
                        Transfer newTransfer = new Transfer();
                        // find stations with the right IDs for each line
                        Station from = null;
                        Station to = null;
                        for (Station s : net.vertexSet()) {
                            if (s.getId().equals(t.station)) {
                                for (Line l : s.getLines()) {
                                    if (l.getId().equals(t.from)) {
                                        from = s;
                                        break;
                                    }
                                    if (l.getId().equals(t.to)) {
                                        to = s;
                                        break;
                                    }
                                }
                            }
                        }
                        if (from != null && to != null) {
                            net.addEdge(from, to, newTransfer);
                            net.setEdgeWeight(newTransfer, t.typS + 3); // TODO remove constant
                        }
                    }

                    API.DatasetInfo info = api.getDatasetInfo(net.getId());
                    net.setDatasetAuthors(info.authors);
                    net.setDatasetVersion(info.version);
                    putNetwork(net);
                    TopologyCache.saveNetwork(MainService.this, net);
                    if (isCancelled()) break;
                }
            } catch (APIException e) {
                return false;
            } catch (CacheException e) {
                return false;
            }
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {
            Intent intent = new Intent(ACTION_UPDATE_TOPOLOGY_PROGRESS);
            intent.putExtra(EXTRA_UPDATE_TOPOLOGY_PROGRESS, progress[0]);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        }

        protected void onPostExecute(Boolean result) {
            Log.d("UpdateTopologyTask", result.toString());
            currentUpdateTopologyTask = null;
            Intent intent = new Intent(ACTION_UPDATE_TOPOLOGY_FINISHED);
            intent.putExtra(EXTRA_UPDATE_TOPOLOGY_FINISHED, result);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        }

        @Override
        protected void onCancelled() {
            Log.d("UpdateTopologyTask", "onCancelled");
            currentUpdateTopologyTask = null;
            Intent intent = new Intent(ACTION_UPDATE_TOPOLOGY_CANCELLED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);
        }
    }

    private CheckTopologyUpdatesTask currentCheckTopologyUpdatesTask = null;

    private class CheckTopologyUpdatesTask extends AsyncTask<Boolean, Integer, Boolean> {
        // returns true if there are updates, false if not
        private boolean autoUpdate;

        protected Boolean doInBackground(Boolean... autoUpdate) {
            this.autoUpdate = autoUpdate[0];
            Log.d("MainService", "CheckTopologyUpdatesTask");
            try {
                synchronized (lock) {
                    for (API.DatasetInfo di : api.getDatasetInfos()) {
                        if (!networks.containsKey(di.network)) {
                            return true;
                        }
                        Network net = networks.get(di.network);
                        if (!di.version.equals(net.getDatasetVersion())) {
                            return true;
                        }
                    }
                }
                return false;
            } catch (APIException e) {
                return false;
            }
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                if (autoUpdate) {
                    updateTopology();
                } else {
                    Intent intent = new Intent(ACTION_TOPOLOGY_UPDATE_AVAILABLE);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
                    bm.sendBroadcast(intent);
                }
            }
            currentCheckTopologyUpdatesTask = null;
        }
    }

    public static final String ACTION_UPDATE_TOPOLOGY_PROGRESS = "im.tny.segvault.disturbances.action.topology.update.progress";
    public static final String EXTRA_UPDATE_TOPOLOGY_PROGRESS = "im.tny.segvault.disturbances.extra.topology.update.progress";
    public static final String ACTION_UPDATE_TOPOLOGY_FINISHED = "im.tny.segvault.disturbances.action.topology.update.finished";
    public static final String EXTRA_UPDATE_TOPOLOGY_FINISHED = "im.tny.segvault.disturbances.extra.topology.update.finished";
    public static final String ACTION_UPDATE_TOPOLOGY_CANCELLED = "im.tny.segvault.disturbances.action.topology.update.cancelled";

    public static final String ACTION_TOPOLOGY_UPDATE_AVAILABLE = "im.tny.segvault.disturbances.action.topology.update.available";

    public static final String ACTION_CHECK_TOPOLOGY_UPDATES = "im.tny.segvault.disturbances.action.checkTopologyUpdates";

    public static class LocationJobCreator implements JobCreator {

        @Override
        public Job create(String tag) {
            switch (tag) {
                case UpdateTopologyJob.TAG:
                    return new UpdateTopologyJob();
                default:
                    return null;
            }
        }
    }

    public static class UpdateTopologyJob extends Job {
        public static final String TAG = "job_update_topology";

        @Override
        @NonNull
        protected Result onRunJob(Params params) {
            Intent intent = new Intent(getContext(), MainService.class).setAction(ACTION_CHECK_TOPOLOGY_UPDATES);
            getContext().startService(intent);
            return Result.SUCCESS;
        }

        public static void schedule() {
            schedule(true);
        }

        public static void schedule(boolean updateCurrent) {
            new JobRequest.Builder(UpdateTopologyJob.TAG)
                    .setExecutionWindow(TimeUnit.HOURS.toMillis(12), TimeUnit.HOURS.toMillis(36))
                    .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobRequest.BackoffPolicy.EXPONENTIAL)
                    .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                    .setPersisted(true)
                    .setUpdateCurrent(updateCurrent)
                    .build()
                    .schedule();
        }
    }

    private static final String ACTION_DISTURBANCE_NOTIFICATION = "im.tny.segvault.disturbances.action.notification.disturbance";

    private static final String EXTRA_DISTURBANCE_NETWORK = "im.tny.segvault.disturbances.extra.notification.disturbance.network";
    private static final String EXTRA_DISTURBANCE_LINE = "im.tny.segvault.disturbances.extra.notification.disturbance.line";
    private static final String EXTRA_DISTURBANCE_ID = "im.tny.segvault.disturbances.extra.notification.disturbance.id";
    private static final String EXTRA_DISTURBANCE_STATUS = "im.tny.segvault.disturbances.extra.notification.disturbance.status";
    private static final String EXTRA_DISTURBANCE_DOWNTIME = "im.tny.segvault.disturbances.extra.notification.disturbance.downtime";

    public static void showDisturbanceNotification(Context context, String network, String line,
                                                   String id, String status, boolean downtime) {
        Intent intent = new Intent(context, MainService.class);
        intent.setAction(ACTION_DISTURBANCE_NOTIFICATION);
        intent.putExtra(EXTRA_DISTURBANCE_NETWORK, network);
        intent.putExtra(EXTRA_DISTURBANCE_LINE, line);
        intent.putExtra(EXTRA_DISTURBANCE_ID, id);
        intent.putExtra(EXTRA_DISTURBANCE_STATUS, status);
        intent.putExtra(EXTRA_DISTURBANCE_DOWNTIME, downtime);
        context.startService(intent);
    }

    private void handleDisturbanceNotification(String network, String line,
                                               String id, String status, boolean downtime) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> linePref = sharedPref.getStringSet("line_selection", null);
        if(linePref != null && !linePref.contains(line)) {
            // notifications disabled for this line
            return;
        }

        Network snetwork;
        synchronized (lock) {
            if(!networks.containsKey(network)) {
                return;
            }
            snetwork = networks.get(network);
        }
        Line sline = snetwork.getLine(line);
        if(sline == null) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_INITIAL_FRAGMENT, R.id.nav_disturbances);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);

        String title = String.format(getString(R.string.notif_disturbance_title), sline.getName());

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(title);
        bigTextStyle.bigText(status);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.ic_disturbance_notif)
                .setColor(sline.getColor())
                .setContentTitle(title)
                .setContentText(status)
                .setAutoCancel(true)
                .setSound(Uri.parse(sharedPref.getString("pref_notifs_ringtone", "content://settings/system/notification_sound")))
                .setContentIntent(pendingIntent);

        if (sharedPref.getBoolean("pref_notifs_vibrate", false)) {
            notificationBuilder.setVibrate(new long[]{0, 100, 100, 150, 150, 200});
        } else {
            notificationBuilder.setVibrate(new long[]{0l});
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(id.hashCode(), notificationBuilder.build());
    }
}
