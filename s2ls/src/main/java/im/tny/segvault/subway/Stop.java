package im.tny.segvault.subway;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by gabriel on 4/5/17.
 */

public class Stop implements Comparable<Stop>, Serializable {
    private Station station;
    private Line line;

    public Stop(Station station, Line line) {
        this.station = station;
        this.line = line;
    }

    @Override
    public String toString() {
        return String.format("Stop: %s (%s)", station.getName(), line.getNames("en")[0]);
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

    public Station getStation() {
        return station;
    }

    public Line getLine() {
        return line;
    }

    @Override
    public int compareTo(final Stop o) {
        int idcomp = this.station.getId().compareTo(o.station.getId());
        if (idcomp == 0) {
            return this.line.getId().compareTo(o.line.getId());
        }
        return idcomp;
    }

    public List<Connection> getTransferEdges(Network n) {
        List<Connection> tedges = new ArrayList<>();
        for (Connection c : n.outgoingEdgesOf(this)) {
            if (c instanceof Transfer) {
                tedges.add(c);
            }
        }
        return tedges;
    }

    public boolean hasTransferEdge(Network n) {
        for (Connection c : n.outgoingEdgesOf(this)) {
            if (c instanceof Transfer) {
                return true;
            }
        }
        return false;
    }
}
