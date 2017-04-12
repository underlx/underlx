package im.tny.segvault.subway;

import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by gabriel on 4/5/17.
 */

public class Connection extends DefaultWeightedEdge {
    public List<Station> getStations() {
        List<Station> s = new ArrayList<>();
        s.add((Station) getSource());
        s.add((Station) getTarget());
        return s;
    }

    public Station getSource() {
        return (Station) super.getSource();
    }

    public Station getTarget() {
        return (Station) super.getTarget();
    }
}
