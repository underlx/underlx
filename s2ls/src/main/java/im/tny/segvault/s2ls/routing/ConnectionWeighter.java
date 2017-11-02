package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.IEdgeWeighter;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 4/27/17.
 */

public abstract class ConnectionWeighter implements IEdgeWeighter {
    @Override
    public abstract double getEdgeWeight(Network network, Connection connection);

    protected boolean isSource(Stop stop) {
        return stop.getMeta("is_route_source") != null;
    }

    protected boolean isTarget(Stop stop) {
        return stop.getMeta("is_route_target") != null;
    }
}
