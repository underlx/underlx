package im.tny.segvault.s2ls;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Transfer;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 5/6/17.
 */

public class InNetworkState extends State {
    private final static int TICKS_UNTIL_LEFT = 6; // assuming a tick occurs more or less every 30 seconds
    private Zone current = new Zone(getS2LS().getNetwork(), new HashSet<Station>());

    private Path path = null;

    public Path getPath() {
        return path;
    }

    protected InNetworkState(S2LS s2ls) {
        super(s2ls);
    }

    @Override
    public S2LS.StateType getType() {
        return S2LS.StateType.IN_NETWORK;
    }

    @Override
    public boolean inNetwork() {
        return true;
    }

    @Override
    public boolean nearNetwork() {
        return true;
    }

    @Override
    public Zone getLocation() {
        return current;
    }

    @Override
    public int getPreferredTickIntervalMillis() {
        return 30000;
    }

    private boolean mightHaveLeft = false;
    private int ticksSinceLeft = 0;

    @Override
    public void onLeftNetwork(IInNetworkDetector detector) {
        if (detector instanceof WiFiLocator) {
            ticksSinceLeft = 0;
            mightHaveLeft = true;
        } else {
            if (getS2LS().detectNearNetwork()) {
                setState(new NearNetworkState(getS2LS()));
            } else {
                setState(new OffNetworkState(getS2LS()));
            }
        }
    }

    @Override
    public void onEnteredStations(ILocator locator, Station... stations) {
        for (Station station : stations) {
            current.addVertex(station);
        }
        mightHaveLeft = false;
        if (path == null) {
            path = new Path(getS2LS().getNetwork(), stations[0], stations[stations.length - 1], new LinkedList<Connection>(), 0);
        } else {
            path.setEndVertex(stations[stations.length - 1], false);
        }
        for (Station station : stations) {
            path.setEnterTime(station, new Date());
        }
        for (OnLocationChangedListener l : listeners) {
            l.onLocationChanged(this);
        }
    }

    @Override
    public void onLeftStations(ILocator locator, Station... stations) {
        for (Station station : stations) {
            current.removeVertex(station);
            if(path != null) {
                path.setLeaveTime(station, new Date());
                for (Connection c : getS2LS().getNetwork().outgoingEdgesOf(station)) {
                    if (c instanceof Transfer && current.containsVertex(c.getTarget())) {
                        path.setEndVertex(c.getTarget(), true);
                        break;
                    }
                }
            }
        }
        for (OnLocationChangedListener l : listeners) {
            l.onLocationChanged(this);
        }
    }

    @Override
    public void tick() {
        if (mightHaveLeft) {
            ticksSinceLeft++;
            if (ticksSinceLeft >= TICKS_UNTIL_LEFT && !getS2LS().detectInNetwork()) {
                if (getS2LS().detectNearNetwork()) {
                    setState(new NearNetworkState(getS2LS()));
                } else {
                    setState(new OffNetworkState(getS2LS()));
                }
            }
        }
    }

    private List<OnLocationChangedListener> listeners = new ArrayList<>();

    public void addLocationChangedListener(OnLocationChangedListener listener) {
        listeners.add(listener);
    }

    public interface OnLocationChangedListener {
        void onLocationChanged(InNetworkState state);
    }

    public class Path implements GraphPath<Station, Connection> {

        private Network graph;

        private List<Connection> edgeList;

        private Map<Station, Date> enterTimes = new HashMap<>();
        private Map<Station, Date> leaveTimes = new HashMap<>();

        private Station startVertex;

        private Station endVertex;

        private double weight;

        public Path(
                Network graph,
                Station startVertex,
                Station endVertex,
                List<Connection> edgeList,
                double weight) {
            this.graph = graph;
            this.startVertex = startVertex;
            this.endVertex = endVertex;
            this.edgeList = edgeList;
            this.weight = weight;
        }

        @Override
        public Graph<Station, Connection> getGraph() {
            return graph;
        }

        @Override
        public Station getStartVertex() {
            return startVertex;
        }

        @Override
        public Station getEndVertex() {
            return endVertex;
        }

        public void setEndVertex(Station vertex, boolean addLastTransfer) {
            List<Connection> cs = graph.getAnyPathBetween(endVertex, vertex).getEdgeList();
            int size = cs.size();
            for (int i = 0; i < size; i++) {
                // if unsure between two stations (e.g. pt-ml-cg double-station on pt-ml network)
                // then do not put a transfer as the last step (i.e. assume user will keep going on
                // the same line)
                if (i == size - 1 && cs.get(i) instanceof Transfer && !addLastTransfer) {
                    Transfer t = (Transfer) cs.get(i);
                    if (current.containsVertex(t.getSource()) && current.containsVertex(t.getTarget())) {
                        this.endVertex = t.getSource();
                        return;
                    }
                }
                if (i > 0) {
                    if(getEnterTime(cs.get(i).getSource()) == null) {
                        setEnterTime(cs.get(i).getSource(), new Date());
                    }
                    if(getLeaveTime(cs.get(i).getSource()) == null) {
                        setLeaveTime(cs.get(i).getSource(), new Date());
                    }
                }
                edgeList.add(cs.get(i));
            }
            this.endVertex = vertex;
        }

        @Override
        public List<Connection> getEdgeList() {
            return edgeList;
        }

        @Override
        public double getWeight() {
            return weight;
        }

        public Date getEnterTime(Station s) {
            return enterTimes.get(s);
        }
        public void setEnterTime(Station s, Date d) {
            enterTimes.put(s, d);
        }
        public Date getLeaveTime(Station s) {
            return leaveTimes.get(s);
        }
        public void setLeaveTime(Station s, Date d) {
            leaveTimes.put(s, d);
        }
    }
}
