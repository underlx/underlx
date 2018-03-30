package im.tny.segvault.disturbances;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
import im.tny.segvault.disturbances.model.Feedback;
import im.tny.segvault.disturbances.model.NotificationRule;
import im.tny.segvault.disturbances.model.RStation;
import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.activity.TripCorrectionActivity;
import im.tny.segvault.s2ls.InNetworkState;
import im.tny.segvault.s2ls.NearNetworkState;
import im.tny.segvault.s2ls.OffNetworkState;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.s2ls.S2LS;
import im.tny.segvault.s2ls.State;
import im.tny.segvault.s2ls.routing.ChangeLineStep;
import im.tny.segvault.s2ls.routing.ConnectionWeighter;
import im.tny.segvault.s2ls.routing.EnterStep;
import im.tny.segvault.s2ls.routing.ExitStep;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.s2ls.routing.Step;
import im.tny.segvault.s2ls.wifi.BSSID;
import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.POI;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

public class MainService extends Service {
    public static final String PRIMARY_NETWORK_ID = "pt-ml";

    private API api;
    private ConnectionWeighter cweighter = new DisturbanceAwareWeighter(this);
    private WiFiChecker wfc;
    private LineStatusCache lineStatusCache = new LineStatusCache(this);
    private StatsCache statsCache = new StatsCache(this);
    private PairManager pairManager;
    private Synchronizer synchronizer;

    private final Object lock = new Object();
    private Map<String, Network> networks = new HashMap<>();
    private Map<String, S2LS> locServices = new HashMap<>();

    private Date creationDate = null;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private Handler stateTickHandler = null;

    private Realm realmForListeners;

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

