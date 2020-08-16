package im.tny.segvault.disturbances;

import android.content.Context;

import java.util.Date;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 4/27/17.
 */

public class DisturbanceAwareWeighter extends ETAWeighter {
    private Context context;


    public DisturbanceAwareWeighter(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public double getEdgeWeight(Network network, Connection connection) {
        double weight = super.getEdgeWeight(network, connection);

        LineStatusCache.Status s = Coordinator.get(context).getLineStatusCache().getLineStatus(connection.getTarget().getLine().getId());
        if (connection instanceof Transfer || isSource(connection.getSource())) {
            if (!isTarget(connection.getTarget()) && s != null
                    // make transferring to closed lines or lines with disturbances much "heavier"
                    && (s.down ||
                    connection.getTarget().getLine().isExceptionallyClosed(new Date()) ||
                    // make transferring through closed stations much "heavier"
                    connection.getTarget().getStation().isAlwaysClosed() ||
                    connection.getTarget().getStation().isExceptionallyClosed(network, new Date()))
            ) {
                // TODO adjust this according to disturbance severity, if possible
                weight += 100000;
                if (connection.getTarget().getLine().isExceptionallyClosed(new Date())) {
                    weight += 100000;
                }
            }
        }
        return weight;
    }
}
