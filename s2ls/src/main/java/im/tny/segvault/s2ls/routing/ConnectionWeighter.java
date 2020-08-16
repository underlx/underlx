package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.IEdgeWeighter;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;

/**
 * Created by gabriel on 4/27/17.
 */

public abstract class ConnectionWeighter implements IEdgeWeighter {
    @Override
    public abstract double getEdgeWeight(Network network, Connection connection);

    private Stop routeSource;
    private Stop routeTarget;

    @Override
    public void setRouteSource(Stop routeSource) {
        this.routeSource = routeSource;
    }

    @Override
    public void setRouteTarget(Stop routeTarget) {
        this.routeTarget = routeTarget;
    }

    protected boolean isSource(Stop stop) {
        return stop.equals(routeSource);
    }

    protected boolean isTarget(Stop stop) {
        return stop.equals(routeTarget);
    }
}
