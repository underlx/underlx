package im.tny.segvault.disturbances;

import java.util.List;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.IEdgeWeighter;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 4/27/17.
 */

public class ConnectionWeighter implements IEdgeWeighter {
    private MainService mainService;

    public ConnectionWeighter(MainService mainService) {
        this.mainService = mainService;
    }

    @Override
    public double getEdgeWeight(Network network, Connection connection) {
        double weight = network.getDefaultEdgeWeight(connection);
        // make transferring to lines with disturbances much "heavier"
        List<Line> lines = connection.getTarget().getLines();
        if (lines.size() < 1) {
            return weight;
        }
        MainService.LineStatus s = mainService.getLineStatus(lines.get(0).getId());
        if (connection instanceof Transfer || isSource(connection.getSource())) {
            if (!isTarget(connection.getTarget()) && s != null && s.down) {
                // TODO adjust this according to disturbance severity, if possible
                weight += 100000;
            }
        }
        return weight;
    }

    private boolean isSource(Station station) {
        return station.getMeta("is_route_source") != null;
    }

    private boolean isTarget(Station station) {
        return station.getMeta("is_route_target") != null;
    }
}
