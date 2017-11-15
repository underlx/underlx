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
    private static final String STOP_META_WIFICHECKER_KEY = "WiFiChecker";

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
        Object o = stop.getMeta(STOP_META_WIFICHECKER_KEY);
        if (o == null || !(o instanceof List) || stop.getStation().isAlwaysClosed()) {
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

        return new Zone(network, stops);
    }

    private List<Stop> getLocationOrdered(Network network) {
        Set<Stop> stops = new HashSet<>();
        List<Stop> orderedStops = new ArrayList<>();

        for (Stop s : network.vertexSet()) {
            if (checkBSSIDs(s)) {
                stops.add(s);
            }
        }

        // assumes currentBSSIDs are sorted by decreasing signal strength
        for (BSSID b : currentBSSIDs) {
            for (Stop s : stops) {
                Object o = s.getMeta(STOP_META_WIFICHECKER_KEY);
                if (o == null || !(o instanceof List)) {
                    continue;
                }
                List<BSSID> stationBSSID = (List<BSSID>) o;
                if (stationBSSID.contains(b)) {
                    orderedStops.add(s);
                }
            }
        }

        return orderedStops;
    }

    private List<Stop> blacklistedStations = new ArrayList<>();
    private Stop lastEntered = null;

    public void updateCurrentBSSIDs(List<BSSID> bssids) {
        boolean prevInNetwork = inNetwork(network);
        List<Stop> prevLocation = getLocationOrdered(network);
        currentBSSIDs = bssids;
        boolean curInNetwork = inNetwork(network);
        List<Stop> curLocation = getLocationOrdered(network);
        List<Stop> stationsLeft = new ArrayList<>();
        for (Stop s : prevLocation) {
            if (!curLocation.contains(s)) {
                stationsLeft.add(s);
                blacklistedStations.remove(s);
                if(lastEntered == s) {
                    lastEntered = null;
                }
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
        // only switch to a different station once its Wi-Fi signal is stronger
        // than that of the current station
        if (curLocation.size() > 0) {
            Stop s = curLocation.get(0);

            if ((lastEntered != s || !prevLocation.contains(s)) && !blacklistedStations.contains(s)) {
                listener.onEnteredStations(this, s);
                lastEntered = s;
                blacklistedStations.add(s);
            }
        }
    }

    public static void addBSSIDforStop(Stop stop, BSSID bssid) {
        List<BSSID> stationBSSID = getBSSIDsForStop(stop);
        stationBSSID.add(bssid);
        stop.putMeta(STOP_META_WIFICHECKER_KEY, stationBSSID);
    }

    public static List<BSSID> getBSSIDsForStop(Stop stop) {
        Object o = stop.getMeta(STOP_META_WIFICHECKER_KEY);
        List<BSSID> stationBSSID;
        if (o == null || !(o instanceof List)) {
            stationBSSID = new ArrayList<>();
        } else {
            stationBSSID = (List<BSSID>) o;
        }
        return stationBSSID;
    }
}
