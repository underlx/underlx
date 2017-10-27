package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 10/22/17.
 */

public class ExitStep extends Step {
    public ExitStep(Station station, Line line) {
        super(station, line);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof ExitStep;
    }
}
