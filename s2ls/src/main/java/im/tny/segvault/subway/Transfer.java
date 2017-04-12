package im.tny.segvault.subway;

import java.util.Collection;
import java.util.ArrayList;

/**
 * Created by gabriel on 4/5/17.
 */

public class Transfer extends Connection {
    public Collection<Line> getLines() {
        Collection<Line> l = new ArrayList<>();
        for(Station station : getStations()) {
            l.addAll(station.getLines());
        }
        return l;
    }
}
