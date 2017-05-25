package im.tny.segvault.disturbances.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Line;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by gabriel on 5/8/17.
 */

public class Trip extends RealmObject {
    @PrimaryKey
    private String id;

    private RealmList<StationUse> path;

    private boolean synced;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RealmList<StationUse> getPath() {
        return path;
    }

    public void setPath(RealmList<StationUse> path) {
        this.path = path;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public List<Connection> toConnectionPath(Network network) {
        List<Connection> edges = new LinkedList<>();

        List<Stop> previous = new ArrayList<>();
        for (StationUse use : path) {
            switch (use.getType()) {
                case INTERCHANGE:
                    Stop source = null;
                    Stop target = null;
                    for (Stop s : network.getStation(use.getStation().getId()).getStops()) {
                        if (s.getLine().getId().equals(use.getSourceLine())) {
                            source = s;
                        }
                        if (s.getLine().getId().equals(use.getTargetLine())) {
                            target = s;
                        }
                    }
                    for (Stop s : previous) {
                        Connection e = network.getEdge(s, source);
                        if (e != null) {
                            edges.add(e);
                        }
                    }
                    Connection edge = network.getEdge(source, target);
                    if (edge != null) {
                        edges.add(edge);
                        previous = new ArrayList<>();
                        previous.add(target);
                    }
                    break;
                case NETWORK_ENTRY:
                    previous = new ArrayList<>(network.getStation(use.getStation().getId()).getStops());
                    break;
                case NETWORK_EXIT:
                case GONE_THROUGH:
                    for (Stop s : previous) {
                        for (Stop s2 : network.getStation(use.getStation().getId()).getStops()) {
                            Connection e = network.getEdge(s, s2);
                            if (e != null) {
                                edges.add(e);
                                previous = new ArrayList<>();
                                previous.add(s2);
                            }
                        }
                    }
                    break;
            }
        }
        return edges;
    }
}
