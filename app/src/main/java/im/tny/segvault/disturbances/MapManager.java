package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import im.tny.segvault.disturbances.exception.APIException;
import im.tny.segvault.disturbances.exception.CacheException;
import im.tny.segvault.s2ls.routing.ConnectionWeighter;
import im.tny.segvault.s2ls.wifi.BSSID;
import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.POI;
import im.tny.segvault.subway.Schedule;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;
import im.tny.segvault.subway.WorldPath;

public class MapManager {
    public static final String PRIMARY_NETWORK_ID = "pt-ml";
    private static final String[] supportedNetworkIDs = new String[]{PRIMARY_NETWORK_ID};

    private Context context;
    private final Object lock = new Object();
    private Map<String, Network> networks = new HashMap<>();
    private List<Network> loadedNotListened = new ArrayList<>();
    private ConnectionWeighter cweighter;

    public MapManager(Context applicationContext) {
        this.context = applicationContext;
        cweighter = new ETAWeighter(applicationContext);
    }

    private OnLoadListener loadListener;

    public void setOnLoadListener(OnLoadListener listener) {
        this.loadListener = listener;
        if (listener != null) {
            for (Network net : loadedNotListened) {
                listener.onNetworkLoaded(net);
            }
        }
        loadedNotListened.clear();
    }

    private OnUpdateListener updateListener;

    public void setOnUpdateListener(OnUpdateListener listener) {
        this.updateListener = listener;
    }

    public Collection<Network> getNetworks() {
        synchronized (lock) {
            if (networks.isEmpty()) {
                loadNetworks();
            }
            return networks.values();
        }
    }

