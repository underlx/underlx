package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 10/22/17.
 */

public class EnterStep extends Step {
    private Station direction;

    EnterStep(Station station, Line line, Station direction) {
        super(station, line);
        this.direction = direction;
    }

    public Station getDirection() {
        return direction;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) &&
                (obj instanceof EnterStep) &&
                ((EnterStep) obj).getDirection().equals(getDirection());
    }
}
