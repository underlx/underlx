package im.tny.segvault.disturbances;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

public class LocationService extends Service {
    private API api;
    private WiFiChecker wfc = new WiFiChecker();

    private final Object lock = new Object();
    private Map<String, Network> networks = new HashMap<>();
    private Map<String, S2LS> locServices = new HashMap<>();

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        LocationService getService() {
            // Return this instance of LocationService so clients can call public methods
            return LocationService.this;
        }
    }

    public LocationService() {

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
        api = new API(URI.create("https://api.perturbacoes.tny.im/v1/"), 10000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (LocationService.this.lock) {
            try {
                Network net = TopologyCache.loadNetwork(this, "pt-ml");
                for (Line l : net.getLines()) {
                    Log.d("UpdateTopologyTask", "Line: " + l.getName());
                    for (Station s : l.vertexSet()) {
                        Log.d("UpdateTopologyTask", s.toString());
                    }
                }
                Log.d("UpdateTopologyTask", "INTERCHANGES");
                for (Connection c : net.edgeSet()) {
                    if (c instanceof Transfer) {
                        Log.d("UpdateTopologyTask", " INTERCHANGE");
                        for (Station s : c.getStations()) {
                            Log.d("UpdateTopologyTask", String.format("  %s", s.toString()));
                        }
                        for (Line l : ((Transfer) c).getLines()) {
                            Log.d("UpdateTopologyTask", String.format("  %s", l.toString()));
                        }
                    }
                }
                Log.d("UpdateTopologyTask", "SHORTEST PATH");
                AStarShortestPath as = new AStarShortestPath(net);
                GraphPath gp = as.getShortestPath(net.getStation("pt-ml-ap").get(0), net.getStation("pt-ml-sp").get(0), new AStarAdmissibleHeuristic<Station>() {
                    @Override
                    public double getCostEstimate(Station sourceVertex, Station targetVertex) {
                        return 0;
                    }
                });
                List<Connection> el = gp.getEdgeList();
                for(Connection c : el) {
                    if(c instanceof Transfer) {
                        Log.d("UpdateTopologyTask", String.format("%s: Line change %s -> %s",
                                c.getSource().getName(), c.getSource().getLines().get(0).getName(),
                                c.getTarget().getLines().get(0).getName()));
                    } else {
                        Log.d("UpdateTopologyTask", String.format("%s (%s) -> %s (%s)",
                                c.getSource().getName(), c.getSource().getLines().get(0).getName(),
                                c.getTarget().getName(), c.getTarget().getLines().get(0).getName()));
                    }
                }

                putNetwork(net);

                S2LS loc = locServices.get("pt-ml");
                Log.d("UpdateTopologyTask", String.format("In network? %b", loc.inNetwork()));
                Log.d("UpdateTopologyTask", String.format("Near network? %b", loc.nearNetwork()));
                Zone z = loc.getLocation();
                for(Station s : z.vertexSet()) {
                    Log.d("UpdateTopologyTask", String.format("May be in station %s", s));
                }
            } catch (CacheException e) {
                // cache invalid, attempt to reload topology
                updateTopology();
            }
        }
        checkForTopologyUpdates();
        return Service.START_STICKY;
    }

    public void updateTopology() {
        cancelTopologyUpdate();
        currentUpdateTopologyTask = new UpdateTopologyTask();
        currentUpdateTopologyTask.execute("pt-ml");
    }

    public void cancelTopologyUpdate() {
        if (currentUpdateTopologyTask != null) {
            currentUpdateTopologyTask.cancel(true);
        }
    }

    public void checkForTopologyUpdates() {
        currentCheckTopologyUpdatesTask = new CheckTopologyUpdatesTask();
        currentCheckTopologyUpdatesTask.execute(true);
    }

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
                            for(API.WiFiAP w : s.wiFiAPs) {
                                WiFiChecker.addBSSIDforStation(station, new BSSID(w.bssid));
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
                        for(Station s : sFrom) {
                            for(Station s2 : sTo) {
                                if(s.getLines().containsAll(s2.getLines())) {
                                    from = s;
                                    to = s2;
                                }
                            }
                        }
                        if(from != null && to != null) {
                            Connection newConnection = net.addEdge(from, to);
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
                    TopologyCache.saveNetwork(LocationService.this, net);
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
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(LocationService.this);
            bm.sendBroadcast(intent);
        }

        protected void onPostExecute(Boolean result) {
            Log.d("UpdateTopologyTask", result.toString());
            currentUpdateTopologyTask = null;
            Intent intent = new Intent(ACTION_UPDATE_TOPOLOGY_FINISHED);
            intent.putExtra(EXTRA_UPDATE_TOPOLOGY_FINISHED, result);
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(LocationService.this);
            bm.sendBroadcast(intent);
        }
    }

    private CheckTopologyUpdatesTask currentCheckTopologyUpdatesTask = null;

    private class CheckTopologyUpdatesTask extends AsyncTask<Boolean, Integer, Boolean> {
        // returns true if there are updates, false if not
        private boolean autoUpdate;
        protected Boolean doInBackground(Boolean... autoUpdate) {
            this.autoUpdate = autoUpdate[0];
            try {
                synchronized (lock) {
                    for(API.DatasetInfo di : api.getDatasetInfos()) {
                        if (!networks.containsKey(di.network)) {
                            return true;
                        }
                        Network net = networks.get(di.network);
                        if(!di.version.equals(net.getDatasetVersion())) {
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
            if(result && autoUpdate) {
                updateTopology();
            }
            currentCheckTopologyUpdatesTask = null;
        }
    }

    public static final String ACTION_UPDATE_TOPOLOGY_PROGRESS = "im.tny.segvault.disturbances.action.topology.update.progress";
    public static final String EXTRA_UPDATE_TOPOLOGY_PROGRESS = "im.tny.segvault.disturbances.extra.topology.update.progress";
    public static final String ACTION_UPDATE_TOPOLOGY_FINISHED = "im.tny.segvault.disturbances.action.topology.update.finished";
    public static final String EXTRA_UPDATE_TOPOLOGY_FINISHED = "im.tny.segvault.disturbances.extra.topology.update.finished";
}
