package im.tny.segvault.s2ls;

import java.util.ArrayList;
import java.util.Collection;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Zone;

/**
 * Created by gabriel on 4/5/17.
 */

public class S2LS implements OnStatusChangeListener {
    private Network network;
    private Collection<ILocator> locators = new ArrayList<>();
    private Collection<IInNetworkDetector> networkDetectors = new ArrayList<>();
    private Collection<IProximityDetector> proximityDetectors = new ArrayList<>();
    private EventListener listener = null;

    private State state;

    public S2LS(Network network, EventListener listener) {
        this.network = network;
        this.listener = listener;
        // TODO: adjust initial state to current conditions
        this.setState(new OffNetworkState(this));
    }

    public Network getNetwork() {
        return network;
    }

    public void addLocator(ILocator locator) {
        locator.setListener(this);
        locators.add(locator);
    }

    public void addNetworkDetector(IInNetworkDetector detector) {
        detector.setListener(this);
        networkDetectors.add(detector);
    }

    public void addProximityDetector(IProximityDetector detector) {
        detector.setListener(this);
        proximityDetectors.add(detector);
    }

    protected boolean detectInNetwork() {
        for (IInNetworkDetector d : networkDetectors) {
            if (d.inNetwork(network)) {
                return true;
            }
        }
        return false;
    }

    protected boolean detectNearNetwork() {
        for (IProximityDetector d : proximityDetectors) {
            if (d.nearNetwork(network)) {
                return true;
            }
        }
        return false;
    }

    protected Zone detectLocation() {
        Zone zone = new Zone(network, network.vertexSet());
        for (ILocator l : locators) {
            zone.intersect(l.getLocation(network));
        }
        return zone;
    }

    public boolean inNetwork() {
        return state.inNetwork();
    }

    public boolean nearNetwork() {
        return state.nearNetwork();
    }

    public Zone getLocation() {
        return state.getLocation();
    }

    @Override
    public void onEnteredNetwork(IInNetworkDetector detector) {
        state.onEnteredNetwork(detector);
    }

    @Override
    public void onLeftNetwork(IInNetworkDetector detector) {
        state.onLeftNetwork(detector);
    }

    @Override
    public void onGotNearNetwork(IProximityDetector detector) {
        state.onGotNearNetwork(detector);
    }

    @Override
    public void onGotAwayFromNetwork(IProximityDetector detector) {
        state.onGotAwayFromNetwork(detector);
    }

    @Override
    public void onEnteredStations(ILocator locator, Stop... stops) {
        state.onEnteredStations(locator, stops);
    }

    @Override
    public void onLeftStations(ILocator locator, Stop... stops) {
        state.onLeftStations(locator, stops);
    }

    public void tick() {
        state.tick();
    }

    protected void setState(State state) {
        if (this.state != null) {
            this.state.onLeaveState();
        }
        this.state = state;
        if (listener != null) {
            listener.onStateChanged(this);
        }
    }

    public State getState() {
        return this.state;
    }

    private Path path = null;

    public Path getCurrentTrip() {
        return path;
    }

    protected void startNewTrip(Stop stop) {
        path = new Path(getNetwork(), stop, 0);
        listener.onTripStarted(this);
    }

    protected void endCurrentTrip() {
        listener.onTripEnded(this);
        path = null;
    }

    public EventListener getEventListener() {
        return listener;
    }

    public interface EventListener {
        void onStateChanged(S2LS s2ls);
        void onTripStarted(S2LS s2ls);
        void onTripEnded(S2LS s2ls);
    }
}
