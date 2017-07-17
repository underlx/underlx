package im.tny.segvault.s2ls;

import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;

/**
 * Created by Gabriel on 17/07/2017.
 */

public class LeavingNetworkState extends InNetworkState {
    private final static int TICKS_UNTIL_LEFT = 10; // assuming a tick occurs more or less every 30 seconds
    private int ticksSinceLeft = 0;

    protected LeavingNetworkState(S2LS s2ls) {
        super(s2ls);
    }

    protected LeavingNetworkState(S2LS s2ls, Zone current) {
        super(s2ls, current);
    }

    @Override
    public int getPreferredTickIntervalMillis() {
        return 30000;
    }

    @Override
    public void onEnteredStations(ILocator locator, Stop... stops) {
        super.onEnteredStations(locator, stops);
        setState(new InNetworkState(getS2LS(), getLocation()));
    }

    @Override
    public void tick() {
        ticksSinceLeft++;
        if (ticksSinceLeft >= TICKS_UNTIL_LEFT && !getS2LS().detectInNetwork()) {
            if (getS2LS().detectNearNetwork()) {
                setState(new NearNetworkState(getS2LS()));
            } else {
                setState(new OffNetworkState(getS2LS()));
            }
        }
    }

    @Override
    public String toString() {
        return "LeavingNetworkState";
    }
}
