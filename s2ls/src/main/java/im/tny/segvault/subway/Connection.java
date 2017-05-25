package im.tny.segvault.subway;

import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by gabriel on 4/5/17.
 */

public class Connection extends DefaultWeightedEdge {
    public List<Stop> getStops() {
        List<Stop> s = new ArrayList<>();
        s.add((Stop) getSource());
        s.add((Stop) getTarget());
        return s;
    }

    public Stop getSource() {
        return (Stop) super.getSource();
    }

    public Stop getTarget() {
        return (Stop) super.getTarget();
    }
}
