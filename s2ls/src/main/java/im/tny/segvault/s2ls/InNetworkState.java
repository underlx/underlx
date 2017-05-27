package im.tny.segvault.s2ls;

import android.util.Pair;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 5/6/17.
 */

public class InNetworkState extends State {
    private final static int TICKS_UNTIL_LEFT = 6; // assuming a tick occurs more or less every 30 seconds
    private Zone current = new Zone(getS2LS().getNetwork(), new HashSet<Stop>());

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
            if (path != null) {
                path.registerExitTime();
            }
        } else {
            if (getS2LS().detectNearNetwork()) {
                setState(new NearNetworkState(getS2LS()));
            } else {
                setState(new OffNetworkState(getS2LS()));
            }
        }
    }

    @Override
    public void onEnteredStations(ILocator locator, Stop... stops) {
        for (Stop stop : stops) {
            current.addVertex(stop);
        }
        mightHaveLeft = false;
        if (path == null) {
            path = new Path(getS2LS().getNetwork(), stops[0], stops[stops.length - 1], 0);
        } else {
            path.setEndVertex(stops[stops.length - 1]);
        }
        for (OnLocationChangedListener l : listeners) {
            l.onLocationChanged(this);
        }
    }

    @Override
    public void onLeftStations(ILocator locator, Stop... stops) {
        for (Stop stop : stops) {
            current.removeVertex(stop);
            if (path != null) {
                path.registerExitTime(stop);
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

    public class Path implements GraphPath<Stop, Connection> {

        private Network graph;

        private List<Connection> edgeList = new LinkedList<Connection>();
        private List<Pair<Date, Date>> times = new ArrayList<>();

        private Stop startVertex;

        private Stop endVertex;

        private double weight;

        public Path(
                Network graph,
                Stop startVertex,
                Stop endVertex,
                double weight) {
            this.graph = graph;
            this.startVertex = startVertex;
            this.endVertex = endVertex;
            this.weight = weight;
            registerEntryTime();
        }

        @Override
        public Graph<Stop, Connection> getGraph() {
            return graph;
        }

        @Override
        public Stop getStartVertex() {
            return startVertex;
        }

        @Override
        public Stop getEndVertex() {
            return endVertex;
        }

        public void setEndVertex(Stop vertex) {
            List<Connection> cs = graph.getAnyPathBetween(endVertex, vertex).getEdgeList();
            int size = cs.size();
            for (int i = 0; i < size; i++) {
                // never add a transfer as the last step (i.e. always assume user will keep going on
                // the same line)
                if (i == size - 1 && cs.get(i) instanceof Transfer) {
                    Transfer t = (Transfer) cs.get(i);
                    this.endVertex = t.getSource();
                    return;
                }

                registerEntryTime(); // also registers exit time for previous station if not yet registered
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

        public Pair<Date, Date> getEntryExitTimes(int i) {
            return times.get(i);
        }

        private void registerEntryTime() {
            Date now = new Date();
            if(times.size() > 0) {
                Pair<Date, Date> p = times.get(times.size() - 1);
                if (p.second == null) {
                    times.set(times.size() - 1, new Pair<>(p.first, now));
                }
            }
            times.add(new Pair<Date, Date>(now, null));
        }

        public void registerExitTime() {
            Pair<Date, Date> p = times.get(times.size() - 1);
            if (p.second == null) {
                times.set(times.size() - 1, new Pair<>(p.first, new Date()));
            }
        }

        public void registerExitTime(Stop s) {
            if(!endVertex.equals(s)) {
                // this is not the "current" station
                return;
            }
            registerExitTime();
        }
    }
}
