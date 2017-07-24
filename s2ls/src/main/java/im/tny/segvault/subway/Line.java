package im.tny.segvault.subway;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by gabriel on 4/5/17.
 */

public class Line extends Zone implements INameable, IColorable, IIDable, Comparable<Line> {
    public Line(Network network, Set<Stop> stops, String id, String name, int usualCarCount) {
        super(network, stops);
        setId(id);
        setName(name);
        setUsualCarCount(usualCarCount);
    }

    private int color;

    public int getColor() {
        return color;
    }

    @Override
    public void setColor(int color) {
        this.color = color;
    }

    private String name;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    private String id;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean addVertex(Stop stop) {
        boolean contained = getBase().addVertex(stop);
        boolean contained_sub = super.addVertex(stop);
        return contained || contained_sub;
    }

    @Override
    public Connection addEdge(Stop source, Stop dest) {
        if (!getBase().containsEdge(source, dest)) {
            getBase().addEdge(source, dest);
        }
        return super.addEdge(source, dest);
    }

    @Override
    public String toString() {
        return String.format("Line: %s with color %s", getName(), String.format("#%06X", 0xFFFFFF & getColor()));
    }

    public Set<Stop> getEndStops() {
        Set<Stop> ends = new HashSet<>();
        for (Stop s : vertexSet()) {
            if (outDegreeOf(s) == 1) {
                ends.add(s);
            }
        }
        return ends;
    }

    public Stop getDirectionForConnection(Connection c) {
        if (!edgeSet().contains(c)) {
            return null;
        }

        Set<Stop> visited = new HashSet<>();
        while (visited.add(c.getSource())) {
            Stop curStop = c.getTarget();
            if (outDegreeOf(curStop) == 1) {
                // it's an end station
                return curStop;
            }
            for (Connection outedge : outgoingEdgesOf(curStop)) {
                if (!visited.contains(outedge.getTarget())) {
                    c = outedge;
                    break;
                }
            }
        }
        // circular line
        return c.getTarget();
    }

    public Stop getStopAfter(Connection c) {
        if (!edgeSet().contains(c)) {
            return null;
        }

        for (Connection outedge : outgoingEdgesOf(c.getTarget())) {
            if (outedge.getTarget() != c.getSource()) {
                return outedge.getTarget();
            }
        }
        return null;
    }

    private Stop firstStop;

    public Stop getFirstStop() {
        return firstStop;
    }

    public void setFirstStop(Stop stop) {
        firstStop = stop;
    }

    private int usualCarCount;

    public int getUsualCarCount() {
        return usualCarCount;
    }

    public void setUsualCarCount(int usualCarCount) {
        this.usualCarCount = usualCarCount;
    }

    @Override
    public int compareTo(final Line o) {
        return this.getId().compareTo(o.getId());
    }
}
