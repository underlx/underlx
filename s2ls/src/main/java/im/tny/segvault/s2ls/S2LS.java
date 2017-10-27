package im.tny.segvault.s2ls;

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import im.tny.segvault.s2ls.routing.Route;
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
            this.state.onLeaveState(state);
        }
        this.state = state;
        if (listener != null) {
            listener.onStateChanged(this);
        }
    }

    public State getState() {
        return this.state;
    }

    @Nullable
    private Route targetRoute = null;

    public Route getCurrentTargetRoute() {
        return targetRoute;
    }

    public void setCurrentTargetRoute(Route route) {
        synchronized (this) {
            targetRoute = route;
            routePathChecker.onNewTargetRoute();
        }
    }

    private Path path = null;

    public Path getCurrentTrip() {
        return path;
    }

    protected void startNewTrip(Stop stop) {
        path = new Path(getNetwork(), stop, 0);
        path.addPathChangedListener(routePathChecker);
        listener.onTripStarted(this);
    }

    protected void endCurrentTripInternal() {
        Path p = path;
        path = null;
        listener.onTripEnded(this, p);
    }

    public void endCurrentTrip() {
        if (detectNearNetwork()) {
            setState(new NearNetworkState(this));
        } else {
            setState(new OffNetworkState(this));
        }
    }

    private RoutePathChecker routePathChecker = new RoutePathChecker();

    private class RoutePathChecker implements Path.OnPathChangedListener {
        private boolean beganFollowingRoute = false;

        @Override
        public void onPathChanged(Path path) {
            synchronized (S2LS.this) {
                if (targetRoute == null) {
                    return;
                }
                if (!beganFollowingRoute && targetRoute.checkPathStartsRoute(path)) {
                    beganFollowingRoute = true;
                    listener.onRouteStarted(S2LS.this, path, targetRoute);
                } else if (beganFollowingRoute &&
                        !(
                                targetRoute.checkPathCompliance(path) ||
                                        (path.getEdgeList().size() == 0 && targetRoute.checkPathStartsRoute(path))
                        )) {
                    listener.onRouteMistake(S2LS.this, path, targetRoute);
                }

                if (beganFollowingRoute && targetRoute.checkPathEndsRoute(path)) {
                    listener.onRouteCompleted(S2LS.this, path, targetRoute);
                }
            }
        }

        void onNewTargetRoute() {
            beganFollowingRoute = false;
        }
    }

    public EventListener getEventListener() {
        return listener;
    }

    public interface EventListener {
        void onStateChanged(S2LS s2ls);

        void onTripStarted(S2LS s2ls);

        void onTripEnded(S2LS s2ls, Path path);

        void onRouteStarted(S2LS s2ls, Path path, Route route);

        void onRouteMistake(S2LS s2ls, Path path, Route route);

        void onRouteCompleted(S2LS s2ls, Path path, Route route);
    }
}
