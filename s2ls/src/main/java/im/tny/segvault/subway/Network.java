package im.tny.segvault.subway;

import android.util.Pair;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Created by gabriel on 4/5/17.
 */

public class Network extends SimpleDirectedWeightedGraph<Station, Connection> implements INameable, IIDable {
    public Network(String id, String name) {
        /*super(new ConnectionFactory());
        ((ConnectionFactory)this.getEdgeFactory()).setNetwork(this);*/
        super(Connection.class);
        setId(id);
        setName(name);
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

    public int getUsualCarCount() {
        // TODO: do not hardcode
        return 6;
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

    private Map<String, List<Station>> stations = new HashMap<>();

    @Override
    public boolean addVertex(Station station) {
        List<Station> l = new ArrayList<>();
        if (stations.get(station.getId()) != null) {
            l = stations.get(station.getId());
        }
        l.add(station);
        stations.put(station.getId(), l);
        return super.addVertex(station);
    }

    @Override
    public boolean removeVertex(Station station) {
        List<Station> l = stations.get(station.getId());
        l.remove(station);
        if (l.size() == 0) {
            stations.remove(station.getId());
        }
        return super.removeVertex(station);
    }

    public List<Station> getStation(String id) {
        return stations.get(id);
    }
/*
    private Map<String, Object> metaMap = new HashMap<>();

    public Object getMeta(String key) {
        return metaMap.get(key);
    }

    public Object putMeta(String key, Object object) {
        return metaMap.put(key, object);
    }
    */

    @Override
    public String toString() {
        return String.format("Network: %s", getName());
    }
}
