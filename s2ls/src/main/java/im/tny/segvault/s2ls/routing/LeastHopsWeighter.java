package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 4/27/17.
 */

/**
 * LeastHopsWeighter is used when we want the shortest path in purely mathematical terms, without
 * considering real-world values. It returns the same weight for all edges.
 */
public class LeastHopsWeighter extends ConnectionWeighter {
    @Override
    public double getEdgeWeight(Network network, Connection connection) {
        return 1.0;
    }
}
