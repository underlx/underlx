package im.tny.segvault.s2ls;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

/**
 * Created by gabriel on 5/6/17.
 */

public interface OnStatusChangeListener {
    void onEnteredNetwork(IInNetworkDetector detector);
    void onLeftNetwork(IInNetworkDetector detector);
    void onGotNearNetwork(IProximityDetector detector);
    void onGotAwayFromNetwork(IProximityDetector detector);
    void onEnteredStations(ILocator locator, Station... stations);
    void onLeftStations(ILocator locator, Station... stations);
}
