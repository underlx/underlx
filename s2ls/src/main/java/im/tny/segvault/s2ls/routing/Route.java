package im.tny.segvault.s2ls.routing;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
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
        // 1st part: find the shortest path

        AStarShortestPath as = new AStarShortestPath(network);
        AStarAdmissibleHeuristic heuristic = new AStarAdmissibleHeuristic<Stop>() {
            @Override
            public double getCostEstimate(Stop sourceVertex, Stop targetVertex) {
                return 0;
            }
        };

        // given that we want to treat stations with transfers as a single station,
        // consider all the possibilities and choose the one with the shortest path:

        List<GraphPath> paths = new ArrayList<>();

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

        // assume that only this class can perform this trick with the "hackish" annotations
        // lock on class so that if this method is called concurrently the annotations won't be messed up
        synchronized (Route.class) {
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
        }

        GraphPath path = null;
        for (GraphPath p : paths) {
            if (path == null || p.getWeight() < path.getWeight()) {
                path = p;
            }
        }

        // 2nd part: turn the path into a Route
        return new Route(path);
    }

    private GraphPath<Stop, Connection> path;
    private Map<Integer, Step> pathIndexToStep = new HashMap<>();

    public Route(GraphPath<Stop, Connection> path) {
        this.path = path;

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

    public Stop getSource() {
        return getPath().getEdgeList().get(0).getSource();
    }

    public Stop getTarget() {
        return getPath().getEdgeList().get(getPath().getEdgeList().size() - 1).getTarget();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o) || !(o instanceof Route)) {
            return false;
        }
        Route other = (Route) o;
        int size;
        if ((size = this.size()) != other.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (!this.get(i).equals(other.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean checkEdgeCompliance(Connection toCheck) {
        for (Connection c : path.getEdgeList()) {
            // let's hope reference comparison is OK here,
            // as this stuff doesn't implement equals...
            if (toCheck == c) {
                return true;
            }
        }
        return false;
    }

    public boolean checkPathCompliance(GraphPath<Stop, Connection> otherPath) {
        if (otherPath.getEdgeList().size() == 0) {
            return false;
        }
        Connection lastConnection = otherPath.getEdgeList().get(otherPath.getEdgeList().size() - 1);
        return checkEdgeCompliance(lastConnection);
    }

    public boolean checkPathStartsRoute(GraphPath<Stop, Connection> otherPath) {
        Stop current = otherPath.getEndVertex();
        for (Connection c : path.getEdgeList()) {
            if (c.getSource() == current || c.getTarget() == current) {
                return true;
            }
        }
        return false;
    }

    public boolean checkPathEndsRoute(GraphPath<Stop, Connection> otherPath) {
        return otherPath.getEndVertex() == path.getEdgeList().get(path.getEdgeList().size() - 1).getTarget();
    }

    public Step getNextStep(Path currentPath) {
        if (currentPath == null) {
            return get(0);
        }
        List<Connection> cur = currentPath.getEdgeList();
        List<Connection> tar = path.getEdgeList();
        // iterate over currentPath until the beginning of the route path is found
        int curIdx, tarIdx = 0;
        for (curIdx = 0; curIdx < cur.size(); curIdx++) {
            if(cur.get(curIdx).getSource() == getSource()) {
                break;
            }
        }
        if (curIdx >= cur.size()) {
            // current path doesn't even touch the target one...
            // next step is actually starting to follow instructions
            return get(0);
        }
        // iterate over both paths until the end of the current path is reached
        for (tarIdx = 0; curIdx < cur.size() && tarIdx < tar.size(); tarIdx++, curIdx++) {
            // TODO: check if paths actually match from now on?
            // or assume mistake-handling code will have taken care of that?
        }
        // find next step
        for (--tarIdx; tarIdx < tar.size(); tarIdx++) {
            if(pathIndexToStep.get(tarIdx) != null) {
                return pathIndexToStep.get(tarIdx);
            }
        }
        return null;
    }
}
