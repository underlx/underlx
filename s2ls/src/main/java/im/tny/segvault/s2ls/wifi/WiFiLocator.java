package im.tny.segvault.s2ls.wifi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.tny.segvault.s2ls.IInNetworkDetector;
import im.tny.segvault.s2ls.ILocator;
import im.tny.segvault.s2ls.IProximityDetector;
import im.tny.segvault.s2ls.OnStatusChangeListener;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 4/5/17.
 */

public class WiFiLocator implements IInNetworkDetector, IProximityDetector, ILocator {
    // TODO many of the results of methods in this class can be cached and only invalidated on updateCurrentBSSIDs
    private static final String STATION_META_WIFICHECKER_KEY = "WiFiChecker";

    private List<BSSID> currentBSSIDs = new ArrayList<>();

    private OnStatusChangeListener listener;
    private Network network;

    public WiFiLocator(Network network) {
        this.network = network;
    }

    @Override
    public void setListener(OnStatusChangeListener listener) {
        this.listener = listener;
    }

    private boolean checkBSSIDs(Stop stop) {
        Object o = stop.getMeta(STATION_META_WIFICHECKER_KEY);
        if (o == null || !(o instanceof List)) {
            return false;
        }
        List<BSSID> stationBSSID = (List<BSSID>) o;
        return !Collections.disjoint(stationBSSID, currentBSSIDs);
    }

    @Override
    public boolean inNetwork(Network network) {
        for (Stop s : network.vertexSet()) {
            if (checkBSSIDs(s)) {
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
        Set<Stop> stops = new HashSet<>();

        for (Stop s : network.vertexSet()) {
            if (checkBSSIDs(s)) {
                stops.add(s);
            }
        }

        if (stops.size() > 1) {
            // assumes currentBSSIDs are sorted by decreasing signal strength
            for (BSSID b : currentBSSIDs) {
                for (Stop s : stops) {
                    Object o = s.getMeta(STATION_META_WIFICHECKER_KEY);
                    if (o == null || !(o instanceof List)) {
                        continue;
                    }
                    List<BSSID> stationBSSID = (List<BSSID>) o;
                    if (stationBSSID.contains(b)) {
                        stops.clear();
                        stops.add(s);
                        return new Zone(network, stops);
                    }
                }
            }
        }

        return new Zone(network, stops);
    }

    public void updateCurrentBSSIDs(List<BSSID> bssids) {
        boolean prevInNetwork = inNetwork(network);
        Zone prevLocation = getLocation(network);
        currentBSSIDs = bssids;
        boolean curInNetwork = inNetwork(network);
        Zone curLocation = getLocation(network);
        List<Stop> stationsLeft = new ArrayList<>();
        for (Stop s : prevLocation.vertexSet()) {
            if (!curLocation.containsVertex(s)) {
                stationsLeft.add(s);
            }
        }
        if (stationsLeft.size() > 0) {
            Stop[] stationsLeftArray = new Stop[stationsLeft.size()];
            listener.onLeftStations(this, stationsLeft.toArray(stationsLeftArray));
        }
        if (curInNetwork != prevInNetwork) {
            if (curInNetwork) {
                listener.onEnteredNetwork(this);
            } else {
                listener.onLeftNetwork(this);
            }
        }
        List<Stop> stationsEntered = new ArrayList<>();
        for (Stop s : curLocation.vertexSet()) {
            if (!prevLocation.containsVertex(s)) {
                stationsEntered.add(s);
            }
        }
        if (stationsEntered.size() > 0) {
            Stop[] stationsEnteredArray = new Stop[stationsEntered.size()];
            listener.onEnteredStations(this, stationsEntered.toArray(stationsEnteredArray));
        }
    }
}
