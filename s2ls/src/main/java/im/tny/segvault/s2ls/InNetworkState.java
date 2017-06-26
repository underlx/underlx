package im.tny.segvault.s2ls;

import java.util.HashSet;

import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 5/6/17.
 */

public class InNetworkState extends State {
    private final static int TICKS_UNTIL_LEFT = 6; // assuming a tick occurs more or less every 30 seconds
    private Zone current = new Zone(getS2LS().getNetwork(), new HashSet<Stop>());

    protected InNetworkState(S2LS s2ls) {
        super(s2ls);
    }

    @Override
    public boolean inNetwork() {
        return true;
    }

    @Override
    public boolean nearNetwork() {
        return true;
    }

    @Override
    public Zone getLocation() {
        return current;
    }

    @Override
    public int getPreferredTickIntervalMillis() {
        return 30000;
    }

    private boolean mightHaveLeft = false;
    private int ticksSinceLeft = 0;

    @Override
    public void onLeftNetwork(IInNetworkDetector detector) {
        if (detector instanceof WiFiLocator) {
            ticksSinceLeft = 0;
            mightHaveLeft = true;
            if (getS2LS().getCurrentTrip() != null) {
                getS2LS().getCurrentTrip().registerExitTime();
            }
        } else {
            if (getS2LS().detectNearNetwork()) {
                setState(new NearNetworkState(getS2LS()));
            } else {
                setState(new OffNetworkState(getS2LS()));
            }
        }
    }

    @Override
    public void onEnteredStations(ILocator locator, Stop... stops) {
        for (Stop stop : stops) {
            current.addVertex(stop);
        }
        mightHaveLeft = false;
        if (getS2LS().getCurrentTrip() == null) {
            getS2LS().startNewTrip(stops[0]);
        } else {
            getS2LS().getCurrentTrip().setEndVertex(stops[stops.length - 1]);
        }
    }

    @Override
    public void onLeftStations(ILocator locator, Stop... stops) {
        for (Stop stop : stops) {
            current.removeVertex(stop);
            if (getS2LS().getCurrentTrip() != null) {
                getS2LS().getCurrentTrip().registerExitTime(stop);
            }
        }
    }

    @Override
    public void tick() {
        if (mightHaveLeft) {
            ticksSinceLeft++;
            if (ticksSinceLeft >= TICKS_UNTIL_LEFT && !getS2LS().detectInNetwork()) {
                if (getS2LS().detectNearNetwork()) {
                    setState(new NearNetworkState(getS2LS()));
                } else {
                    setState(new OffNetworkState(getS2LS()));
                }
            }
        }
    }

    @Override
    public void onLeaveState() {
        getS2LS().endCurrentTrip();
    }

    @Override
    public String toString() {
        return "InNetworkState";
    }
}
