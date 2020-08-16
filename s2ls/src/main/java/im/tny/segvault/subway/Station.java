package im.tny.segvault.subway;

import android.location.Location;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.tny.segvault.s2ls.routing.IAlternativeQualifier;

/**
 * Created by gabriel on 5/25/17.
 */

public class Station extends Zone implements IIDable, Comparable<Station> {
    public Station(Network network, Set<Stop> stops, String id, String name, List<String> altNames, List<String> tags, List<String> lowTags, Map<String, String> triviaURLs) {
        super(network, stops);
        setId(id);
        setName(name);
        setAltNames(altNames);
        setTags(tags, lowTags);
        this.lines = new ArrayList<>();
        setTriviaURLs(triviaURLs);
    }

    public Station(Network network, String id, String name, List<String> altNames, List<String> tags, List<String> lowTags, Map<String, String> triviaURLs) {
        this(network, new HashSet<Stop>(), id, name, altNames, tags, lowTags, triviaURLs);
    }

    private String name;

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
        parts = result.split("-");
        if (parts.length > 1) {
            parts[0] = parts[0].charAt(0) + ".";
        }
        result = TextUtils.join("-", parts);
        if (result.length() <= targetLength) {
            return result;
        }
        parts = result.split(" ");
        parts[0] = parts[0].charAt(0) + ".";
        return TextUtils.join(" ", parts);
    }

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

    private List<POI> pois = new ArrayList<>();

    public List<POI> getPOIs() {
        return new ArrayList<>(pois);
    }

    public void addPOI(POI poi) {
        pois.add(poi);
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

    public List<Station> getAlternatives(IAlternativeQualifier qualifier, int maxAmount) {
        Location thisLocation = new Location("");
        float[] worldCoords = getWorldCoordinates();
        thisLocation.setLatitude(worldCoords[0]);
        thisLocation.setLongitude(worldCoords[1]);

        final HashMap<Station, Double> stationCost = new HashMap<>();
        for (Station station : getNetwork().getStations()) {
            if (!qualifier.acceptable(station)) {
                continue;
            }
            double minDistance = Double.MAX_VALUE;
            for (Lobby lobby : station.getLobbies()) {
                for (Lobby.Exit exit : lobby.getExits()) {
                    Location exitLocation = new Location("");
                    exitLocation.setLatitude(exit.worldCoord[0]);
                    exitLocation.setLongitude(exit.worldCoord[1]);

                    double distance = thisLocation.distanceTo(exitLocation);
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }
            stationCost.put(station, minDistance);
        }
        List<Station> alternatives = new ArrayList<>(stationCost.keySet());
        Collections.sort(alternatives, new Comparator<Station>() {
            @Override
            public int compare(Station station, Station t1) {
                return stationCost.get(station).compareTo(stationCost.get(t1));
            }
        });
        return alternatives.subList(0, maxAmount > alternatives.size() ? alternatives.size() : maxAmount);
    }

    public boolean isAlwaysClosed() {
        for (Lobby l : getLobbies()) {
            if (!l.isAlwaysClosed()) {
                return false;
            }
        }
        return true;
    }

    public boolean isExceptionallyClosed(Network network, Date at) {
        boolean allLobiesClosed = true;
        for (Lobby l : getLobbies()) {
            if (l.isOpen(network, at)) {
                allLobiesClosed = false;
                break;
            }
        }
        return network.isOpen(at) && allLobiesClosed;
    }

    public boolean isOpen(Network network, Date at) {
        for (Lobby l : getLobbies()) {
            if (!l.isOpen(network, at)) {
                return false;
            }
        }
        return true;
    }

    public long getNextOpenTime(Network network) {
        return getNextOpenTime(network, new Date());
    }

    public long getNextOpenTime(Network network, Date curDate) {
        long earliest = Long.MAX_VALUE;
        for (Lobby l : getLobbies()) {
            if (l.getNextOpenTime(network, curDate) < earliest) {
                earliest = l.getNextOpenTime(network, curDate);
            }
        }
        return earliest;
    }

    public long getNextCloseTime(Network network) {
        return getNextCloseTime(network, new Date());
    }

    public long getNextCloseTime(Network network, Date curDate) {
        long latest = Long.MIN_VALUE;
        for (Lobby l : getLobbies()) {
            if (l.getNextCloseTime(network, curDate) > latest) {
                latest = l.getNextCloseTime(network, curDate);
            }
        }
        return latest;
    }

    @Override
    public String toString() {
        return String.format("Stop: %s", getName());
    }

    @Override
    public int compareTo(final Station o) {
        return this.getId().compareTo(o.getId());
    }

    private List<String> tags = new ArrayList<>();
    private List<String> lowTags = new ArrayList<>();

    public void setTags(List<String> tags, List<String> lowTags) {
        this.tags = tags;
        this.lowTags = lowTags;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public List<String> getLowTags() {
        return this.lowTags;
    }

    public List<String> getAllTags() {
        List<String> l = new ArrayList<>(this.tags);
        l.addAll(this.lowTags);
        return l;
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
    public static final String CONNECTION_TYPE_BIKE = "bike";

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

    private float[] worldCoords = null;

    public float[] getWorldCoordinates() {
        if (worldCoords == null) {
            worldCoords = computeWorldCoordinates();
        }
        return worldCoords;
    }

    private float[] computeWorldCoordinates() {
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