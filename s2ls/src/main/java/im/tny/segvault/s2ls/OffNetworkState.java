package im.tny.segvault.s2ls;

import java.util.HashSet;

import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 5/6/17.
 */

public class OffNetworkState extends State {
    protected OffNetworkState(S2LS s2ls) {
        super(s2ls);
    }

    @Override
    public S2LS.StateType getType() {
        return S2LS.StateType.OFF_NETWORK;
    }

    @Override
    public boolean inNetwork() {
        return false;
    }

    @Override
    public boolean nearNetwork() {
        return false;
    }

    @Override
    public Zone getLocation() {
        return new Zone(getS2LS().getNetwork(), new HashSet<Station>());
    }

    @Override
    public void onEnteredNetwork(IInNetworkDetector detector) {
        setState(new InNetworkState(getS2LS()));
    }

    @Override
    public int getPreferredTickIntervalMillis() {
        return 0;
    }

    @Override
    public void onGotNearNetwork(IProximityDetector detector) {
        setState(new NearNetworkState(getS2LS()));
    }
}
