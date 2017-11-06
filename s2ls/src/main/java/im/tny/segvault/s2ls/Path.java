package im.tny.segvault.s2ls;

import android.util.Log;
import android.util.Pair;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import im.tny.segvault.s2ls.routing.NeturalWeighter;
import im.tny.segvault.s2ls.routing.Route;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 6/26/17.
 */
public class Path implements GraphPath<Stop, Connection> {
    private final Network graph;

    private List<Connection> edgeList = new LinkedList<Connection>();
    private List<Pair<Date, Date>> times = new ArrayList<>();
    private List<Boolean> manualEntry = new ArrayList<>();

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

    public Path(Network graph,
                Stop startVertex,
                List<Connection> edgeList,
                List<Pair<Date, Date>> times,
                List<Boolean> manualEntry,
                double weight) {
        this.graph = graph;
        this.edgeList = edgeList;
        this.times = times;
        this.manualEntry = manualEntry;
        this.startVertex = startVertex;
        if (edgeList.size() > 0) {
            this.endVertex = edgeList.get(edgeList.size() - 1).getTarget();
        } else {
            this.endVertex = startVertex;
        }
        this.weight = weight;
        this.leftFirstStation = true;
    }

    public Path(Path copy) {
        this.graph = copy.graph;
        this.startVertex = copy.startVertex;
        this.endVertex = copy.endVertex;
        this.weight = copy.weight;
        this.edgeList = new LinkedList<>(copy.edgeList);
        this.times = new ArrayList<>(copy.times);
        this.manualEntry = new ArrayList<>(copy.manualEntry);
        this.leftFirstStation = copy.leftFirstStation;
        // this copy does not inherit the listeners
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
        List<Connection> cs = getPathBetweenAvoidingExisting(endVertex, vertex).getEdgeList();
        int size = cs.size();
        for (int i = 0; i < size; i++) {
            // never add a transfer as the last step (i.e. always assume user will keep going on
            // the same line)
            if (i == size - 1 && cs.get(i) instanceof Transfer) {
                this.endVertex = cs.get(i).getSource();
                for (OnPathChangedListener l : listeners) {
                    l.onPathChanged(this);
                }
                return;
            }

            registerEntryTime(); // also registers exit time for previous station if not yet registered
            edgeList.add(cs.get(i));
        }
        this.endVertex = vertex;
        for (OnPathChangedListener l : listeners) {
            l.onPathChanged(this);
        }
        leftFirstStation = false;
    }

    public void manualExtendStart(Stop vertex) {
        // start by reverting to the path with the start without user-made extensions
        int edgesToRemove = 0;
        while (manualEntry.get(0)) {
            manualEntry.remove(0);
            times.remove(0);
            edgesToRemove++;
        }
        for (int i = 0; i < edgesToRemove; i++) {
            edgeList.remove(0);
        }
        if (edgeList.size() > 0) {
            startVertex = edgeList.get(0).getSource();
        }

        // now go from the program-made start
        Date time = times.get(0).first;
        List<Connection> cs = getPathBetweenAvoidingExisting(vertex, startVertex).getEdgeList();
        int size = cs.size();
        int insertPos = 0;
        for (int i = 0; i < size; i++) {
            // never add a transfer as the first step
            if (i == 0 && cs.get(i) instanceof Transfer) {
                continue;
            }

            times.add(insertPos, new Pair<Date, Date>(time, time));
            manualEntry.add(insertPos, true);
            edgeList.add(insertPos++, cs.get(i));
        }
        this.startVertex = vertex;
        for (OnPathChangedListener l : listeners) {
            l.onPathChanged(this);
        }
    }

