package im.tny.segvault.s2ls.routing;

import android.support.annotation.Nullable;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.IEdgeWeighter;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;
import im.tny.segvault.subway.Stop;
import im.tny.segvault.subway.Transfer;

/**
 * Created by gabriel on 10/22/17.
 */

public class Route extends ArrayList<Step> {
    public static Route calculate(Network network, Station source, Station target) {
        return calculate(network, source, target, null);
    }

    public static Route calculate(Network network, Station source, Station target, @Nullable ConnectionWeighter weighter) {
        // 1st part: find the shortest path
        GraphPath path = getShortestPath(network, source, target, weighter);

        // 2nd part: turn the path into a Route
        return new Route(path);
    }

    public static GraphPath getShortestPath(Network network, Station source, Station target, @Nullable IEdgeWeighter weighter) {
        // given that we want to treat stations with transfers as a single station,
        // consider all the possibilities and choose the one with the shortest path:

        List<Stop> possibleSources = new ArrayList<>();
        if (source.isAlwaysClosed()) {
            for (Station neighbor : source.getImmediateNeighbors()) {
                possibleSources.addAll(neighbor.getStops());
            }
        } else {
            possibleSources.addAll(source.getStops());
        }

        List<Stop> possibleTargets = new ArrayList<>();
        if (target.isAlwaysClosed()) {
            for (Station neighbor : target.getImmediateNeighbors()) {
                possibleTargets.addAll(neighbor.getStops());
            }
        } else {
            possibleTargets.addAll(target.getStops());
        }
        return getShortestPath(network, possibleSources, possibleTargets, weighter);
    }

    public static GraphPath getShortestPath(Network network, Stop source, Stop target, @Nullable IEdgeWeighter weighter) {
        List<Stop> possibleSources = new ArrayList<>(1);
        possibleSources.add(source);
        List<Stop> possibleTargets = new ArrayList<>(1);
        possibleTargets.add(target);
        return getShortestPath(network, possibleSources, possibleTargets, weighter);
    }

    public static GraphPath getShortestPath(Network network, List<Stop> possibleSources, List<Stop> possibleTargets, @Nullable IEdgeWeighter weighter) {
        AStarShortestPath as = new AStarShortestPath(network);
        AStarAdmissibleHeuristic heuristic = new AStarAdmissibleHeuristic<Stop>() {
            @Override
            public double getCostEstimate(Stop sourceVertex, Stop targetVertex) {
                return 0;
            }
        };
        List<GraphPath> paths = new ArrayList<>();
        // assume that only this class can perform this trick with the "hackish" annotations
        // lock on class so that if this method is called concurrently the annotations won't be messed up
        synchronized (Route.class) {
            IEdgeWeighter prevWeighter = network.getEdgeWeighter();
            if (weighter != null) {
                network.setEdgeWeighter(weighter);
            }
            for (Stop pSource : possibleSources) {
                for (Stop pTarget : possibleTargets) {
                    // hackish "annotations" for the connection weighter
                    pSource.putMeta("is_route_source", true);
                    pTarget.putMeta("is_route_target", true);

                    paths.add(as.getShortestPath(pSource, pTarget, heuristic));
                    pSource.putMeta("is_route_source", null);
                    pTarget.putMeta("is_route_target", null);
                }
            }
            network.setEdgeWeighter(prevWeighter);
        }

        GraphPath path = null;
        for (GraphPath p : paths) {
            if (path == null || p.getWeight() < path.getWeight()) {
                path = p;
            }
        }
        return path;
    }

    private GraphPath<Stop, Connection> path;
    private Map<Integer, Step> pathIndexToStep = new HashMap<>();
    private boolean matchesNeutralPath = false;

