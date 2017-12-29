package im.tny.segvault.subway;

import org.jgrapht.graph.DirectedWeightedSubgraph;

import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

/**
 * Created by gabriel on 4/5/17.
 */

public class Zone extends DirectedWeightedSubgraph<Stop, Connection> {
    public Zone(Network network, Set<Stop> stops) {
        super(network, stops, new HashSet<Connection>());
    }

    public Network getNetwork() {
        return (Network)getBase();
    }

    public void intersect(Zone zone) {
        // retainAll throws UnsupportedOperationException if used directly on vertex sets
        // vertexSet().retainAll(zone.vertexSet());
        // we can't do this either, as it results in a ConcurrentModificationException:
        /*for(Stop s : vertexSet()) {
            if(!zone.containsVertex(s)) {
                removeVertex(s);
            }
        }*/
        if(zone == this) {
            return;
        }
        LinkedList<Stop> copy = new LinkedList<>();
        for (Stop v : vertexSet()) {
            copy.add(v);
        }
        for(Stop s : copy) {
            if(!zone.containsVertex(s)) {
                removeVertex(s);
            }
        }

    }
}
