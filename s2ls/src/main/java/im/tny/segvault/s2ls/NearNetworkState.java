package im.tny.segvault.s2ls;

import java.util.HashSet;

import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 5/6/17.
 */

public class NearNetworkState extends State {
    protected NearNetworkState(S2LS s2ls) {
        super(s2ls);
    }

    @Override
    public S2LS.StateType getType() {
        return S2LS.StateType.NEAR_NETWORK;
    }

    @Override
    public boolean inNetwork() {
        return false;
    }

    @Override
    public boolean nearNetwork() {
        return true;
    }

    @Override
    public Zone getLocation() {
        return new Zone(getS2LS().getNetwork(), new HashSet<Stop>());
    }

    @Override
    public int getPreferredTickIntervalMillis() {
        return 0;
    }

    @Override
    public void onEnteredNetwork(IInNetworkDetector detector) {
        setState(new InNetworkState(getS2LS()));
    }

    @Override
    public void onGotAwayFromNetwork(IProximityDetector detector) {
        setState(new OffNetworkState(getS2LS()));
    }
}