    private GraphPath<Stop, Connection> getPathBetweenAvoidingExisting(Stop source, Stop target) {
        AStarShortestPath as = new AStarShortestPath(graph);
        AStarAdmissibleHeuristic heuristic = new AStarAdmissibleHeuristic<Stop>() {
            @Override
            public double getCostEstimate(Stop sourceVertex, Stop targetVertex) {
                // let's assume users rarely go through edges they have already visited elsewhere in the trip
                // (in the same direction or not)
                for (Connection c : edgeList) {
                    if ((c.getSource() == sourceVertex && c.getTarget() == targetVertex) || (c.getSource() == targetVertex && c.getTarget() == sourceVertex)) {
                        return 10000;
                    }
                }
                return 0;
            }
        };

        return as.getShortestPath(source, target, heuristic);
    }

    public void manualExtendEnd(Stop vertex) {
        // start by reverting to the path with the end without user-made extensions
        int edgesToRemove = 0;
        while (manualEntry.get(manualEntry.size() - 1)) {
            manualEntry.remove(manualEntry.size() - 1);
            times.remove(times.size() - 1);
            edgesToRemove++;
        }
        for (int i = 0; i < edgesToRemove; i++) {
            edgeList.remove(edgeList.size() - 1);
        }
        if (edgeList.size() > 0) {
            endVertex = edgeList.get(edgeList.size() - 1).getTarget();
        }

        // now go from the program-made end
        Date time = times.get(times.size() - 1).second;
        List<Connection> cs = Route.getShortestPath(graph, endVertex, vertex, new NeturalWeighter()).getEdgeList();
        int size = cs.size();
        for (int i = 0; i < size; i++) {
            // never add a transfer as the last step (i.e. always assume user will keep going on
            // the same line)
            if (i == size - 1 && cs.get(i) instanceof Transfer) {
                this.endVertex = cs.get(i).getSource();
                return;
            }

            times.add(new Pair<Date, Date>(time, time));
            manualEntry.add(true);
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

    public Date getEntryTime(int i) {
        return times.get(i).first;
    }

    public Date getExitTime(int i) {
        return times.get(i).second;
    }

    public boolean getManualEntry(int i) {
        return manualEntry.get(i);
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
        manualEntry.add(false);
    }

    private boolean leftFirstStation = false;

    public void registerExitTime() {
        leftFirstStation = true;
        Pair<Date, Date> p = times.get(times.size() - 1);
        times.set(times.size() - 1, new Pair<>(p.first, new Date()));
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

    public Date getCurrentStopEntryTime() {
        return times.get(times.size() - 1).first;
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
        return getEdgeList().size() == 0 && !leftFirstStation;
    }

    public int getPhysicalLength() {
        int total = 0;
        for(Connection c : getEdgeList()) {
            total += c.getWorldLength();
        }
        return total;
    }

    public int getTimeablePhysicalLength() {
        int total = 0;
        List<Connection> conns = getEdgeList();
        for(int i = 0; i < conns.size(); i++) {
            if(!getManualEntry(i)) {
                total += conns.get(i).getWorldLength();
            }
        }
        return total;
    }

    public long getMovementMilliseconds() {
        List<Connection> conns = getEdgeList();
        long startTime = -1;
        long endTime = -1;
        long typTime = 0;
        for(int i = 0; i < conns.size(); i++) {
            if(!getManualEntry(i)) {
                if (startTime < 0) {
                    startTime = (long)(getExitTime(i).getTime() * 0.7 + getEntryTime(i).getTime() * 0.3);
                }
                endTime = getEntryTime(i).getTime();
                typTime += conns.get(i) instanceof Transfer ? 0 : conns.get(i).getTimes().typSeconds;
            }
        }
        if (endTime - startTime < typTime * 600) { // * 600 = * 1000 * 0.6
            // this was too fast, something strange happened
            return typTime * 600;
        }
        return endTime - startTime;
    }

    private List<OnPathChangedListener> listeners = new ArrayList<>();

    public void addPathChangedListener(OnPathChangedListener listener) {
        listeners.add(listener);
    }

    public interface OnPathChangedListener {
        void onPathChanged(Path path);
    }
}
