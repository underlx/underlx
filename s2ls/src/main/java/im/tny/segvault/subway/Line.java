package im.tny.segvault.subway;

import org.jgrapht.Graph;
import org.jgrapht.graph.Subgraph;

import android.graphics.Color;

import java.io.Serializable;
import java.util.Set;

/**
 * Created by gabriel on 4/5/17.
 */

public class Line extends Zone implements INameable, IColorable, IIDable, Comparable<Line> {
    public Line(Network network, Set<Station> stations, String id, String name) {
        super(network, stations);
        setId(id);
        setName(name);
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
        if(!getBase().containsEdge(source, dest)) {
            getBase().addEdge(source, dest);
        }
        return super.addEdge(source, dest);
    }

    @Override
    public String toString() {
        return String.format("Line: %s with color %s", getName(), String.format("#%06X", 0xFFFFFF & getColor()));
    }

    @Override
    public int compareTo(final Line o) {
        return this.getId().compareTo(o.getId());
    }
}
