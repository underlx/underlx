package im.tny.segvault.subway;

import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by gabriel on 4/5/17.
 */

public class Connection extends DefaultWeightedEdge {
    private boolean virtual = false;
    private Stop virtualSource;
    private Stop virtualTarget;
    public Connection() {

    }
    public Connection(boolean virtual, Stop source, Stop target) {
        this.virtual = virtual;
        if(virtual) {
            virtualSource = source;
            virtualTarget = target;
        }
    }

    public List<Stop> getStops() {
        List<Stop> s = new ArrayList<>();
        s.add(getSource());
        s.add(getTarget());
        return s;
    }

    public Stop getSource() {
        if(virtual) {
            return virtualSource;
        }
        return (Stop) super.getSource();
    }

    public Stop getTarget() {
        if(virtual) {
            return virtualTarget;
        }
        return (Stop) super.getTarget();
    }
}
