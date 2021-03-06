package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 10/22/17.
 */

public abstract class Step {
    private Station station;
    private Line line;

    Step(Station station, Line line) {
        this.station = station;
        this.line = line;
    }

    public Station getStation() {
        return station;
    }

    public Line getLine() {
        return line;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) &&
                (obj instanceof Step) &&
                ((Step) obj).getStation().equals(getStation()) &&
                ((Step) obj).getLine().equals(getLine());
    }
}
