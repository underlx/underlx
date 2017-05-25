package im.tny.segvault.s2ls;

import im.tny.segvault.subway.Stop;

/**
 * Created by gabriel on 5/6/17.
 */

public interface OnStatusChangeListener {
    void onEnteredNetwork(IInNetworkDetector detector);
    void onLeftNetwork(IInNetworkDetector detector);
    void onGotNearNetwork(IProximityDetector detector);
    void onGotAwayFromNetwork(IProximityDetector detector);
    void onEnteredStations(ILocator locator, Stop... stops);
    void onLeftStations(ILocator locator, Stop... stops);
}
