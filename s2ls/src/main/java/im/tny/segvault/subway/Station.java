package im.tny.segvault.subway;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.tny.segvault.s2ls.wifi.BSSID;

/**
 * Created by gabriel on 5/25/17.
 */

public class Station extends Zone implements INameable, IIDable, Comparable<Station> {
    public Station(Network network, Set<Stop> stops, String id, String name, List<String> altNames, Features features, Map<String, String> triviaURLs) {
        super(network, stops);
        setId(id);
        setName(name);
        setAltNames(altNames);
        setFeatures(features);
        this.lines = new ArrayList<>();
        setTriviaURLs(triviaURLs);
    }

    public Station(Network network, String id, String name, List<String> altNames, Features features, Map<String, String> triviaURLs) {
        this(network, new HashSet<Stop>(), id, name, altNames, features, triviaURLs);
    }

    private String name;

    @Override
    public String getName() {
        return name;
    }

    public String getName(int targetLength) {
        if (name.length() <= targetLength) {
            return name;
        }
        String[] parts = name.split(" de | do | da | dos | das ");
        String result = TextUtils.join(" ", parts);
        if (result.length() <= targetLength) {
            return result;
        }
        parts = result.split(" ");
        parts[0] = parts[0].charAt(0) + ".";
        return TextUtils.join(" ", parts);
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    private List<String> altNames;

    public List<String> getAltNames() {
        return altNames;
    }

    public void setAltNames(List<String> altNames) {
        this.altNames = altNames;
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
            if (s.getStation() == this) {
                stops.add(s);
            }
        }
        return stops;
    }

    public Set<Station> getImmediateNeighbors() {
        Set<Station> neighbors = new HashSet<>();
        for (Stop s : getStops()) {
            for (Connection c : getBase().outgoingEdgesOf(s)) {
                neighbors.add(c.getTarget().getStation());
            }
        }
        return neighbors;
    }

    public boolean isAlwaysClosed() {
        for (Lobby l : getLobbies()) {
            for (Lobby.Schedule s : l.getSchedules()) {
                if (s.open) {
                    return false;
                }
            }
        }
        return true;
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
        public boolean wifi;

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
        // TODO wi-fi: un-mock this and use info from server
        features.wifi = false;
        for (Stop stop : getStops()) {
            Object o = stop.getMeta("WiFiChecker");
            if (o == null || !(o instanceof List)) {
                continue;
            }
            features.wifi = ((List) o).size() > 0;
            if (features.wifi) {
                break;
            }
        }
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

    private Map<String, String> triviaURLs = new HashMap<>();

    public Map<String, String> getTriviaURLs() {
        return triviaURLs;
    }

    public String getTriviaURLforLocale(String locale) {
        return triviaURLs.get(locale);
    }

    public void setTriviaURLs(Map<String, String> triviaURLs) {
        this.triviaURLs = triviaURLs;
    }

    private Map<String, Map<String, String>> connURLs = new HashMap<>();

    public static final String CONNECTION_TYPE_BOAT = "boat";
    public static final String CONNECTION_TYPE_BUS = "bus";
    public static final String CONNECTION_TYPE_TRAIN = "train";
    public static final String CONNECTION_TYPE_PARK = "park";

    public Map<String, Map<String, String>> getConnectionURLs() {
        return connURLs;
    }

    public String getConnectionURLforLocale(String type, String locale) {
        Map<String, String> l = connURLs.get(type);
        if (l == null) {
            return null;
        }
        return l.get(locale);
    }

    public boolean hasConnectionUrl(String type) {
        return connURLs.get(type) != null;
    }

    public void setConnectionURLs(Map<String, Map<String, String>> connURLs) {
        this.connURLs = connURLs;
    }

    public float[] getWorldCoordinates() {
        // return the center of the coordinates for all the exits...
        // they are close enough together so we can treat Earth as a flat plane
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> zs = new ArrayList<>();
        for (Lobby l : getLobbies()) {
            for (Lobby.Exit exit : l.getExits()) {
                double lat = Math.toRadians((double) exit.worldCoord[0]);
                double lon = Math.toRadians((double) exit.worldCoord[1]);
                xs.add(Math.cos(lat) * Math.cos(lon));
                ys.add(Math.cos(lat) * Math.sin(lon));
                zs.add(Math.sin(lat));
            }
        }

        double avgX = 0;
        double avgY = 0;
        double avgZ = 0;
        for (int i = 0; i < xs.size(); i++) {
            avgX += xs.get(i);
            avgY += ys.get(i);
            avgZ += zs.get(i);
        }
        avgX /= xs.size();
        avgY /= xs.size();
        avgZ /= xs.size();

        double lon = Math.atan2(avgY, avgX);
        double hyp = Math.sqrt(avgX * avgX + avgY * avgY);
        double lat = Math.atan2(avgZ, hyp);
        return new float[]{(float) Math.toDegrees(lat), (float) Math.toDegrees(lon)};
    }

}