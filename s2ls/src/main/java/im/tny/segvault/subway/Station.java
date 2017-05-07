package im.tny.segvault.subway;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Created by gabriel on 4/5/17.
 */

public class Station implements INameable, IIDable, Comparable<Station>, Serializable {
    private int rand;

    public Station(String id, String name, Features features) {
        setId(id);
        setName(name);
        setFeatures(features);
        this.lines = new ArrayList<>();
        this.rand = new Random().nextInt(10000);
    }

    private List<Line> lines = null;

    public List<Line> getLines() {
        return lines;
    }

    public boolean addLine(Line line) {
        return lines.add(line);
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
    public String toString() {
        return String.format("Station: %s %d", getName(), rand);
    }

    private Map<String, Object> metaMap = new HashMap<>();

    public Object getMeta(String key) {
        return metaMap.get(key);
    }

    public Object putMeta(String key, Object object) {
        if (object == null) {
            return metaMap.remove(key);
        }
        return metaMap.put(key, object);
    }

    @Override
    public int compareTo(final Station o) {
        int idcomp = this.getId().compareTo(o.getId());
        if (idcomp == 0) {
            return this.getName().compareTo(o.getName());
        }
        return idcomp;
    }

    public boolean hasTransferEdge(Network n) {
        for (Connection c : n.outgoingEdgesOf(this)) {
            if (c instanceof Transfer) {
                return true;
            }
        }
        return false;
    }

    public Station getDirectionForConnection(Connection c) {
        for (Line l : getLines()) {
            Station d = l.getDirectionForConnection(c);
            if (d != null) return d;
        }
        return null;
    }

    public Station getStationAfter(Connection c) {
        for (Line l : getLines()) {
            Station d = l.getStationAfter(c);
            if (d != null) return d;
        }
        return null;
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


}
