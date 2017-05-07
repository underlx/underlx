package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.s2ls.wifi.BSSID;
import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 4/12/17.
 */

class WiFiChecker {
    private static final String STATION_META_WIFICHECKER_KEY = "WiFiChecker";

    private Map<String, WiFiLocator> locators = new HashMap<>();
    private int scanInterval = 30000;
    private WifiManager wifiMan;
    private final Handler handler = new Handler();

    public WiFiChecker(Context context, WifiManager manager) {
        wifiMan = manager;
        context.registerReceiver(mBroadcastReceiver, new IntentFilter(
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    public void setScanInterval(int scanInterval) {
        this.scanInterval = scanInterval;
    }

    private void doScan() {
        wifiMan.startScan();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doScan();
            }
        }, scanInterval);
    }

    public void stopScanning() {
        handler.removeCallbacksAndMessages(null);
    }

    public void startScanning() {
        if (wifiMan.isWifiEnabled() == false) {
            wifiMan.setWifiEnabled(true);
        }
        stopScanning();
        doScan();
    }

    // this method is for debugging only, meant to be called in an expression evaluator in a debugger
    public void updateBSSIDsDebug(String listString) {
        String[] ids = listString.split(",");
        List<BSSID> bssids = new ArrayList<>();
        for (String id : ids) {
            bssids.add(new BSSID(id));
        }
        for(WiFiLocator w : locators.values()) {
            w.updateCurrentBSSIDs(bssids);
        }
    }

    public static void addBSSIDforStation(Station station, BSSID bssid) {
        Object o = station.getMeta(STATION_META_WIFICHECKER_KEY);
        List<BSSID> stationBSSID;
        if (o == null || !(o instanceof List)) {
            stationBSSID = new ArrayList<>();
        } else {
            stationBSSID = (List<BSSID>) o;
        }
        stationBSSID.add(bssid);
        station.putMeta(STATION_META_WIFICHECKER_KEY, stationBSSID);
    }

    public void setLocatorForNetwork(Network network, WiFiLocator locator) {
        locators.put(network.getId(), locator);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            Log.d("WiFiChecker", "onReceive");
            List<ScanResult> wifiList = wifiMan.getScanResults();
            List<BSSID> bssids = new ArrayList<>();
            for (ScanResult s : wifiList) {
                bssids.add(new BSSID(s.BSSID));
                Log.d("WiFiChecker", s.BSSID);
            }
            for(WiFiLocator w : locators.values()) {
                w.updateCurrentBSSIDs(bssids);
            }
        }
    };
}
