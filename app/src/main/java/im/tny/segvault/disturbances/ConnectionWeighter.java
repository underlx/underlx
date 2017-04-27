package im.tny.segvault.disturbances;

import java.util.List;

import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.IEdgeWeighter;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

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
        List<Line> lines = connection.getTarget().getLines();
        if(lines.size() < 1) {
            return weight;
        }
        MainService.LineStatus s = mainService.getLineStatus(lines.get(0).getId());
        if (s != null && s.down) {
            weight += 100000;
        }
        return weight;
    }
}
