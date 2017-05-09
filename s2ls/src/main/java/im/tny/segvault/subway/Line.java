package im.tny.segvault.subway;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by gabriel on 4/5/17.
 */

public class Line extends Zone implements INameable, IColorable, IIDable, Comparable<Line> {
    public Line(Network network, Set<Station> stations, String id, String name, int usualCarCount) {
        super(network, stations);
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
    public boolean addVertex(Station station) {
        boolean contained = getBase().addVertex(station);
        boolean contained_sub = super.addVertex(station);
        return contained || contained_sub;
    }

    @Override
    public Connection addEdge(Station source, Station dest) {
        if (!getBase().containsEdge(source, dest)) {
            getBase().addEdge(source, dest);
        }
        return super.addEdge(source, dest);
    }

    @Override
    public String toString() {
        return String.format("Line: %s with color %s", getName(), String.format("#%06X", 0xFFFFFF & getColor()));
    }

    public Set<Station> getEndStations() {
        Set<Station> ends = new HashSet<>();
        for (Station s : vertexSet()) {
            if (outDegreeOf(s) == 1) {
                ends.add(s);
            }
        }
        return ends;
    }

    public Station getDirectionForConnection(Connection c) {
        if (!edgeSet().contains(c)) {
            return null;
        }

        Set<Station> visited = new HashSet<>();
        while (visited.add(c.getSource())) {
            Station curStation = c.getTarget();
            if (outDegreeOf(curStation) == 1) {
                // it's an end station
                return curStation;
            }
            for (Connection outedge : outgoingEdgesOf(curStation)) {
                if (!visited.contains(outedge.getTarget())) {
                    c = outedge;
                    break;
                }
            }
        }
        // circular line
        return c.getTarget();
    }

    public Station getStationAfter(Connection c) {
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