    public Route(GraphPath<Stop, Connection> path) {
        this.path = path;

        GraphPath neutralPath = getShortestPath(
                (Network) path.getGraph(),
                path.getStartVertex().getStation(),
                path.getEndVertex().getStation(), new NeutralWeighter());
        matchesNeutralPath = path.getEdgeList().equals(neutralPath.getEdgeList());

        List<Connection> el = path.getEdgeList();

        boolean isFirst = true;
        for (int i = 0; i < el.size(); i++) {
            Connection c = el.get(i);
            if (i == 0 && c instanceof Transfer) {
                // starting with a line change? ignore
                continue;
            }
            if (isFirst) {
                Step step = new EnterStep(
                        c.getSource().getStation(),
                        c.getSource().getLine(),
                        c.getTarget().getLine().getDirectionForConnection(c).getStation());
                this.add(step);
                isFirst = false;
            }

            if (i == el.size() - 1) {
                Step step = new ExitStep(c.getTarget().getStation(), c.getTarget().getLine());
                this.add(step);
                this.pathIndexToStep.put(i, step);
            } else if (c instanceof Transfer) {
                Connection c2 = el.get(i + 1);

                final Line targetLine = c.getTarget().getLine();

                Step step = new ChangeLineStep(
                        c.getSource().getStation(),
                        c.getSource().getLine(),
                        c.getTarget().getLine(),
                        c2.getTarget().getLine().getDirectionForConnection(c2).getStation());
                this.add(step);
                this.pathIndexToStep.put(i, step);
            }
        }
    }

    public GraphPath<Stop, Connection> getPath() {
        return path;
    }

    public boolean hasLineChange() {
        for (Step s : this) {
            if (s instanceof ChangeLineStep) {
                return true;
            }
        }
        return false;
    }

    public boolean isSameAsNeutral() {
        return matchesNeutralPath;
    }

    public Station getSource() {
        return getSourceStop().getStation();
    }

    public Stop getSourceStop() {
        return getPath().getEdgeList().get(0).getSource();
    }

    public Station getTarget() {
        return getTargetStop().getStation();
    }

    public Stop getTargetStop() {
        return getPath().getEdgeList().get(getPath().getEdgeList().size() - 1).getTarget();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o) || !(o instanceof Route)) {
            return false;
        }
        return true;
    }

    private boolean checkEdgeCompliance(Connection toCheck) {
        // returns true if the connection toCheck is part of the target path
        for (Connection c : path.getEdgeList()) {
            if (toCheck == c) {
                return true;
            }
        }
        return false;
    }

    public boolean checkPathCompliance(GraphPath<Stop, Connection> otherPath) {
        if (otherPath.getEdgeList().size() == 0) {
            return checkPathStartsRoute(otherPath);
        }
        Connection lastConnection = otherPath.getEdgeList().get(otherPath.getEdgeList().size() - 1);
        return checkEdgeCompliance(lastConnection);
    }

    public boolean checkPathStartsRoute(GraphPath<Stop, Connection> otherPath) {
        Station current = otherPath.getEndVertex().getStation();
        for (Connection c : path.getEdgeList()) {
            if (c.getSource().getStation() == current || c.getTarget().getStation() == current) {
                return true;
            }
        }
        return false;
    }

    public boolean checkPathEndsRoute(GraphPath<Stop, Connection> otherPath) {
        return otherPath.getEndVertex().getStation() == getTarget();
    }

    public Step getNextStep(Path currentPath) {
        if (currentPath == null) {
            return get(0);
        }
        List<Connection> cur = currentPath.getEdgeList();
        List<Connection> tar = path.getEdgeList();
        if (cur.size() == 0) {
            return get(0);
        }

        // iterate over the target path until the last edge of the current path is found
        Connection lastCur = cur.get(cur.size() - 1);
        int tarIdx;
        for (tarIdx = 0; tarIdx < tar.size(); tarIdx++) {
            if (tar.get(tarIdx) == lastCur) {
                break;
            }
        }
        if (tarIdx >= tar.size()) {
            // last leg of the current path isn't part of the target path,
            // so at the moment the user is not following the instructions
            if (get(0) instanceof EnterStep) {
                // if user is already on a path on the right direction, do not tell him to "catch a train" he is already in
                Stop direction = null;
                if (path.getGraph() instanceof Network) {
                    direction = ((Network) path.getGraph()).getDirectionForConnection(lastCur);
                } else if (path.getGraph() instanceof Line) {
                    direction = ((Line) path.getGraph()).getDirectionForConnection(lastCur);
                }
                if (direction != null && ((EnterStep) get(0)).getDirection() == direction.getStation()) {
                    return get(1);
                }
            }
            return get(0);
        }
        // find next step
        for (; tarIdx < tar.size(); tarIdx++) {
            if (pathIndexToStep.get(tarIdx) != null) {
                return pathIndexToStep.get(tarIdx);
            }
        }
        return null;
    }
}