    private void putNetwork(final Network net) {
        synchronized (lock) {
            // create Realm stations for the network if they don't exist already
            Realm realm = Application.getDefaultRealmInstance(this);

            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    for (Station s : net.getStations()) {
                        if (realm.where(RStation.class).equalTo("id", s.getId()).count() == 0) {
                            RStation rs = new RStation();
                            rs.setStop(s);
                            rs.setNetwork(net.getId());
                            realm.copyToRealm(rs);
                        }
                    }
                }
            });
            realm.close();

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

        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.registerOnSharedPreferenceChangeListener(generalPrefsListener);
        if (new Date().getTime() - sharedPref.getLong(PreferenceNames.LastAutoTopologyUpdateCheck, 0) > TimeUnit.HOURS.toMillis(2)) {
            SharedPreferences.Editor e = sharedPref.edit();
            e.putLong(PreferenceNames.LastAutoTopologyUpdateCheck, new Date().getTime());
            e.apply();
            checkForTopologyUpdates();
        }

        realmForListeners = Application.getDefaultRealmInstance(this);
        tripRealmResults = realmForListeners.where(Trip.class).findAll();
        tripRealmResults.addChangeListener(tripRealmChangeListener);
        feedbackRealmResults = realmForListeners.where(Feedback.class).findAll();
        feedbackRealmResults.addChangeListener(feedbackRealmChangeListener);

        pairManager = new PairManager(getApplicationContext());
        //pairManager.unpair();
        API.getInstance().setPairManager(pairManager);
        if (!pairManager.isPaired()) {
            pairManager.pairAsync();
        }

        synchronizer = new Synchronizer(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        wfc.stopScanning();
        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        sharedPref.unregisterOnSharedPreferenceChangeListener(generalPrefsListener);
        tripRealmResults.removeChangeListener(tripRealmChangeListener);
        feedbackRealmResults.removeChangeListener(feedbackRealmChangeListener);
        realmForListeners.close();
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
                case ACTION_SYNC_TRIPS:
                    Log.d("MainService", "onStartCommand SyncTrips");
                    synchronizer.attemptSync();
                    break;
                case ACTION_DISTURBANCE_NOTIFICATION: {
                    final String network = intent.getStringExtra(EXTRA_DISTURBANCE_NETWORK);
                    final String line = intent.getStringExtra(EXTRA_DISTURBANCE_LINE);
                    final String id = intent.getStringExtra(EXTRA_DISTURBANCE_ID);
                    final String status = intent.getStringExtra(EXTRA_DISTURBANCE_STATUS);
                    final boolean downtime = intent.getBooleanExtra(EXTRA_DISTURBANCE_DOWNTIME, false);
                    final long msgtime = intent.getLongExtra(EXTRA_DISTURBANCE_MSGTIME, 0);
                    handleDisturbanceNotification(network, line, id, status, downtime, msgtime);
                    break;
                }
                case ACTION_ANNOUNCEMENT_NOTIFICATION: {
                    final String network = intent.getStringExtra(EXTRA_ANNOUNCEMENT_NETWORK);
                    final String title = intent.getStringExtra(EXTRA_ANNOUNCEMENT_TITLE);
                    final String body = intent.getStringExtra(EXTRA_ANNOUNCEMENT_BODY);
                    final String url = intent.getStringExtra(EXTRA_ANNOUNCEMENT_URL);
                    final String source = intent.getStringExtra(EXTRA_ANNOUNCEMENT_SOURCE);
                    final long msgtime = intent.getLongExtra(EXTRA_ANNOUNCEMENT_MSGTIME, 0);
                    handleAnnouncementNotification(network, title, body, url, source, msgtime);
                    break;
                }
                case ACTION_END_TRIP: {
                    final String network = intent.getStringExtra(EXTRA_TRIP_NETWORK);
                    S2LS loc;
                    synchronized (lock) {
                        loc = locServices.get(network);
                    }
                    if (loc != null) {
                        loc.endCurrentTrip();
                    }
                    break;
                }
                case ACTION_END_NAVIGATION: {
                    final String network = intent.getStringExtra(EXTRA_NAVIGATION_NETWORK);
                    S2LS loc;
                    synchronized (lock) {
                        loc = locServices.get(network);
                    }
                    if (loc != null) {
                        loc.setCurrentTargetRoute(null, false);
                    }
                    break;
                }
            }
        }

        reloadFCMsubscriptions();

        UpdateTopologyJob.schedule();
        SyncTripsJob.schedule();

        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        boolean permanentForeground = sharedPref.getBoolean(PreferenceNames.PermanentForeground, false);
        if (permanentForeground) {
            startPermanentForeground();
        }

        return Service.START_STICKY;
    }

    private void loadNetworks() {
        synchronized (lock) {
            try {
                Network net = TopologyCache.loadNetwork(this, PRIMARY_NETWORK_ID, api.getEndpoint().toString());
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

    public void reloadFCMsubscriptions() {
        FirebaseMessaging fcm = FirebaseMessaging.getInstance();
        SharedPreferences sharedPref = getSharedPreferences("notifsettings", MODE_PRIVATE);
        Set<String> linePref = sharedPref.getStringSet(PreferenceNames.NotifsLines, null);
        if (linePref != null && linePref.size() != 0) {
            fcm.subscribeToTopic("disturbances");
            if (BuildConfig.DEBUG) {
                fcm.subscribeToTopic("disturbances-debug");
            }
        } else {
            fcm.unsubscribeFromTopic("disturbances");
            fcm.unsubscribeFromTopic("disturbances-debug");
        }

        Set<String> sourcePref = sharedPref.getStringSet(PreferenceNames.AnnouncementSources, null);

        for (Announcement.Source possibleSource : Announcement.getSources()) {
            if (sourcePref != null && sourcePref.contains(possibleSource.id)) {
                fcm.subscribeToTopic("announcements-" + possibleSource.id);
                if (BuildConfig.DEBUG) {
                    fcm.subscribeToTopic("announcements-debug-" + possibleSource.id);
                }
            } else {
                fcm.unsubscribeFromTopic("announcements-" + possibleSource.id);
                fcm.unsubscribeFromTopic("announcements-debug-" + possibleSource.id);
            }
        }
    }

    public void updateTopology() {
        if (!isTopologyUpdateInProgress()) {
            synchronized (lock) {
                cancelTopologyUpdate();
                currentUpdateTopologyTask = new UpdateTopologyTask();
                currentUpdateTopologyTask.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR, PRIMARY_NETWORK_ID);
            }
        }
    }

    public void updateTopology(String... network_ids) {
        if (!isTopologyUpdateInProgress()) {
            synchronized (lock) {
                cancelTopologyUpdate();
                currentUpdateTopologyTask = new UpdateTopologyTask();
                currentUpdateTopologyTask.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR, network_ids);
            }
        }
    }

    public void cancelTopologyUpdate() {
        synchronized (lock) {
            if (currentUpdateTopologyTask != null) {
                currentUpdateTopologyTask.cancel(true);
            }
        }
    }

    public boolean isTopologyUpdateInProgress() {
        return currentUpdateTopologyTask != null;
    }

    public void checkForTopologyUpdates() {
        if (!isTopologyUpdateInProgress()) {
            synchronized (lock) {
                currentCheckTopologyUpdatesTask = new CheckTopologyUpdatesTask();
                currentCheckTopologyUpdatesTask.execute(Connectivity.isConnectedWifi(this));
            }
        }
    }

    public void checkForTopologyUpdates(boolean autoUpdate) {
        if (!isTopologyUpdateInProgress()) {
            synchronized (lock) {
                currentCheckTopologyUpdatesTask = new CheckTopologyUpdatesTask();
                currentCheckTopologyUpdatesTask.execute(autoUpdate);
            }
        }
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

    public List<Station> getAllStations() {
        List<Station> stations = new ArrayList<>();
        synchronized (lock) {
            for (Network n : networks.values()) {
                stations.addAll(n.getStations());
            }
        }
        return stations;
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

    public List<POI> getAllPOIs() {
        List<POI> pois = new ArrayList<>();
        synchronized (lock) {
            for (Network n : networks.values()) {
                pois.addAll(n.getPOIs());
            }
        }
        return pois;
    }

    public POI getPOI(String id) {
        synchronized (lock) {
            for (Network n : networks.values()) {
                if (n.getPOI(id) != null) {
                    return n.getPOI(id);
                }
            }
        }
        return null;
    }

    public LineStatusCache getLineStatusCache() {
        return lineStatusCache;
    }

    public StatsCache getStatsCache() {
        return statsCache;
    }

    public Stop getLikelyNextExit(List<Connection> path, double threshold) {
        if (path.size() == 0) {
            return null;
        }
        // get the line for the latest connection
        Connection last = path.get(path.size() - 1);
        Line line = null;
        for (Line l : getAllLines()) {
            if (l.edgeSet().contains(last)) {
                line = l;
                break;
            }
        }
        if (line == null) {
            return null;
        }

        Set<Stop> alreadyVisited = new HashSet<>();
        for (Connection c : path) {
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
            if (!alreadyVisited.contains(curStop)) {
                double r = getLeaveTrainFactorForStop(curStop);
                if (maxStop == null || r > max) {
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

        if (max < threshold) {
            // most relevant station is not relevant enough
            return null;
        }
        return maxStop;
    }

    public double getLeaveTrainFactorForStop(Stop stop) {
        Realm realm = Application.getDefaultRealmInstance(this);
        long entryCount = realm.where(StationUse.class).equalTo("station.id", stop.getStation().getId()).equalTo("type", StationUse.UseType.NETWORK_ENTRY.name()).count();
        long exitCount = realm.where(StationUse.class).equalTo("station.id", stop.getStation().getId()).equalTo("type", StationUse.UseType.NETWORK_EXIT.name()).count();
        // number of times user left at this stop to transfer to another line
        long transferCount = realm.where(StationUse.class).equalTo("station.id", stop.getStation().getId()).equalTo("sourceLine", stop.getLine().getId()).equalTo("type", StationUse.UseType.INTERCHANGE.name()).count();
        realm.close();
        return entryCount * 0.3 + exitCount + transferCount;
    }

    public List<ScanResult> getLastWiFiScanResults() {
        return wfc.getLastScanResults();
    }

    public void mockLocation(Station station) {
        if (BuildConfig.DEBUG && station.getStops().size() > 0) {
            List<BSSID> bssids = new ArrayList<>();
            for (Stop s : station.getStops()) {
                bssids.addAll(WiFiLocator.getBSSIDsForStop(s));
            }
            wfc.updateBSSIDsDebug(bssids);
        }
    }

    // DEBUG:
    public String dumpDebugInfo() {
        String s = "Service created on " + creationDate.toString();
        s += (wfc.isScanning() ? String.format(". WFC scanning every %d s", wfc.getScanInterval() / 1000) : ". WFC not scanning") + "\n";
        for (Network n : getNetworks()) {
            S2LS loc;
            synchronized (lock) {
                loc = locServices.get(n.getId());
            }
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

        synchronizer.attemptSync();

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
                    publishProgress(5);
                    if (isCancelled()) break;

                    float netPart = (float) (cur_net + 1) / (float) net_count;
                    Log.d("UpdateTopologyTask", "Updating network " + n.id);

                    HashMap<String, API.Station> apiStations = new HashMap<>();
                    for (API.Station s : api.getStations()) {
                        if (s.network.equals(n.id)) {
                            apiStations.put(s.id, s);
                        }
                    }

                    publishProgress(20);
                    if (isCancelled()) break;

                    HashMap<String, API.Lobby> apiLobbies = new HashMap<>();
                    for (API.Lobby l : api.getLobbies()) {
                        if (l.network.equals(n.id)) {
                            apiLobbies.put(l.id, l);
                        }
                    }

                    publishProgress(40);
                    if (isCancelled()) break;

                    int line_count = n.lines.size();
                    int cur_line = 0;
                    HashMap<String, API.Line> apiLines = new HashMap<>();
                    for (String lineid : n.lines) {
                        API.Line l = api.getLine(lineid);
                        apiLines.put(lineid, l);
                        cur_line++;
                        publishProgress(40 + (int) (((cur_line / (float) (line_count)) * netPart) * 20));
                        if (cur_line < line_count) {
                            cur_line++;
                        }
                        if (isCancelled()) break;
                    }
                    if (isCancelled()) break;

                    List<API.Connection> connections = api.getConnections();

                    publishProgress(80);
                    if (isCancelled()) break;

                    List<API.Transfer> transfers = api.getTransfers();

                    publishProgress(90);
                    if (isCancelled()) break;

                    List<API.POI> pois = api.getPOIs();

                    publishProgress(100);

                    API.DatasetInfo info = api.getDatasetInfo(n.id);
                    lineStatusCache.clear();
                    statsCache.clear();

                    TopologyCache.saveNetwork(MainService.this, n, apiStations, apiLobbies, apiLines, connections, transfers, pois, info);
                    putNetwork(TopologyCache.loadNetwork(MainService.this, n.id, api.getEndpoint().toString()));
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
                List<API.DatasetInfo> datasetInfos = api.getDatasetInfos();
                synchronized (lock) {
                    for (API.DatasetInfo di : datasetInfos) {
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
    public static final String ACTION_SYNC_TRIPS = "im.tny.segvault.disturbances.action.syncTrips";

    public static final String ACTION_CURRENT_TRIP_UPDATED = "im.tny.segvault.disturbances.action.trip.current.updated";
    public static final String ACTION_CURRENT_TRIP_ENDED = "im.tny.segvault.disturbances.action.trip.current.ended";
    public static final String ACTION_S2LS_STATUS_CHANGED = "im.tny.segvault.disturbances.action.s2ls.status.changed";

    public static final String ACTION_CACHE_EXTRAS_PROGRESS = "im.tny.segvault.disturbances.action.cacheextras.progress";
    public static final String EXTRA_CACHE_EXTRAS_PROGRESS_CURRENT = "im.tny.segvault.disturbances.extra.cacheextras.progress.current";
    public static final String EXTRA_CACHE_EXTRAS_PROGRESS_TOTAL = "im.tny.segvault.disturbances.extra.cacheextras.progress.total";
    public static final String ACTION_CACHE_EXTRAS_FINISHED = "im.tny.segvault.disturbances.action.cacheextras.finished";
    public static final String EXTRA_CACHE_EXTRAS_FINISHED = "im.tny.segvault.disturbances.extra.cacheextras.finished";

    public static class LocationJobCreator implements JobCreator {

        @Override
        public Job create(String tag) {
            switch (tag) {
                case UpdateTopologyJob.TAG:
                    return new UpdateTopologyJob();
                case SyncTripsJob.TAG:
                    return new SyncTripsJob();
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

    public static class SyncTripsJob extends Job {
        public static final String TAG = "job_sync_trips";

        @Override
        @NonNull
        protected Result onRunJob(Params params) {
            Intent intent = new Intent(getContext(), MainService.class).setAction(ACTION_SYNC_TRIPS);
            getContext().startService(intent);
            return Result.SUCCESS;
        }

        public static void schedule() {
            schedule(true);
        }

        public static void schedule(boolean updateCurrent) {
            new JobRequest.Builder(SyncTripsJob.TAG)
                    .setExecutionWindow(TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(48))
                    .setBackoffCriteria(TimeUnit.HOURS.toMillis(1), JobRequest.BackoffPolicy.EXPONENTIAL)
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
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

    public static void startForDisturbanceNotification(Context context, String network, String line,
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
        Set<String> linePref = sharedPref.getStringSet(PreferenceNames.NotifsLines, null);

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

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (!downtime && !sharedPref.getBoolean(PreferenceNames.NotifsServiceResumed, true)) {
            // notifications for normal service resumed disabled
            notificationManager.cancel(id.hashCode());
            return;
        }

        Realm realm = Application.getDefaultRealmInstance(this);
        for (NotificationRule rule : realm.where(NotificationRule.class).findAll()) {
            if (rule.isEnabled() && rule.applies(new Date(msgtime))) {
                realm.close();
                return;
            }
        }
        realm.close();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_INITIAL_FRAGMENT, "nav_disturbances");
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        String title = String.format(getString(R.string.notif_disturbance_title), Util.getLineNames(this, sline)[0]);

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
                .setWhen(msgtime)
                .setSound(Uri.parse(sharedPref.getString(downtime ? PreferenceNames.NotifsRingtone : PreferenceNames.NotifsRegularizationRingtone, "content://settings/system/notification_sound")))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);

        if (sharedPref.getBoolean(downtime ? PreferenceNames.NotifsVibrate : PreferenceNames.NotifsRegularizationVibrate, false)) {
            notificationBuilder.setVibrate(new long[]{0, 100, 100, 150, 150, 200});
        } else {
            notificationBuilder.setVibrate(new long[]{0l});
        }

        notificationManager.notify(id.hashCode(), notificationBuilder.build());
    }

    private static final String ACTION_ANNOUNCEMENT_NOTIFICATION = "im.tny.segvault.disturbances.action.notification.announcement";
    private static final String EXTRA_ANNOUNCEMENT_NETWORK = "im.tny.segvault.disturbances.extra.notification.announcement.network";
    private static final String EXTRA_ANNOUNCEMENT_TITLE = "im.tny.segvault.disturbances.extra.notification.announcement.title";
    private static final String EXTRA_ANNOUNCEMENT_BODY = "im.tny.segvault.disturbances.extra.notification.announcement.body";
    private static final String EXTRA_ANNOUNCEMENT_URL = "im.tny.segvault.disturbances.extra.notification.announcement.url";
    private static final String EXTRA_ANNOUNCEMENT_SOURCE = "im.tny.segvault.disturbances.extra.notification.announcement.source";

    private static final String EXTRA_ANNOUNCEMENT_MSGTIME = "im.tny.segvault.disturbances.extra.notification.announcement.msgtime";

    public static void startForAnnouncementNotification(Context context, String network, String title,
                                                        String body, String url, String source, long messageTime) {
        Intent intent = new Intent(context, MainService.class);
        intent.setAction(ACTION_ANNOUNCEMENT_NOTIFICATION);
        intent.putExtra(EXTRA_ANNOUNCEMENT_NETWORK, network);
        intent.putExtra(EXTRA_ANNOUNCEMENT_TITLE, title);
        intent.putExtra(EXTRA_ANNOUNCEMENT_BODY, body);
        intent.putExtra(EXTRA_ANNOUNCEMENT_URL, url);
        intent.putExtra(EXTRA_ANNOUNCEMENT_SOURCE, source);
        intent.putExtra(EXTRA_ANNOUNCEMENT_MSGTIME, messageTime);
        context.startService(intent);
    }

    private void handleAnnouncementNotification(String network, String title,
                                                String body, String url, String sourceId,
                                                long msgtime) {
        Log.d("MainService", "handleAnnouncementNotification");
        SharedPreferences sharedPref = getSharedPreferences("notifsettings", MODE_PRIVATE);
        Set<String> sourcePref = sharedPref.getStringSet(PreferenceNames.AnnouncementSources, null);

        Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
        notificationIntent.setData(Uri.parse(url));
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Announcement.Source source = Announcement.getSource(sourceId);
        if (source == null) {
            return;
        }

        if (title.isEmpty()) {
            title = String.format(getString(R.string.notif_announcement_alt_title), getString(source.nameResourceId));
        }

        if (body.isEmpty()) {
            body = getString(R.string.frag_announcement_no_text);
        }

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(title);
        bigTextStyle.bigText(body);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.ic_pt_ml_notif)
                .setColor(source.color)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setWhen(msgtime)
                .setSound(Uri.parse(sharedPref.getString(PreferenceNames.NotifsAnnouncementRingtone, "content://settings/system/notification_sound")))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent);

        if (sharedPref.getBoolean(PreferenceNames.NotifsAnnouncementVibrate, false)) {
            notificationBuilder.setVibrate(new long[]{0, 100, 100, 150, 150, 200});
        } else {
            notificationBuilder.setVibrate(new long[]{0l});
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(url.hashCode(), notificationBuilder.build());
    }

    private static final int PERMANENT_FOREGROUND_NOTIFICATION_ID = -101;
    private static final int ROUTE_NOTIFICATION_ID = -100;
    public static final String ACTION_END_NAVIGATION = "im.tny.segvault.disturbances.action.route.current.end";
    public static final String EXTRA_NAVIGATION_NETWORK = "im.tny.segvault.disturbances.extra.route.current.end.network";
    public static final String ACTION_END_TRIP = "im.tny.segvault.disturbances.action.trip.current.end";
    public static final String EXTRA_TRIP_NETWORK = "im.tny.segvault.disturbances.extra.trip.current.end.network";

    private void updateRouteNotification(S2LS loc) {
        updateRouteNotification(loc, false);
    }

    private void updateRouteNotification(S2LS loc, boolean highPriorityNotification) {
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
            inboxStyle.setSummaryText(String.format(getString(R.string.notif_route_navigating_status), currentRoute.getTarget().getName()));
            Step nextStep = currentRoute.getNextStep(currentPath);
            if (nextStep instanceof EnterStep) {
                if (currentPath != null && currentPath.getCurrentStop() != null && currentRoute.checkPathStartsRoute(currentPath)) {
                    title = String.format(getString(R.string.notif_route_catch_train_title), ((EnterStep) nextStep).getDirection().getName(12));
                } else {
                    title = String.format(getString(R.string.notif_route_enter_station_title), nextStep.getStation().getName(15));
                }
                // TODO: show "encurtamentos" warnings here if applicable
                statusLines.add(String.format(getString(R.string.notif_route_catch_train_status), ((EnterStep) nextStep).getDirection().getName()));
                color = currentRoute.getSourceStop().getLine().getColor();
            } else if (nextStep instanceof ChangeLineStep) {
                ChangeLineStep clStep = (ChangeLineStep) nextStep;
                String lineName = Util.getLineNames(this, clStep.getTarget())[0];
                String titleStr;
                if (currentPath != null && currentPath.getCurrentStop() != null && currentPath.getCurrentStop().getStation() == nextStep.getStation()) {
                    titleStr = String.format(getString(R.string.notif_route_catch_train_line_change_title),
                            lineName);
                } else {
                    titleStr = String.format(getString(R.string.notif_route_line_change_title),
                            nextStep.getStation().getName(10),
                            lineName);
                }
                int lStart = titleStr.indexOf(lineName);
                int lEnd = lStart + lineName.length();
                Spannable sb = new SpannableString(titleStr);
                sb.setSpan(new ForegroundColorSpan(clStep.getTarget().getColor()), lStart, lEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                title = sb;

                // TODO: show "encurtamentos" warnings here if applicable
                sb = new SpannableString(
                        String.format(getString(R.string.notif_route_catch_train_status),
                                clStep.getDirection().getName())
                );
                sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                statusLines.add(sb);
                color = clStep.getTarget().getColor();
            } else if (nextStep instanceof ExitStep) {
                if (currentPath != null && currentPath.getCurrentStop().getStation() == nextStep.getStation()) {
                    title = getString(R.string.notif_route_leave_train_now);
                } else if (currentPath != null && currentPath.getNextStop() != null &&
                        new Date().getTime() - currentPath.getCurrentStopEntryTime().getTime() > 30 * 1000 &&
                        nextStep.getStation() == currentPath.getNextStop().getStation()) {
                    title = getString(R.string.notif_route_leave_train_next);
                } else {
                    title = String.format(getString(R.string.notif_route_leave_train), nextStep.getStation().getName(20));
                }
            }
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

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setStyle(inboxStyle)
                .setColor(color)
                .setContentTitle(title)
                .setContentText(singleLineStatus)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true);

        if (highPriorityNotification) {
            stopForeground(true);
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
        }

        if (currentRoute != null) {
            notificationBuilder.setSmallIcon(R.drawable.ic_navigation_white_24dp);
            Intent stopIntent = new Intent(this, MainService.class);
            stopIntent.setAction(ACTION_END_NAVIGATION);
            stopIntent.putExtra(EXTRA_NAVIGATION_NETWORK, loc.getNetwork().getId());
            PendingIntent pendingStopIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(),
                    stopIntent, 0);
            notificationBuilder.addAction(R.drawable.ic_close_black_24dp, getString(R.string.notif_route_end_navigation), pendingStopIntent);
        } else {
            notificationBuilder.setSmallIcon(R.drawable.ic_trip_notif);
            if (loc.canRequestEndOfTrip()) {
                Intent stopIntent = new Intent(this, MainService.class);
                stopIntent.setAction(ACTION_END_TRIP);
                stopIntent.putExtra(EXTRA_TRIP_NETWORK, loc.getNetwork().getId());
                PendingIntent pendingStopIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(),
                        stopIntent, 0);
                notificationBuilder.addAction(R.drawable.ic_stop_black_24dp, getString(R.string.notif_route_end_trip), pendingStopIntent);
            }
        }

        startForeground(ROUTE_NOTIFICATION_ID, notificationBuilder.build());
    }

    public class S2LSChangeListener implements S2LS.EventListener {
        @Override
        public void onStateChanged(final S2LS loc) {
            Log.d("onStateChanged", "State changed");

            stateTickHandler.removeCallbacksAndMessages(null);
            if (loc.getState().getPreferredTickIntervalMillis() != 0) {
                doTick(loc.getState());
            }

            SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
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
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
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
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
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
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
            bm.sendBroadcast(intent);

            if (!checkStopForeground(s2ls)) {
                updateRouteNotification(s2ls);
            }

            SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
            boolean openTripCorrection = sharedPref.getBoolean(PreferenceNames.AutoOpenTripCorrection, false);
            boolean openVisitCorrection = sharedPref.getBoolean(PreferenceNames.AutoOpenVisitCorrection, false);
            if (openTripCorrection && (path.getEdgeList().size() > 0 || openVisitCorrection)) {
                intent = new Intent(getApplicationContext(), TripCorrectionActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(TripCorrectionActivity.EXTRA_NETWORK_ID, s2ls.getNetwork().getId());
                intent.putExtra(TripCorrectionActivity.EXTRA_TRIP_ID, tripId);
                intent.putExtra(TripCorrectionActivity.EXTRA_IS_STANDALONE, true);
                startActivity(intent);
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
        }

        @Override
        public void onRouteCompleted(S2LS s2ls, Path path, Route route) {
            Log.d("onRouteCompleted", "Route completed");
            if (!checkStopForeground(s2ls)) {
                updateRouteNotification(s2ls);
            }
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

    private boolean checkStopForeground(S2LS loc) {
        if (loc.getCurrentTargetRoute() == null && !(loc.getState() instanceof InNetworkState)) {
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

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(MainService.this)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_permanent_foreground_description))
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_sleep_white_24dp)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true);
        startForeground(PERMANENT_FOREGROUND_NOTIFICATION_ID, notificationBuilder.build());
    }

    private class SubmitRealtimeLocationTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            API.RealtimeLocationRequest r = new API.RealtimeLocationRequest();
            r.s = strings[0];
            if (strings.length > 1) {
                r.d = strings[1];
            }
            try {
                api.postRealtimeLocation(r);
            } catch (APIException e) {
                // oh well...
            }
            return null;
        }
    }

    public void cacheAllExtras(String... network_ids) {
        ExtraContentCache.clearAllCachedExtras(this);
        for (String id : network_ids) {
            Network network = getNetwork(id);
            ExtraContentCache.cacheAllExtras(this, new ExtraContentCache.OnCacheAllListener() {
                @Override
                public void onSuccess() {
                    Intent intent = new Intent(ACTION_CACHE_EXTRAS_FINISHED);
                    intent.putExtra(EXTRA_CACHE_EXTRAS_FINISHED, true);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
                    bm.sendBroadcast(intent);
                }

                @Override
                public void onProgress(int current, int total) {
                    Intent intent = new Intent(ACTION_CACHE_EXTRAS_PROGRESS);
                    intent.putExtra(EXTRA_CACHE_EXTRAS_PROGRESS_CURRENT, current);
                    intent.putExtra(EXTRA_CACHE_EXTRAS_PROGRESS_TOTAL, total);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
                    bm.sendBroadcast(intent);
                }

                @Override
                public void onFailure() {
                    Intent intent = new Intent(ACTION_CACHE_EXTRAS_FINISHED);
                    intent.putExtra(EXTRA_CACHE_EXTRAS_FINISHED, false);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(MainService.this);
                    bm.sendBroadcast(intent);
                }
            }, network);
        }
    }

    private SharedPreferences.OnSharedPreferenceChangeListener generalPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            switch (key) {
                case PreferenceNames.LocationEnable:
                    if (prefs.getBoolean(PreferenceNames.LocationEnable, true))
                        wfc.startScanningIfWiFiEnabled();
                    else
                        wfc.stopScanning();
                    break;
                case PreferenceNames.PermanentForeground:
                    checkStopForeground(getS2LS(PRIMARY_NETWORK_ID));
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

}
