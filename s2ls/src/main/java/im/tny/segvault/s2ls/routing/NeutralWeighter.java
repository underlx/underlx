package im.tny.segvault.s2ls.routing;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;

/**
 * Created by gabriel on 4/27/17.
 */

public class NeutralWeighter extends ConnectionWeighter {
    @Override
    public double getEdgeWeight(Network network, Connection connection) {
        Connection.Times times = connection.getTimes();
        double weight = times.typSeconds;
        if(isSource(connection.getSource())) {
            weight += times.typWaitingSeconds;
        } else {
            weight += times.typStopSeconds;
        }
        return weight;
    }
}
