package im.tny.segvault.s2ls.wifi;

import java.util.HashSet;
import java.util.Set;

import im.tny.segvault.s2ls.IInNetworkDetector;
import im.tny.segvault.s2ls.ILocator;
import im.tny.segvault.s2ls.IProximityDetector;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 4/5/17.
 */

public class WiFiLocator implements IInNetworkDetector, IProximityDetector, ILocator {

    private IBSSIDChecker checker;

    public WiFiLocator(IBSSIDChecker checker) {
        this.checker = checker;
    }

    @Override
    public void initialize(Network network) {

    }

    @Override
    public boolean inNetwork(Network network) {
        for (Station s : network.vertexSet()) {
            if (checker.checkBSSIDs(s)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean nearNetwork(Network network) {
        return inNetwork(network);
    }

    @Override
    public Zone getLocation(Network network) {
        Set<Station> stations = new HashSet<>();

        for (Station s : network.vertexSet()) {
            if (checker.checkBSSIDs(s)) {
                stations.add(s);
            }
        }

        return new Zone(network, stations);
    }
}
