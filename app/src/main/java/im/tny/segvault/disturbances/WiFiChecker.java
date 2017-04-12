package im.tny.segvault.disturbances;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.s2ls.wifi.BSSID;
import im.tny.segvault.s2ls.wifi.IBSSIDChecker;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 4/12/17.
 */

class WiFiChecker implements IBSSIDChecker {
    private static final String STATION_META_WIFICHECKER_KEY = "WiFiChecker";
    @Override
    public boolean checkBSSIDs(Station station) {
        List<BSSID> currentBSSIDs = getCurrentBSSIDs();
        Object o = station.getMeta(STATION_META_WIFICHECKER_KEY);
        if(o == null || !(o instanceof List)) {
            return false;
        }
        List<BSSID> stationBSSID = (List<BSSID>)o;
        return !Collections.disjoint(stationBSSID, currentBSSIDs);
    }

    public static void addBSSIDforStation(Station station, BSSID bssid) {
        Object o = station.getMeta(STATION_META_WIFICHECKER_KEY);
        List<BSSID> stationBSSID;
        if(o == null || !(o instanceof List)) {
            stationBSSID = new ArrayList<>();
        } else {
            stationBSSID = (List<BSSID>)o;
        }
        stationBSSID.add(bssid);
        station.putMeta(STATION_META_WIFICHECKER_KEY, stationBSSID);
    }

    private List<BSSID> getCurrentBSSIDs() {
        return new ArrayList<>(); // TODO
    }
}
