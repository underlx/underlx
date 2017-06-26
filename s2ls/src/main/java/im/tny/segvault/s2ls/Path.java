package im.tny.segvault.s2ls;

import android.util.Pair;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 6/26/17.
 */
public class Path implements GraphPath<Stop, Connection> {
    private Network graph;

    private List<Connection> edgeList = new LinkedList<Connection>();
    private List<Pair<Date, Date>> times = new ArrayList<>();

    private Stop startVertex;

    private Stop endVertex;

    private double weight;

    public Path(Network graph,
                Stop startVertex,
                double weight) {
        this.graph = graph;
        this.startVertex = startVertex;
        this.endVertex = startVertex;
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
        for (OnPathChangedListener l : listeners) {
            l.onPathChanged(this);
        }
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
        if (times.size() > 0) {
            Pair<Date, Date> p = times.get(times.size() - 1);
            if (p.second == null) {
                times.set(times.size() - 1, new Pair<>(p.first, now));
            }
        }
        times.add(new Pair<Date, Date>(now, null));
    }

    private boolean exitedOnce = false;
    public void registerExitTime() {
        exitedOnce = true;
        Pair<Date, Date> p = times.get(times.size() - 1);
        if (p.second == null) {
            times.set(times.size() - 1, new Pair<>(p.first, new Date()));
        }
        for (OnPathChangedListener l : listeners) {
            l.onPathChanged(this);
        }
    }

    public void registerExitTime(Stop s) {
        if (!endVertex.equals(s)) {
            // this is not the "current" station
            return;
        }
        registerExitTime();
    }

    public Stop getCurrentStop() {
        return getEndVertex();
    }

    public Stop getNextStop() {
        if (getEdgeList().size() > 0) {
            Connection latest = getEdgeList().get(getEdgeList().size() - 1);
            return getCurrentStop().getLine().getStopAfter(latest);
        }
        return null;
    }

    public Stop getDirection() {
        if (getEdgeList().size() > 0) {
            Connection latest = getEdgeList().get(getEdgeList().size() - 1);
            return getCurrentStop().getLine().getDirectionForConnection(latest);
        }
        return null;
    }

    public boolean isWaitingFirstTrain() {
        return getEdgeList().size() == 0 && !exitedOnce;
    }

    private List<OnPathChangedListener> listeners = new ArrayList<>();

    public void addPathChangedListener(OnPathChangedListener listener) {
        listeners.add(listener);
    }

    public interface OnPathChangedListener {
        void onPathChanged(Path path);
    }
}
