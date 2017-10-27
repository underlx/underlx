package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 10/22/17.
 */

public class ChangeLineStep extends Step {
    private Line target;
    private Station direction;

    public ChangeLineStep(Station station, Line origin, Line target, Station direction) {
        super(station, origin);
        this.target = target;
        this.direction = direction;
    }

    public Line getTarget() {
        return target;
    }

    public Station getDirection() {
        return direction;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) &&
                (obj instanceof ChangeLineStep) &&
                ((ChangeLineStep) obj).getTarget().equals(getTarget()) &&
                ((ChangeLineStep) obj).getDirection().equals(getDirection());
    }
}
