package im.tny.segvault.s2ls.routing;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;

import java.util.ArrayList;
import java.util.List;

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
    public static Route create(Network network, Station source, Station target) {
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

    private GraphPath path;

    private Route(GraphPath path) {
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
                this.add(new EnterStep(
                        c.getSource().getStation(),
                        c.getSource().getLine(),
                        c.getTarget().getLine().getDirectionForConnection(c).getStation()));
                isFirst = false;
            }

            if (i == el.size() - 1) {
                this.add(new ExitStep(c.getTarget().getStation(), c.getTarget().getLine()));
            } else if (c instanceof Transfer) {
                Connection c2 = el.get(i + 1);

                final Line targetLine = c.getTarget().getLine();

                this.add(new ChangeLineStep(
                        c.getSource().getStation(),
                        c.getSource().getLine(),
                        c.getTarget().getLine(),
                        c2.getTarget().getLine().getDirectionForConnection(c2).getStation()));
            }
        }
    }

    public GraphPath getPath() {
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
}
