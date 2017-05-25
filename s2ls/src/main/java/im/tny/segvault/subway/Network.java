package im.tny.segvault.subway;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by gabriel on 4/5/17.
 */

public class Network extends SimpleDirectedWeightedGraph<Stop, Connection> implements INameable, IIDable {
    public Network(String id, String name, int usualCarCount) {
        /*super(new ConnectionFactory());
        ((ConnectionFactory)this.getEdgeFactory()).setNetwork(this);*/
        super(Connection.class);
        setId(id);
        setName(name);
        setUsualCarCount(usualCarCount);
    }

    private String name;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    private String id;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    private String datasetVersion;

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(String datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    private List<String> datasetAuthors;

    public List<String> getDatasetAuthors() {
        return datasetAuthors;
    }

    public void setDatasetAuthors(List<String> datasetAuthors) {
        this.datasetAuthors = datasetAuthors;
    }

    private int usualCarCount;

    public int getUsualCarCount() {
        return usualCarCount;
    }

    public void setUsualCarCount(int usualCarCount) {
        this.usualCarCount = usualCarCount;
    }

    private Map<String, Line> lines = new HashMap<>();

    public Collection<Line> getLines() {
        return lines.values();
    }

    public Line getLine(String id) {
        return lines.get(id);
    }

    public void addLine(Line line) {
        lines.put(line.getId(), line);
    }

    private Map<String, Station> stations = new HashMap<>();

    public Station getStation(String id) {
        return stations.get(id);
    }

    public Collection<Station> getStations() {
        return stations.values();
    }

    @Override
    public boolean addVertex(Stop stop) {
        Station s = stations.get(stop.getStation().getId());
        if (s == null) {
            s = stop.getStation();
            stations.put(stop.getStation().getId(), s);
        }
        return super.addVertex(stop);
    }

    @Override
    public boolean removeVertex(Stop stop) {
        Station s = stations.get(stop.getStation().getId());
        s.removeVertex(stop);
        if (s.vertexSet().size() == 0) {
            stations.remove(stop.getStation().getId());
        }
        return super.removeVertex(stop);
    }

    private transient IEdgeWeighter edgeWeighter = null;

    public void setEdgeWeighter(IEdgeWeighter edgeWeighter) {
        this.edgeWeighter = edgeWeighter;
    }

    @Override
    public double getEdgeWeight(Connection connection) {
        if (edgeWeighter == null) {
            return super.getEdgeWeight(connection);
        } else {
            return edgeWeighter.getEdgeWeight(this, connection);
        }
    }

    public double getDefaultEdgeWeight(Connection connection) {
        return super.getEdgeWeight(connection);
    }

    public GraphPath<Stop, Connection> getAnyPathBetween(Stop source, Stop target) {
        AStarShortestPath as = new AStarShortestPath(this);
        AStarAdmissibleHeuristic heuristic = new AStarAdmissibleHeuristic<Stop>() {
            @Override
            public double getCostEstimate(Stop sourceVertex, Stop targetVertex) {
                return 0;
            }
        };

        return as.getShortestPath(source, target, heuristic);
    }

    @Override
    public String toString() {
        return String.format("Network: %s", getName());
    }
}
