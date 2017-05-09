package im.tny.segvault.disturbances.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Line;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

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

        List<Station> previous = new ArrayList<>();
        for (StationUse use : path) {
            switch (use.getType()) {
                case INTERCHANGE:
                    Station source = null;
                    Station target = null;
                    for (Station s : network.getStation(use.getStation().getId())) {
                        for (Line l : s.getLines()) {
                            if (l.getId().equals(use.getSourceLine())) {
                                source = s;
                            }
                            if (l.getId().equals(use.getTargetLine())) {
                                target = s;
                            }
                        }
                    }
                    for (Station s : previous) {
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
                    previous = network.getStation(use.getStation().getId());
                    break;
                case NETWORK_EXIT:
                case GONE_THROUGH:
                    for (Station s : previous) {
                        for (Station s2 : network.getStation(use.getStation().getId())) {
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
