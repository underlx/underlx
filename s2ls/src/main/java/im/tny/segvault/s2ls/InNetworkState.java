package im.tny.segvault.s2ls;

import java.util.HashSet;

import im.tny.segvault.s2ls.wifi.WiFiLocator;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 5/6/17.
 */

public class InNetworkState extends State {
    private Zone current = new Zone(getS2LS().getNetwork(), new HashSet<Stop>());

    protected InNetworkState(S2LS s2ls) {
        super(s2ls);
    }

    protected InNetworkState(S2LS s2ls, Zone current) {
        super(s2ls);
        this.current = current;
    }

    @Override
    public int getPreferredTickIntervalMillis() {
        return 0;
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
    public void onLeftNetwork(IInNetworkDetector detector) {
        if (detector instanceof WiFiLocator) {
            if (getS2LS().getCurrentTrip() != null) {
                getS2LS().getCurrentTrip().registerExitTime();
            }
            setState(new LeavingNetworkState(getS2LS(), current));
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
    public void onLeaveState(State newState) {
        if (!(newState instanceof InNetworkState)) {
            getS2LS().endCurrentTripInternal();
        }
    }

    @Override
    public String toString() {
        return "InNetworkState";
    }
}
