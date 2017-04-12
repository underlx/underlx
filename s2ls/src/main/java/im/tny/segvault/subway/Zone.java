package im.tny.segvault.subway;

import android.util.Log;

import org.jgrapht.graph.DirectedWeightedSubgraph;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Created by gabriel on 4/5/17.
 */

public class Zone extends DirectedWeightedSubgraph<Station, Connection> {
    public Zone(Network network, Set<Station> stations) {
        super(network, stations, new HashSet<Connection>());
    }

    public void intersect(Zone zone) {
        // retainAll throws UnsupportedOperationException if used directly on vertex sets
        // vertexSet().retainAll(zone.vertexSet());
        // we can't do this either, as it results in a ConcurrentModificationException:
        /*for(Station s : vertexSet()) {
            if(!zone.containsVertex(s)) {
                removeVertex(s);
            }
        }*/
        if(zone == this) {
            return;
        }
        LinkedList<Station> copy = new LinkedList<>();
        for (Station v : vertexSet()) {
            copy.add(v);
        }
        for(Station s : copy) {
            if(!zone.containsVertex(s)) {
                removeVertex(s);
            }
        }

    }
}
