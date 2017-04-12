package im.tny.segvault.s2ls;

import java.util.ArrayList;
import java.util.Collection;

import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 4/5/17.
 */

public class S2LS {
    private Network network;
    private Collection<ILocator> locators = new ArrayList<>();
    private Collection<IInNetworkDetector> networkDetectors = new ArrayList<>();
    private Collection<IProximityDetector> proximityDetectors = new ArrayList<>();

    public S2LS(Network network) {
        this.network = network;
    }

    public void addLocator(ILocator locator) {
        locator.initialize(network);
        locators.add(locator);
    }

    public void addNetworkDetector(IInNetworkDetector detector) {
        detector.initialize(network);
        networkDetectors.add(detector);
    }

    public void addProximityDetector(IProximityDetector detector) {
        detector.initialize(network);
        proximityDetectors.add(detector);
    }

    // TODO consider state machine instead (or in addition): OUT, NEAR, IN (+ IN_TRAIN, IN_STATION...)
    public boolean inNetwork() {
        for(IInNetworkDetector d : networkDetectors) {
            if(d.inNetwork(network)) {
                return true;
            }
        }
        return false;
    }

    public boolean nearNetwork() {
        for(IProximityDetector d : proximityDetectors) {
            if(d.nearNetwork(network)) {
                return true;
            }
        }
        return false;
    }

    public Zone getLocation() {
        Zone zone = new Zone(network, network.vertexSet());
        for(ILocator l : locators) {
            zone.intersect(l.getLocation(network));
        }
        return zone;
    }
}
