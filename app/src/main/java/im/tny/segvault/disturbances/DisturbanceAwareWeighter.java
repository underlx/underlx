package im.tny.segvault.disturbances;

import java.util.Date;

import im.tny.segvault.s2ls.routing.NeutralWeighter;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 4/27/17.
 */

public class DisturbanceAwareWeighter extends NeutralWeighter {
    private MainService mainService;


    public DisturbanceAwareWeighter(MainService mainService) {
        this.mainService = mainService;
    }

    @Override
    public double getEdgeWeight(Network network, Connection connection) {
        double weight = super.getEdgeWeight(network, connection);
        // make transferring to lines with disturbances much "heavier"
        LineStatusCache.Status s = mainService.getLineStatusCache().getLineStatus(connection.getTarget().getLine().getId());
        if (connection instanceof Transfer || isSource(connection.getSource())) {
            if (!isTarget(connection.getTarget()) && s != null &&
                    (s.down || connection.getTarget().getLine().isExceptionallyClosed(new Date()))) {
                // TODO adjust this according to disturbance severity, if possible
                weight += 100000;
                if(connection.getTarget().getLine().isExceptionallyClosed(new Date())) {
                    weight += 100000;
                }
            }
        }
        return weight;
    }
}
