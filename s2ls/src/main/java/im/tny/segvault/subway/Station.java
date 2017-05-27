package im.tny.segvault.subway;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by gabriel on 5/25/17.
 */

public class Station extends Zone implements INameable, IIDable, Comparable<Station> {
    public Station(Network network, Set<Stop> stops, String id, String name, Features features) {
        super(network, stops);
        setId(id);
        setName(name);
        setFeatures(features);
        this.lines = new ArrayList<>();
    }

    public Station(Network network, String id, String name, Features features) {
        this(network, new HashSet<Stop>(), id, name, features);
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

    private List<Line> lines = null;

    public List<Line> getLines() {
        return lines;
    }

    @Override
    public boolean addVertex(Stop stop) {
        boolean contained = getBase().addVertex(stop);
        boolean contained_sub = super.addVertex(stop);
        if (!lines.contains(stop.getLine())) {
            lines.add(stop.getLine());
        }
        return contained || contained_sub;
    }

    @Override
    public Connection addEdge(Stop source, Stop dest) {
        if (!getBase().containsEdge(source, dest)) {
            getBase().addEdge(source, dest);
        }
        return super.addEdge(source, dest);
    }

    public Stop getDirectionForConnection(Connection c) {
        for (Line l : getLines()) {
            Stop d = l.getDirectionForConnection(c);
            if (d != null) return d;
        }
        return null;
    }

    public Stop getStationAfter(Connection c) {
        for (Line l : getLines()) {
            Stop d = l.getStopAfter(c);
            if (d != null) return d;
        }
        return null;
    }

    public Set<Stop> getStops() {
        Set<Stop> stops = new HashSet<>();
        for (Stop s : getBase().vertexSet()) {
            if(s.getStation() == this) {
                stops.add(s);
            }
        }
        return stops;
    }

    @Override
    public String toString() {
        return String.format("Stop: %s", getName());
    }

    @Override
    public int compareTo(final Station o) {
        return this.getId().compareTo(o.getId());
    }

    static public class Features implements Serializable {
        public boolean lift;
        public boolean bus;
        public boolean boat;
        public boolean train;
        public boolean airport;

        public Features(boolean lift, boolean bus, boolean boat, boolean train, boolean airport) {
            this.lift = lift;
            this.bus = bus;
            this.boat = boat;
            this.train = train;
            this.airport = airport;
        }
    }

    private Features features;

    public Features getFeatures() {
        return features;
    }

    public void setFeatures(Features features) {
        this.features = features;
    }

    private List<Lobby> lobbies = new ArrayList<>();

    public List<Lobby> getLobbies() {
        return lobbies;
    }

    public void addLobby(Lobby lobby) {
        this.lobbies.add(lobby);
    }

}