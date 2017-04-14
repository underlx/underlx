package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

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

    private List<BSSID> bssids = new ArrayList<>();
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

    @Override
    public boolean checkBSSIDs(Station station) {
        List<BSSID> currentBSSIDs = getCurrentBSSIDs();
        Object o = station.getMeta(STATION_META_WIFICHECKER_KEY);
        if (o == null || !(o instanceof List)) {
            return false;
        }
        List<BSSID> stationBSSID = (List<BSSID>) o;
        return !Collections.disjoint(stationBSSID, currentBSSIDs);
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

    private List<BSSID> getCurrentBSSIDs() {
        return bssids;
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
            WiFiChecker.this.bssids = bssids;
        }
    };
}
