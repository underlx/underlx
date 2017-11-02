package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 4/27/17.
 */

public class NeturalWeighter extends ConnectionWeighter {
    @Override
    public double getEdgeWeight(Network network, Connection connection) {
        return network.getDefaultEdgeWeight(connection);
    }
}
