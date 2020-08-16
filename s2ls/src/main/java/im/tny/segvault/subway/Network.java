package im.tny.segvault.subway;

import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by gabriel on 4/5/17.
 */

public class Network extends SimpleDirectedWeightedGraph<Stop, Connection> implements IIDable {
    public Network(String id, String mainLocale, Map<String, String> names, int usualCarCount, List<Integer> holidays, String timezone, String announcementsURL) {
        super(Connection.class);
        setId(id);
        this.mainLocale = mainLocale;
        this.names = names;
        setUsualCarCount(usualCarCount);
        setHolidays(holidays);
        setTimezone(timezone);
        setAnnouncementsURL(announcementsURL);
        this.schedules = new HashMap<>();
    }

    private String mainLocale;
    private Map<String, String> names;

    public String[] getNames(String locale) {
        if (locale.equals(mainLocale)) {
            return new String[]{names.get(locale)};
        }
        if (names.get(locale) != null) {
            return new String[]{names.get(mainLocale), names.get(locale)};
        }
        // no translation available for this locale
        return new String[]{names.get(mainLocale)};
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
    private Map<String, Line> linesByExtID = new HashMap<>();

    public Collection<Line> getLines() {
        return lines.values();
    }

    public Line getLine(String id) {
        return lines.get(id);
    }

    public Line getLineByExternalID(String id) {
        return linesByExtID.get(id);
    }

    public void addLine(Line line) {
        lines.put(line.getId(), line);
        linesByExtID.put(line.getExternalID(), line);
    }

    private Map<String, Station> stations = new HashMap<>();
    private Map<String, Station> stationNames = new HashMap<>();

    public Station getStation(String id) {
        return stations.get(id);
    }

    public Station getStationByName(String name) {
        return stationNames.get(name);
    }

    public Collection<Station> getStations() {
        return stations.values();
    }


    private Map<String, POI> pois = new HashMap<>();

    public POI getPOI(String id) {
        return pois.get(id);
    }

    public Collection<POI> getPOIs() {
        return pois.values();
    }

    public void addPOI(POI poi) {
        pois.put(poi.getId(), poi);
    }

    @Override
    public boolean addVertex(Stop stop) {
        Station s = stations.get(stop.getStation().getId());
        if (s == null) {
            s = stop.getStation();
            stations.put(stop.getStation().getId(), s);
            stationNames.put(stop.getStation().getName(), s);
        }
        return super.addVertex(stop);
    }

    @Override
    public boolean removeVertex(Stop stop) {
        Station s = stations.get(stop.getStation().getId());
        s.removeVertex(stop);
        if (s.vertexSet().size() == 0) {
            stations.remove(stop.getStation().getId());
            stationNames.remove(stop.getStation().getName());
        }
        return super.removeVertex(stop);
    }

    private transient IEdgeWeighter edgeWeighter = null;

    public IEdgeWeighter getEdgeWeighter() {
        return edgeWeighter;
    }

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

    public Stop getDirectionForConnection(Connection c) {
        return c.getSource().getLine().getDirectionForConnection(c);
    }

    @Override
    public String toString() {
        return String.format("Network: %s", getNames(mainLocale)[0]);
    }

    private Map<Integer, Schedule> schedules = null;

    public void addSchedule(Schedule schedule) {
        Schedule.addSchedule(schedules, schedule);
    }

    public boolean isOpen() {
        return isOpen(new Date());
    }

    public boolean isAboutToClose() {
        return isAboutToClose(new Date());
    }

    public boolean isAboutToClose(Date at) {
        return isOpen(at) && !isOpen(new Date(at.getTime() + 15 * 60 * 1000));
    }

    public boolean isOpen(Date at) {
        return Schedule.isOpen(this, schedules, at);
    }

    public long getNextOpenTime() {
        return Schedule.getNextOpenTime(this, schedules, new Date());
    }

    public long getNextOpenTime(Date curDate) {
        return Schedule.getNextOpenTime(this, schedules, curDate);
    }

    public long getNextCloseTime() {
        return Schedule.getNextCloseTime(this, schedules, new Date());
    }

    public long getNextCloseTime(Date curDate) {
        return Schedule.getNextCloseTime(this, schedules, curDate);
    }

    public static abstract class Plan {
    }

    public static class HtmlDiagram extends Plan {
        private String url;
        private boolean wideViewport;

        public HtmlDiagram(String url, boolean wideViewport) {
            this.url = url;
            this.wideViewport = wideViewport;
        }

        public String getUrl() {
            return url;
        }

        public boolean needsWideViewport() {
            return wideViewport;
        }

    }

    public static class WorldMap extends Plan {
    }

    private List<Plan> maps = new ArrayList<>();

    public List<Plan> getMaps() {
        return maps;
    }

    public void addMap(Plan map) {
        maps.add(map);
    }
}
