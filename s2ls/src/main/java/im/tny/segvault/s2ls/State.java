package im.tny.segvault.s2ls;

import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 5/6/17.
 */

public abstract class State implements OnStatusChangeListener {
    private final S2LS s2ls;

    protected final S2LS getS2LS() {
        return s2ls;
    }

    protected State(S2LS s2ls) {
        this.s2ls = s2ls;
    }

    protected void setState(State state) {
        s2ls.setState(state);
    }

    public abstract boolean inNetwork();

    public abstract boolean nearNetwork();

    public abstract Zone getLocation();

    public abstract int getPreferredTickIntervalMillis();

    @Override
    public void onEnteredNetwork(IInNetworkDetector detector) {

    }

    @Override
    public void onLeftNetwork(IInNetworkDetector detector) {

    }

    public void onLeftNetworkCertain() {
        if (s2ls.detectNearNetwork()) {
            setState(new NearNetworkState(getS2LS()));
        } else {
            setState(new OffNetworkState(getS2LS()));
        }
    }

    @Override
    public void onGotNearNetwork(IProximityDetector detector) {

    }

    @Override
    public void onGotAwayFromNetwork(IProximityDetector detector) {

    }

    @Override
    public void onEnteredStations(ILocator locator, Stop... stops) {

    }

    @Override
    public void onLeftStations(ILocator locator, Stop... stops) {

    }

    public void tick() {

    }

    public void onLeaveState(State newState) {

    }
}