    public Network getNetwork(String id) {
        synchronized (lock) {
            Network network = networks.get(id);
            if (network == null) {
                try {
                    return loadNetwork(id);
                } catch (CacheException ex) {
                    return null;
                }
            }
            return network;
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

    private UpdateTopologyTask currentUpdateTopologyTask = null;

    private static class UpdateTopologyTask extends AsyncTask<String, Integer, Boolean> {
        private MapManager mapManager;

        public UpdateTopologyTask(MapManager mapManager) {
            this.mapManager = mapManager;
        }

        protected Boolean doInBackground(String... networkIds) {
            int net_count = networkIds.length;
            publishProgress(0);
            try {
                API api = API.getInstance();
                for (int cur_net = 0; cur_net < net_count; cur_net++) {
                    API.Network n = api.getNetwork(networkIds[cur_net]);
                    publishProgress(5);
                    if (isCancelled()) break;

                    Log.d("UpdateTopologyTask", "Updating network " + n.id);

                    HashMap<String, API.Station> apiStations = new HashMap<>();
                    for (API.Station s : api.getStations()) {
                        if (s.network.equals(n.id)) {
                            apiStations.put(s.id, s);
                        }
                    }

                    publishProgress(10);
                    if (isCancelled()) break;

                    HashMap<String, API.Lobby> apiLobbies = new HashMap<>();
                    for (API.Lobby l : api.getLobbies()) {
                        if (l.network.equals(n.id)) {
                            apiLobbies.put(l.id, l);
                        }
                    }

                    publishProgress(20);
                    if (isCancelled()) break;

                    int line_count = n.lines.size();
                    int cur_line = 0;
                    HashMap<String, API.Line> apiLines = new HashMap<>();
                    for (String lineid : n.lines) {
                        API.Line l = api.getLine(lineid);
                        apiLines.put(lineid, l);
                        cur_line++;
                        publishProgress(20 + (int) ((cur_line / (float) (line_count)) * 20f));
                        if (cur_line < line_count) {
                            cur_line++;
                        }
                        if (isCancelled()) break;
                    }
                    if (isCancelled()) break;

                    List<API.Connection> connections = api.getConnections();

                    publishProgress(50);
                    if (isCancelled()) break;

                    List<API.Transfer> transfers = api.getTransfers();

                    publishProgress(55);
                    if (isCancelled()) break;

                    List<API.POI> pois = api.getPOIs();

                    publishProgress(60);
                    if (isCancelled()) break;

                    List<API.Diagram> maps = api.getDiagrams();

                    publishProgress(70);
                    if (isCancelled()) break;

                    clearAllCachedDiagrams(mapManager.context);
                    for (API.Diagram map : maps) {
                        if (map instanceof API.HTMLMap && ((API.HTMLMap) map).cache) {
                            downloadDiagram(mapManager.context, (API.HTMLMap) map);
                        }
                    }

                    publishProgress(80);
                    if (isCancelled()) break;

                    API.DatasetInfo info = api.getDatasetInfo(n.id);

                    Network network = mapManager.saveNetwork(n, apiStations, apiLobbies, apiLines, connections, transfers, pois, maps, info);
                    Coordinator.get(mapManager.context).getLineStatusCache().clear();

                    publishProgress(90);
                    if (isCancelled()) break;

                    // update help overlays and infra contents
                    CacheManager dm = Coordinator.get(mapManager.context).getDataManager();
                    // remove all overlayfs files
                    dm.range((key, storeDate, data) -> {
                        if(key.startsWith("overlayfs/")) {
                            dm.remove(key);
                        }
                        return true;
                    }, Util.OverlaidFile.class);
                    API.AndroidClientConfigs clientConfigs = api.getAndroidClientConfigs();
                    for(String name : clientConfigs.helpOverlays.keySet()) {
                        String path = clientConfigs.helpOverlays.get(name);
                        Util.OverlaidFile f = new Util.OverlaidFile();
                        f.contents = api.getFileRelativeToEndpoint(path);
                        dm.put(String.format("overlayfs/help/%s", name), f);
                    }

                    publishProgress(95);
                    if (isCancelled()) break;

                    for(String name : clientConfigs.infra.keySet()) {
                        String path = clientConfigs.infra.get(name);
                        Util.OverlaidFile f = new Util.OverlaidFile();
                        f.contents = api.getFileRelativeToEndpoint(path);
                        dm.put(String.format("overlayfs/infra/%s", name), f);
                    }

                    publishProgress(100);
                    if (isCancelled()) break;

                    if (mapManager.updateListener != null) {
                        mapManager.updateListener.onNetworkUpdated(network);
                    }

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
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(mapManager.context);
            bm.sendBroadcast(intent);
        }

        protected void onPostExecute(Boolean result) {
            Log.d("UpdateTopologyTask", result.toString());
            synchronized (mapManager.lock) {
                mapManager.currentUpdateTopologyTask = null;
            }
            Intent intent = new Intent(ACTION_UPDATE_TOPOLOGY_FINISHED);
            intent.putExtra(EXTRA_UPDATE_TOPOLOGY_FINISHED, result);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(mapManager.context);
            bm.sendBroadcast(intent);
        }

        @Override
        protected void onCancelled() {
            Log.d("UpdateTopologyTask", "onCancelled");
            synchronized (mapManager.lock) {
                mapManager.currentUpdateTopologyTask = null;
            }
            Intent intent = new Intent(ACTION_UPDATE_TOPOLOGY_CANCELLED);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(mapManager.context);
            bm.sendBroadcast(intent);
        }
    }

    public static class CheckUpdatesJob extends Job {
        public static final String TAG = "job_check_updates";
        public static final String NO_UPDATE_TAG = "job_check_updates_no";
        public static final String AUTO_UPDATE_TAG = "job_check_updates_auto";

        private boolean autoUpdate;
        private boolean updateDependingOnConnectivity = false;

        public CheckUpdatesJob(String tag) {
            switch (tag) {
                case TAG:
                    updateDependingOnConnectivity = true;
                    break;
                case NO_UPDATE_TAG:
                    autoUpdate = false;
                    break;
                case AUTO_UPDATE_TAG:
                    autoUpdate = true;
                    break;
            }
        }

        @Override
        @NonNull
        protected Result onRunJob(Params params) {
            MapManager mapManager = Coordinator.get(getContext()).getMapManager();
            List<String> outOfDateNetworks = new ArrayList<>();
            try {
                List<API.DatasetInfo> datasetInfos = API.getInstance().getDatasetInfos();
                for (API.DatasetInfo di : datasetInfos) {
                    Network net = mapManager.getNetwork(di.network);
                    if (net == null || !di.version.equals(net.getDatasetVersion())) {
                        outOfDateNetworks.add(di.network);
                    }
                }
            } catch (APIException e) {
                return Result.RESCHEDULE;
            }

            if (!outOfDateNetworks.isEmpty()) {
                if (updateDependingOnConnectivity) {
                    autoUpdate = Connectivity.isConnectedWifi(getContext());
                }
                if (autoUpdate) {
                    String[] networksArray = new String[outOfDateNetworks.size()];
                    outOfDateNetworks.toArray(networksArray);
                    Coordinator.get(getContext()).getMapManager().updateTopologySync(networksArray);
                } else {
                    Intent intent = new Intent(ACTION_TOPOLOGY_UPDATE_AVAILABLE);
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(mapManager.context);
                    bm.sendBroadcast(intent);
                }
            }

            return Result.SUCCESS;
        }

        public static void schedule() {
            schedule(true);
        }

        public static void schedule(boolean updateCurrent) {
            new JobRequest.Builder(TAG)
                    .setExecutionWindow(TimeUnit.HOURS.toMillis(12), TimeUnit.HOURS.toMillis(36))
                    .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobRequest.BackoffPolicy.EXPONENTIAL)
                    .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                    .setUpdateCurrent(updateCurrent)
                    .build()
                    .schedule();
        }
    }

    private void loadNetworks() {
        synchronized (lock) {
            try {
                for (String id : supportedNetworkIDs) {
                    loadNetwork(id);
                }
            } catch (CacheException e) {
                // cache invalid, attempt to reload topology
                updateTopology();
            }
        }
    }

    public void updateTopology() {
        synchronized (lock) {
            if (!isTopologyUpdateInProgress()) {
                cancelTopologyUpdate();
                currentUpdateTopologyTask = new UpdateTopologyTask(this);
                currentUpdateTopologyTask.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR, supportedNetworkIDs);
            }
        }
    }

    public void updateTopology(String... network_ids) {
        synchronized (lock) {
            if (!isTopologyUpdateInProgress()) {
                cancelTopologyUpdate();
                currentUpdateTopologyTask = new UpdateTopologyTask(this);
                currentUpdateTopologyTask.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR, network_ids);
            }
        }
    }

    private boolean updateTopologySync(String... network_ids) {
        synchronized (lock) {
            if (!isTopologyUpdateInProgress()) {
                cancelTopologyUpdate();
                currentUpdateTopologyTask = new UpdateTopologyTask(this);
            } else {
                return false;
            }
        }
        try {
            return currentUpdateTopologyTask.executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR, network_ids).get();
        } catch (InterruptedException e) {
            return false;
        } catch (ExecutionException e) {
            return false;
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
        synchronized (lock) {
            return currentUpdateTopologyTask != null;
        }
    }

    public void checkForTopologyUpdates() {
        synchronized (lock) {
            if (!isTopologyUpdateInProgress() && lastTopologyUpdatesCheckLongAgo()) {
                lastTopologyUpdatesCheck = new Date();
                new JobRequest.Builder(CheckUpdatesJob.TAG)
                        .startNow()
                        .build()
                        .schedule();
            }
        }
    }

    public void checkForTopologyUpdates(boolean autoUpdate) {
        synchronized (lock) {
            if (!isTopologyUpdateInProgress() && lastTopologyUpdatesCheckLongAgo()) {
                lastTopologyUpdatesCheck = new Date();
                String tag = CheckUpdatesJob.NO_UPDATE_TAG;
                if (autoUpdate) {
                    tag = CheckUpdatesJob.AUTO_UPDATE_TAG;
                }
                new JobRequest.Builder(tag)
                        .startNow()
                        .build()
                        .schedule();
            }
        }
    }

    private Date lastTopologyUpdatesCheck = new Date(0);

    private boolean lastTopologyUpdatesCheckLongAgo() {
        return new Date().getTime() - lastTopologyUpdatesCheck.getTime() > 5000;
    }


    static private class Topology implements Serializable {
        public API.Network network;
        public HashMap<String, API.Station> stations;
        public HashMap<String, API.Lobby> lobbies;
        public HashMap<String, API.Line> lines;
        public List<API.Connection> connections;
        public List<API.Transfer> transfers;
        public List<API.POI> pois;
        public List<API.Diagram> maps;
        public API.DatasetInfo info;
    }

    private Network saveNetwork(API.Network network,
                                HashMap<String, API.Station> stations,
                                HashMap<String, API.Lobby> lobbies,
                                HashMap<String, API.Line> lines,
                                List<API.Connection> connections,
                                List<API.Transfer> transfers,
                                List<API.POI> pois,
                                List<API.Diagram> maps,
                                API.DatasetInfo info) throws CacheException {
        Topology t = new Topology();
        t.network = network;
        t.stations = stations;
        t.lobbies = lobbies;
        t.lines = lines;
        t.connections = connections;
        t.transfers = transfers;
        t.pois = pois;
        t.maps = maps;
        t.info = info;

        String filename = "net-" + network.id;
        try {
            FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(t);
            os.close();
            fos.close();
        } catch (FileNotFoundException e) {
            throw new CacheException(e).addInfo("File " + filename + " not found");
        } catch (IOException e) {
            throw new CacheException(e).addInfo("IO Exception");
        }

        // reload network
        return loadNetwork(network.id);
    }

    private Network loadNetwork(String id) throws CacheException {
        String filename = "net-" + id;

        Topology t = null;
        try {
            FileInputStream fis = context.openFileInput(filename);
            ObjectInputStream is = new ObjectInputStream(fis);
            t = (Topology) is.readObject();
            is.close();
            fis.close();
        } catch (FileNotFoundException e) {
            throw new CacheException(e).addInfo("File " + filename + " not found");
        } catch (IOException e) {
            throw new CacheException(e).addInfo("IO exception");
        } catch (ClassNotFoundException e) {
            throw new CacheException(e).addInfo("Class not found");
        } catch (Exception e) {
            e.printStackTrace();
            throw new CacheException(e).addInfo("Unforeseen exception");
        }
        final Network net = new Network(t.network.id, t.network.mainLocale, t.network.names, t.network.typCars,
                t.network.holidays, t.network.timezone, t.network.newsURL);

        for (API.POI poi : t.pois) {
            net.addPOI(new POI(poi.id, poi.type, poi.worldCoord, poi.webURL, poi.mainLocale, poi.names));
        }

        for (String lineid : t.network.lines) {
            API.Line l = t.lines.get(lineid);
            Line line = new Line(net, l.mainLocale, l.names, new HashSet<Stop>(), l.id, l.typCars, l.order);
            line.setColor(Color.parseColor("#" + l.color));
            line.setExternalID(l.externalID);
            boolean isFirstStationInLine = true;
            for (String sid : l.stations) {
                API.Station s = t.stations.get(sid);
                Station station = net.getStation(s.id);
                if (station == null) {
                    Map<String, String> triviaURLs = new HashMap<>();
                    for (Map.Entry<String, String> entry : s.triviaURLs.entrySet()) {
                        triviaURLs.put(entry.getKey(), API.getInstance().getEndpoint().toString() + entry.getValue());
                    }

                    Map<String, Map<String, String>> connURLs = new HashMap<>();
                    for (Map.Entry<String, Map<String, String>> entry : s.connURLs.entrySet()) {
                        Map<String, String> urls = new HashMap<>();
                        for (Map.Entry<String, String> localeEntry : entry.getValue().entrySet()) {
                            urls.put(localeEntry.getKey(), API.getInstance().getEndpoint().toString() + localeEntry.getValue());
                        }
                        connURLs.put(entry.getKey(), urls);
                    }

                    station = new Station(net, s.id, s.name, s.altNames, s.tags, s.lowTags, triviaURLs);
                    station.setConnectionURLs(connURLs);

                    // Lobbies
                    for (String lid : s.lobbies) {
                        API.Lobby alobby = t.lobbies.get(lid);
                        Lobby lobby = new Lobby(station, alobby.id, alobby.name);
                        for (API.Exit aexit : alobby.exits) {
                            Lobby.Exit exit = new Lobby.Exit(aexit.id, aexit.worldCoord, aexit.streets, aexit.type);
                            lobby.addExit(exit);
                        }
                        for (API.Schedule asched : alobby.schedule) {
                            Schedule sched = new Schedule(
                                    asched.holiday, asched.day, asched.open, asched.openTime * 1000, asched.duration * 1000);
                            lobby.addSchedule(sched);
                        }
                        station.addLobby(lobby);
                    }

                    // POIs
                    for (String poiId : s.pois) {
                        POI poi = net.getPOI(poiId);
                        if (poi != null) {
                            station.addPOI(poi);
                        }
                    }
                }
                Stop stop = new Stop(station, line);
                line.addVertex(stop);
                station.addVertex(stop);
                if (isFirstStationInLine) {
                    line.setFirstStop(stop);
                    isFirstStationInLine = false;
                }

                // WiFi APs
                for (API.WiFiAP w : s.wiFiAPs) {
                    // take line affinity into account
                    if (w.line.equals(line.getId())) {
                        WiFiLocator.addBSSIDforStop(stop, new BSSID(w.bssid));
                    }
                }
            }

            for (API.Schedule asched : l.schedule) {
                Schedule sched = new Schedule(
                        asched.holiday, asched.day, asched.open, asched.openTime * 1000, asched.duration * 1000);
                line.addSchedule(sched);
            }

            for (API.WorldPath apath : l.worldPaths) {
                WorldPath path = new WorldPath(apath.id, apath.path);
                line.addPath(path);
            }
            net.addLine(line);
        }

        // Connections are within stations in the same line
        for (API.Connection c : t.connections) {
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
                newConnection.setTimes(new Connection.Times(c.typWaitS, c.typStopS, c.typS));
                newConnection.setWorldLength(c.worldLength);
                net.setEdgeWeight(newConnection, c.typS);
            }
        }

        for (API.Transfer tr : t.transfers) {
            Transfer newTransfer = new Transfer();
            // find stations with the right IDs for each line
            Station station = net.getStation(tr.station);
            for (Stop from : station.vertexSet()) {
                for (Stop to : station.vertexSet()) {
                    if (from.getLine().getId().equals(tr.from) && to.getLine().getId().equals(tr.to)) {
                        net.addEdge(from, to, newTransfer);
                        net.setEdgeWeight(newTransfer, tr.typS);
                        newTransfer.setTimes(new Connection.Times(0, 0, tr.typS));
                    }
                }

            }
        }

        for (API.Schedule asched : t.network.schedule) {
            Schedule sched = new Schedule(
                    asched.holiday, asched.day, asched.open, asched.openTime * 1000, asched.duration * 1000);
            net.addSchedule(sched);
        }

        for (API.Diagram map : t.maps) {
            if (map instanceof API.WorldMap) {
                net.addMap(new Network.WorldMap());
            } else if (map instanceof API.HTMLMap) {
                String url = ((API.HTMLMap) map).url;
                if (((API.HTMLMap) map).cache) {
                    url = buildCachedDiagramFilename((API.HTMLMap) map);
                    url = "file://" + context.getFilesDir().getAbsolutePath() + "/" + url;
                } else {
                    url = API.getInstance().getEndpoint().resolve(url).normalize().toString();
                }
                net.addMap(new Network.HtmlDiagram(url, ((API.HTMLMap) map).wideViewport));
            }
        }

        net.setDatasetAuthors(t.info.authors);
        net.setDatasetVersion(t.info.version);
        net.setEdgeWeighter(cweighter);

        synchronized (lock) {
            networks.put(net.getId(), net);
        }

        if (loadListener != null) {
            loadListener.onNetworkLoaded(net);
        } else {
            loadedNotListened.add(net);
        }

        return net;
    }

