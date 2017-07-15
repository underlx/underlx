package im.tny.segvault.disturbances;

import android.app.Notification;
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
import android.os.Handler;
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
import im.tny.segvault.disturbances.model.RStation;
import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.s2ls.InNetworkState;
import im.tny.segvault.s2ls.NearNetworkState;
import im.tny.segvault.s2ls.OffNetworkState;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.State;
import im.tny.segvault.s2ls.wifi.BSSID;
import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;
import im.tny.segvault.subway.Zone;
import io.realm.Realm;

public class MainService extends Service {
    public static final String PRIMARY_NETWORK_ID = "pt-ml";
    private API api;
    private ConnectionWeighter cweighter = new ConnectionWeighter(this);
    private WiFiChecker wfc;
    private LineStatusCache lineStatusCache;
    private PairManager pairManager;

    private final Object lock = new Object();
    private Map<String, Network> networks = new HashMap<>();
    private Map<String, S2LS> locServices = new HashMap<>();

    private Date creationDate = null;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private Handler stateTickHandler = null;

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
        lineStatusCache = new LineStatusCache(this);
    }

    private void putNetwork(Network net) {
        synchronized (lock) {
            // create Realm stations for the network if they don't exist already
            Realm realm = Realm.getDefaultInstance();
            realm.beginTransaction();
            for (Station s : net.getStations()) {
                if (realm.where(RStation.class).equalTo("id", s.getId()).count() == 0) {
                    RStation rs = new RStation();
                    rs.setStop(s);
                    rs.setNetwork(net.getId());
                    realm.copyToRealm(rs);
                }
            }
            realm.commitTransaction();

            net.setEdgeWeighter(cweighter);
            networks.put(net.getId(), net);
            S2LS loc = new S2LS(net, new S2LSChangeListener());
            locServices.put(net.getId(), loc);
            WiFiLocator wl = new WiFiLocator(net);
            wfc.setLocatorForNetwork(net, wl);
            loc.addNetworkDetector(wl);
            loc.addProximityDetector(wl);
            loc.addLocator(wl);
        }
    }

    @Override
    public void onCreate() {
        creationDate = new Date();
        stateTickHandler = new Handler();
        PreferenceManager.setDefaultValues(this.getApplicationContext(), R.xml.notif_settings, false);
        api = API.getInstance();
        wfc = new WiFiChecker(this, (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE));
        wfc.setScanInterval(10000);
        if (networks.size() == 0) {
            loadNetworks();
        }
        checkForTopologyUpdates();

        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.registerOnSharedPreferenceChangeListener(generalPrefsListener);

        pairManager = new PairManager(getApplicationContext());
        pairManager.unpair();
        if (!pairManager.isPaired()) {
            pairManager.pairAsync();
        }
    }

    @Override
    public void onDestroy() {
        wfc.stopScanning();
        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.unregisterOnSharedPreferenceChangeListener(generalPrefsListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (networks.size() == 0) {
            loadNetworks();
        }
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_CHECK_TOPOLOGY_UPDATES:
                    Log.d("MainService", "onStartCommand CheckUpdates");
                    if (new Date().getTime() - creationDate.getTime() < TimeUnit.SECONDS.toMillis(10)) {
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
                    final long msgtime = intent.getLongExtra(EXTRA_DISTURBANCE_MSGTIME, 0);
                    handleDisturbanceNotification(network, line, id, status, downtime, msgtime);
                    break;
            }
        }

        FirebaseMessaging.getInstance().subscribeToTopic("disturbances");
        if (BuildConfig.DEBUG) {
            FirebaseMessaging.getInstance().subscribeToTopic("disturbances-debug");
        }

        UpdateTopologyJob.schedule();
        return Service.START_STICKY;
    }

    private void loadNetworks() {
        synchronized (lock) {
            try {
                Network net = TopologyCache.loadNetwork(this, PRIMARY_NETWORK_ID);
                /*for (Line l : net.getLines()) {
                    Log.d("UpdateTopologyTask", "Line: " + l.getName());
                    for (Stop s : l.vertexSet()) {
                        Log.d("UpdateTopologyTask", s.toString());
                    }
                }*/
                Log.d("loadNetworks", "INTERCHANGES");
                for (Connection c : net.edgeSet()) {
                    if (c instanceof Transfer) {
                        Log.d("loadNetworks", " INTERCHANGE");
                        for (Stop s : c.getStops()) {
                            Log.d("loadNetworks", String.format("  %s", s.toString()));
                        }
                        for (Line l : ((Transfer) c).getLines()) {
                            Log.d("loadNetworks", String.format("  %s", l.toString()));
                        }
                    }
                }

                putNetwork(net);

                S2LS loc = locServices.get(PRIMARY_NETWORK_ID);
                Log.d("loadNetworks", String.format("In network? %b", loc.inNetwork()));
                Log.d("loadNetworks", String.format("Near network? %b", loc.nearNetwork()));
                Zone z = loc.getLocation();
                for (Stop s : z.vertexSet()) {
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
        currentUpdateTopologyTask.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR, PRIMARY_NETWORK_ID);
    }

    public void updateTopology(String... network_ids) {
        cancelTopologyUpdate();
        currentUpdateTopologyTask = new UpdateTopologyTask();
        currentUpdateTopologyTask.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR, network_ids);
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

    public S2LS getS2LS(String networkId) {
        synchronized (lock) {
            return locServices.get(networkId);
        }
    }

    public List<Stop> getAllStations() {
        List<Stop> stops = new ArrayList<>();
        synchronized (lock) {
            for (Network n : networks.values()) {
                stops.addAll(n.vertexSet());
            }
        }
        return stops;
    }

    public List<Line> getAllLines() {
        List<Line> lines = new ArrayList<>();
        synchronized (lock) {
            for (Network n : networks.values()) {
                lines.addAll(n.getLines());
            }
        }
        return lines;
    }

    public LineStatusCache getLineStatusCache() {
        return lineStatusCache;
    }

    public Stop getLikelyNextExit(List<Connection> path, double threshold) {
        if(path.size() == 0) {
            return null;
        }
        // get the line for the latest connection
        Connection last = path.get(path.size() - 1);
        Line line = null;
        for(Line l : getAllLines()) {
            if(l.edgeSet().contains(last)) {
                line = l;
                break;
            }
        }
        if (line == null) {
            return null;
        }

        Set<Stop> alreadyVisited = new HashSet<>();
        for(Connection c : path) {
            alreadyVisited.add(c.getSource());
            alreadyVisited.add(c.getTarget());
        }

        // get all the stops till the end of the line, after the given connection
        // (or in the case of circular lines, all stops of the line)
        Stop maxStop = null;
        double max = 0;
        Set<Stop> stops = new HashSet<>();
        while (stops.add(last.getSource())) {
            Stop curStop = last.getTarget();
            if(!alreadyVisited.contains(curStop)) {
                double r = getLeaveTrainFactorForStop(curStop);
                if(maxStop == null || r > max) {
                    maxStop = curStop;
                    max = r;
                }
            }
            if (line.outDegreeOf(curStop) == 1) {
                break;
            }
            for (Connection outedge : line.outgoingEdgesOf(curStop)) {
                if (!stops.contains(outedge.getTarget())) {
                    last = outedge;
                    break;
                }
            }
        }

        if(max < threshold) {
            // most relevant station is not relevant enough
            return null;
        }
        return maxStop;
    }

    public double getLeaveTrainFactorForStop(Stop stop) {
        Realm realm = Realm.getDefaultInstance();
        long entryCount = realm.where(StationUse.class).equalTo("station.id", stop.getStation().getId()).equalTo("type", StationUse.UseType.NETWORK_ENTRY.name()).count();
        long exitCount = realm.where(StationUse.class).equalTo("station.id", stop.getStation().getId()).equalTo("type", StationUse.UseType.NETWORK_EXIT.name()).count();
        // number of times user left at this stop to transfer to another line
        long transferCount = realm.where(StationUse.class).equalTo("station.id", stop.getStation().getId()).equalTo("sourceLine", stop.getLine().getId()).equalTo("type", StationUse.UseType.INTERCHANGE.name()).count();
        return entryCount * 0.3 + exitCount + transferCount;
    }

    // DEBUG:
    protected String dumpDebugInfo() {
        String s = "Service created on " + creationDate.toString();
        s += (wfc.isScanning() ? String.format(". WFC scanning every %d s", wfc.getScanInterval() / 1000) : ". WFC not scanning") + "\n";
        for (Network n : getNetworks()) {
            S2LS loc;
            synchronized (lock) {
                loc = locServices.get(n.getId());
            }
            s += "Network " + n.getName() + "\n";
            s += "State machine: " + loc.getState().toString() + "\n";
            s += String.format("\tIn network? %b\n\tNear network? %b\n", loc.inNetwork(), loc.nearNetwork());
            if (loc.getState() instanceof InNetworkState) {
                InNetworkState ins = (InNetworkState) loc.getState();
                s += "\tPossible stops:\n";
                for (Stop stop : loc.getLocation().vertexSet()) {
                    s += String.format("\t\t%s (%s)\n", stop.getStation().getName(), stop.getLine().getName());
                }
                s += "\tPath:\n";
                if (loc.getCurrentTrip() != null) {
                    for (Connection c : loc.getCurrentTrip().getEdgeList()) {
                        s += String.format("\t\t%s -> %s\n", c.getSource().toString(), c.getTarget().toString());
                    }
                }
            }
        }
        return s;
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

                    float netPart = (float) (cur_net + 1) / (float) net_count;
                    Log.d("UpdateTopologyTask", "Updating network " + n.id);
                    Network net = new Network(n.id, n.name, n.typCars, n.holidays, n.openTime * 1000, n.duration * 1000, n.timezone, n.newsURL);

                    Map<String, API.Station> apiStations = new HashMap<>();
                    for (API.Station s : api.getStations()) {
                        if (s.network.equals(n.id)) {
                            apiStations.put(s.id, s);
                        }
                    }

                    publishProgress(15);

                    Map<String, API.Lobby> apiLobbies = new HashMap<>();
                    for (API.Lobby l : api.getLobbies()) {
                        if (l.network.equals(n.id)) {
                            apiLobbies.put(l.id, l);
                        }
                    }

                    publishProgress(40);

                    int line_count = n.lines.size();
                    int cur_line = 0;
                    for (String lineid : n.lines) {
                        Log.d("UpdateTopologyTask", " Line: " + lineid);
                        API.Line l = api.getLine(lineid);
                        Line line = new Line(net, new HashSet<Stop>(), l.id, l.name, l.typCars);
                        line.setColor(Color.parseColor("#" + l.color));
                        for (String sid : l.stations) {
                            Log.d("UpdateTopologyTask", "  Stop: " + sid);
                            API.Station s = apiStations.get(sid);
                            Station station = net.getStation(s.id);
                            if (station == null) {
                                Map<String, String> triviaURLs = new HashMap<>();
                                for (Map.Entry<String, String> entry : s.triviaURLs.entrySet()) {
                                    triviaURLs.put(entry.getKey(), api.getEndpoint().toString() + entry.getValue());
                                }

                                Map<String, Map<String, String>> connURLs = new HashMap<>();
                                for (Map.Entry<String, Map<String, String>> entry : s.connURLs.entrySet()) {
                                    Map<String, String> urls = new HashMap<>();
                                    for (Map.Entry<String, String> localeEntry : entry.getValue().entrySet()) {
                                        urls.put(localeEntry.getKey(), api.getEndpoint().toString() + localeEntry.getValue());
                                    }
                                    connURLs.put(entry.getKey(), urls);
                                }

                                station = new Station(net, s.id, s.name,
                                        new Station.Features(s.features.lift, s.features.bus, s.features.boat, s.features.train, s.features.airport),
                                        triviaURLs);
                                station.setConnectionURLs(connURLs);

                                // Lobbies
                                for (String id : s.lobbies) {
                                    API.Lobby alobby = apiLobbies.get(id);
                                    Lobby lobby = new Lobby(alobby.id, alobby.name);
                                    for (API.Exit aexit : alobby.exits) {
                                        Lobby.Exit exit = new Lobby.Exit(aexit.id, aexit.worldCoord, aexit.streets);
                                        lobby.addExit(exit);
                                    }
                                    for (API.Schedule asched : alobby.schedule) {
                                        Lobby.Schedule sched = new Lobby.Schedule(
                                                asched.holiday, asched.day, asched.open, asched.openTime * 1000, asched.duration * 1000);
                                        lobby.addSchedule(sched);
                                    }
                                    station.addLobby(lobby);
                                }
                            }
                            Stop stop = new Stop(station, line);
                            line.addVertex(stop);
                            station.addVertex(stop);

                            // WiFi APs
                            for (API.WiFiAP w : s.wiFiAPs) {
                                // take line affinity into account
                                if (w.line.equals(line.getId())) {
                                    WiFiChecker.addBSSIDforStop(stop, new BSSID(w.bssid));
                                }
                            }
                        }
                        net.addLine(line);
                        cur_line++;
                        publishProgress(40 + (int) (((cur_line / (float) (line_count)) * netPart) * 20));
                        if (cur_line < line_count) {
                            cur_line++;
                        }
                        if (isCancelled()) break;
                    }
                    if (isCancelled()) break;

                    // Connections are within stations in the same line
                    for (API.Connection c : api.getConnections()) {
                        Set<Stop> sFrom = net.getStation(c.from).vertexSet();
                        Set<Stop> sTo = net.getStation(c.to).vertexSet();
                        Stop from = null, to = null;
                        for (Stop s : sFrom) {
                            for (Stop s2 : sTo) {
                                if (s.getLine().getId().equals(s2.getLine().getId())) {
                                    from = s;
                                    to = s2;
                                }
                            }
                        }
                        if (from != null && to != null) {
                            Connection newConnection = net.addEdge(from, to);
                            from.getLine().addEdge(from, to);
                            net.setEdgeWeight(newConnection, c.typS + 1); // TODO remove constant
                        }
                    }

                    publishProgress(80);

                    for (API.Transfer t : api.getTransfers()) {
                        Transfer newTransfer = new Transfer();
                        // find stations with the right IDs for each line
                        Station station = net.getStation(t.station);
                        for (Stop from : station.vertexSet()) {
                            for (Stop to : station.vertexSet()) {
                                if (from.getLine().getId().equals(t.from) && to.getLine().getId().equals(t.to)) {
                                    net.addEdge(from, to, newTransfer);
                                    net.setEdgeWeight(newTransfer, t.typS + 3); // TODO remove constant
                                }
                            }

                        }
                    }

                    publishProgress(100);

                    API.DatasetInfo info = api.getDatasetInfo(net.getId());
                    net.setDatasetAuthors(info.authors);
                    net.setDatasetVersion(info.version);
                    lineStatusCache.clear();
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

    public static final String ACTION_CURRENT_TRIP_UPDATED = "im.tny.segvault.disturbances.action.trip.current.updated";
    public static final String ACTION_CURRENT_TRIP_ENDED = "im.tny.segvault.disturbances.action.trip.current.ended";

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
    private static final String EXTRA_DISTURBANCE_MSGTIME = "im.tny.segvault.disturbances.extra.notification.disturbance.msgtime";

    public static void startForNotification(Context context, String network, String line,
                                            String id, String status, boolean downtime, long messageTime) {
        Intent intent = new Intent(context, MainService.class);
        intent.setAction(ACTION_DISTURBANCE_NOTIFICATION);
        intent.putExtra(EXTRA_DISTURBANCE_NETWORK, network);
        intent.putExtra(EXTRA_DISTURBANCE_LINE, line);
        intent.putExtra(EXTRA_DISTURBANCE_ID, id);
        intent.putExtra(EXTRA_DISTURBANCE_STATUS, status);
        intent.putExtra(EXTRA_DISTURBANCE_DOWNTIME, downtime);
        intent.putExtra(EXTRA_DISTURBANCE_MSGTIME, messageTime);
        context.startService(intent);
    }

    private void handleDisturbanceNotification(String network, String line,
                                               String id, String status, boolean downtime,
                                               long msgtime) {
        Log.d("MainService", "handleDisturbanceNotification");
        SharedPreferences sharedPref = getSharedPreferences("notifsettings", MODE_PRIVATE);
        Set<String> linePref = sharedPref.getStringSet("pref_notifs_lines", null);

        Network snetwork;
        synchronized (lock) {
            if (!networks.containsKey(network)) {
                return;
            }
            snetwork = networks.get(network);
        }
        Line sline = snetwork.getLine(line);
        if (sline == null) {
            return;
        }

        if (downtime) {
            lineStatusCache.markLineAsDown(sline, new Date(msgtime));
        } else {
            lineStatusCache.markLineAsUp(sline);
        }

        if (linePref != null && !linePref.contains(line)) {
            // notifications disabled for this line
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

    private Notification buildRouteNotification(String networkId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_INITIAL_FRAGMENT, R.id.nav_home);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);

        S2LS loc;
        synchronized (lock) {
            loc = locServices.get(networkId);
        }
        Path currentPath = loc.getCurrentTrip();

        Stop curStop = currentPath.getCurrentStop();
        String title = curStop.getStation().getName();
        String status;
        if (currentPath.isWaitingFirstTrain()) {
            status = getString(R.string.notif_route_waiting);
        } else {
            Stop direction = currentPath.getDirection();
            Stop next = currentPath.getNextStop();
            if (direction != null && next != null) {
                status = String.format(getString(R.string.notif_route_next_station) + "\n", next.getStation().getName());
                status += String.format(getString(R.string.notif_route_direction), direction.getStation().getName());
            } else {
                status = getString(R.string.notif_route_left_station);
            }
        }

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(curStop.getStation().getName());
        bigTextStyle.bigText(status);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.ic_trip_notif)
                .setColor(curStop.getLine().getColor())
                .setContentTitle(title)
                .setContentText(status.replace("\n", " | "))
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        return notificationBuilder.build();
    }

    private static final int ROUTE_NOTIFICATION_ID = -100;

    public class S2LSChangeListener implements S2LS.EventListener {
        @Override
        public void onStateChanged(final S2LS loc) {
            Log.d("onStateChanged", "State changed");

            stateTickHandler.removeCallbacksAndMessages(null);
            if (loc.getState().getPreferredTickIntervalMillis() != 0) {
                doTick(loc.getState());
            }

            SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
            boolean locationEnabled = sharedPref.getBoolean("pref_location_enable", true);

            if (loc.getState() instanceof InNetworkState) {
                wfc.setScanInterval(TimeUnit.SECONDS.toMillis(10));
                if (locationEnabled)
                    wfc.startScanning();
            } else if (loc.getState() instanceof NearNetworkState) {
                wfc.setScanInterval(TimeUnit.MINUTES.toMillis(1));
                if (locationEnabled)
                    wfc.startScanningIfWiFiEnabled();
                stopForeground(true);
            } else if (loc.getState() instanceof OffNetworkState) {
                // wfc.stopScanning(); // TODO only enable this when there are locators besides WiFi
                wfc.setScanInterval(TimeUnit.MINUTES.toMillis(1));
                if (locationEnabled)
                    wfc.startScanningIfWiFiEnabled();
                stopForeground(true);
            }
        }

        @Override
        public void onTripStarted(final S2LS s2ls) {
            Path path = s2ls.getCurrentTrip();
            path.addPathChangedListener(new Path.OnPathChangedListener() {
                @Override
                public void onPathChanged(Path path) {
                    Log.d("onPathChanged", "Path changed");
                    startForeground(ROUTE_NOTIFICATION_ID, buildRouteNotification(s2ls.getNetwork().getId()));

                    Intent intent = new Intent(ACTION_CURRENT_TRIP_UPDATED);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
                    bm.sendBroadcast(intent);
                }
            });
            startForeground(ROUTE_NOTIFICATION_ID, buildRouteNotification(s2ls.getNetwork().getId()));
        }

        @Override
        public void onTripEnded(S2LS s2ls, Path path) {
            Trip.persistConnectionPath(path);
            Intent intent = new Intent(ACTION_CURRENT_TRIP_ENDED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
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
    }

    private SharedPreferences.OnSharedPreferenceChangeListener generalPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (key.equals("pref_location_enable")) {
                if (prefs.getBoolean("pref_location_enable", true))
                    wfc.startScanningIfWiFiEnabled();
                else
                    wfc.stopScanning();
            }
        }
    };

}
