package im.tny.segvault.disturbances.db;

import android.arch.persistence.room.Embedded;
import android.arch.persistence.room.Relation;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;

/**
 * Created by gabriel on 7/9/17.
 */

public class Trip {
    @Embedded
    public TripEntity trip;
    @Relation(parentColumn = "trip.id", entityColumn = "tripId", entity = StationUse.class)
    public List<StationUse> path;

    public List<Connection> toConnectionPath(Network network) {
        List<Connection> edges = new LinkedList<>();

        List<Stop> previous = new ArrayList<>();
        for (StationUse use : path) {
            switch (use.getType()) {
                case INTERCHANGE:
                    Stop source = null;
                    Stop target = null;
                    for (Stop s : network.getStation(use.getStationId()).getStops()) {
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
                    previous = new ArrayList<>(network.getStation(use.getStationId()).getStops());
                    break;
                case NETWORK_EXIT:
                case GONE_THROUGH:
                    for (Stop s : previous) {
                        for (Stop s2 : network.getStation(use.getStationId()).getStops()) {
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