    public static String buildCachedDiagramFilename(API.HTMLMap map) {
        return "map-" + Integer.toString(map.url.hashCode()) + ".html";
    }

    public static void clearAllCachedDiagrams(Context context) {
        File cacheDir = context.getFilesDir();
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith("map-")) {
                    file.delete();
                }
            }
        }
    }

    public static void downloadDiagram(Context context, API.HTMLMap map) {
        try {
            String url = map.url;
                url = API.getInstance().getEndpoint().resolve(url).normalize().toString();
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setRequestProperty("Accept-Encoding", "gzip");
            h.setRequestMethod("GET");
            h.setDoInput(true);

            InputStream is;
            int code;
            try {
                // Will throw IOException if server responds with 401.
                code = h.getResponseCode();
            } catch (IOException e) {
                // Will return 401, because now connection has the correct internal state.
                code = h.getResponseCode();
            }
            if (code == 200) {
                is = h.getInputStream();
            } else {
                return;
            }

            if ("gzip".equals(h.getContentEncoding())) {
                is = new GZIPInputStream(is);
            }

            File cacheDir = context.getFilesDir();
            File file = new File(cacheDir, buildCachedDiagramFilename(map));

            BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8);

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file));
            String line;
            while ((line = reader.readLine()) != null)
                outputStreamWriter.write(line + "\n");

            outputStreamWriter.close();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface OnLoadListener {
        void onNetworkLoaded(Network network);
    }

    public interface OnUpdateListener {
        void onNetworkUpdated(Network network);
    }

    public static final String ACTION_TOPOLOGY_UPDATE_AVAILABLE = "im.tny.segvault.disturbances.action.topology.update.available";

    public static final String ACTION_UPDATE_TOPOLOGY_PROGRESS = "im.tny.segvault.disturbances.action.topology.update.progress";
    public static final String EXTRA_UPDATE_TOPOLOGY_PROGRESS = "im.tny.segvault.disturbances.extra.topology.update.progress";
    public static final String ACTION_UPDATE_TOPOLOGY_FINISHED = "im.tny.segvault.disturbances.action.topology.update.finished";
    public static final String EXTRA_UPDATE_TOPOLOGY_FINISHED = "im.tny.segvault.disturbances.extra.topology.update.finished";
    public static final String ACTION_UPDATE_TOPOLOGY_CANCELLED = "im.tny.segvault.disturbances.action.topology.update.cancelled";
}
