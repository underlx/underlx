package im.tny.segvault.subway;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.AStarShortestPath;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by gabriel on 4/5/17.
 */

public class Network extends SimpleDirectedWeightedGraph<Stop, Connection> implements INameable, IIDable {
    public Network(String id, String name, int usualCarCount, List<Integer> holidays, long openTime, long duration, String timezone, String announcementsURL) {
        /*super(new ConnectionFactory());
        ((ConnectionFactory)this.getEdgeFactory()).setNetwork(this);*/
        super(Connection.class);
        setId(id);
        setName(name);
        setUsualCarCount(usualCarCount);
        setHolidays(holidays);
        setOpenTime(openTime);
        setOpenDuration(duration);
        setTimezone(timezone);
        setAnnouncementsURL(announcementsURL);
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

    private List<Integer> holidays;

    public List<Integer> getHolidays() {
        return holidays;
    }

    public void setHolidays(List<Integer> holidays) {
        this.holidays = holidays;
    }

    private long openTime;

    public long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(long openTime) {
        this.openTime = openTime;
    }

    private long openDuration;

    public long getOpenDuration() {
        return openDuration;
    }

    public void setOpenDuration(long openDuration) {
        this.openDuration = openDuration;
    }

    private TimeZone timezone;

    public TimeZone getTimezone() {
        return timezone;
    }

    public void setTimezone(TimeZone timezone) {
        this.timezone = timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = TimeZone.getTimeZone(timezone);
    }

    private String announcementsURL;

    public String getAnnouncementsURL() {
        return announcementsURL;
    }

    public void setAnnouncementsURL(String announcementsURL) {
        this.announcementsURL = announcementsURL;
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

    public boolean isOpen() {
        return isOpen(new Date());
    }

    public boolean isAboutToClose() {
        return isAboutToClose(new Date());
    }

    public boolean isAboutToClose(Date at) {
        return isOpen(at) && !isOpen(new Date(at.getTime() + 15*60*1000));
    }

    public boolean isOpen(Date at) {
        Calendar c = Calendar.getInstance(getTimezone());
        long now = at.getTime();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long passed = now - c.getTimeInMillis();
        boolean openToday = passed >= getOpenTime() && passed < getOpenTime() + getOpenDuration();
        c.add(Calendar.DATE, -1);
        passed = now - c.getTimeInMillis();
        boolean openYesterday = passed >= getOpenTime() && passed < getOpenTime() + getOpenDuration();
        return openToday || openYesterday;
    }

    public Stop getDirectionForConnection(Connection c) {
        return c.getSource().getLine().getDirectionForConnection(c);
    }

    @Override
    public String toString() {
        return String.format("Network: %s", getName());
    }
}
