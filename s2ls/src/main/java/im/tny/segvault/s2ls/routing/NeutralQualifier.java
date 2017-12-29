package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Station;

/**
 * Created by Gabriel on 29/12/2017.
 */

public class NeutralQualifier implements IAlternativeQualifier {
    @Override
    public boolean acceptable(Station station) {
        return !station.isAlwaysClosed();
    }
}
