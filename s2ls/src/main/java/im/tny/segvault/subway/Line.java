package im.tny.segvault.subway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by gabriel on 4/5/17.
 */

public class Line extends Zone implements IColorable, IIDable, Comparable<Line> {
    public Line(Network network, String mainLocale, Map<String, String> names, Set<Stop> stops, String id, int usualCarCount, int order) {
        super(network, stops);
        setId(id);
        this.mainLocale = mainLocale;
        this.names = names;
        setUsualCarCount(usualCarCount);
        setOrder(order);
        this.schedules = new HashMap<>();
    }

    private int color;

    public int getColor() {
        return color;
    }

    @Override
    public void setColor(int color) {
        this.color = color;
    }

    private String mainLocale;
    private Map<String, String> names;
    private List<Station> stationsInOrder = new ArrayList<>();

    public String getMainLocale() {
        return mainLocale;
    }

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

    @Override
    public boolean addVertex(Stop stop) {
        boolean contained = getBase().addVertex(stop);
        boolean contained_sub = super.addVertex(stop);
        if(!stationsInOrder.contains(stop.getStation())) {
            stationsInOrder.add(stop.getStation());
        }
        return contained || contained_sub;
    }

    public List<Station> getStationsInOrder() {
        return new ArrayList<>(stationsInOrder);
    }

    @Override
    public Connection addEdge(Stop source, Stop dest) {
        if (!getBase().containsEdge(source, dest)) {
            getBase().addEdge(source, dest);
        }
        return super.addEdge(source, dest);
    }

    @Override
    public String toString() {
        return String.format("Line: %s with color %s", getNames(mainLocale)[0], String.format("#%06X", 0xFFFFFF & getColor()));
    }

    public Set<Stop> getEndStops() {
        Set<Stop> ends = new HashSet<>();
        for (Stop s : vertexSet()) {
            if (outDegreeOf(s) == 1) {
                ends.add(s);
            }
        }
        return ends;
    }

    public Stop getDirectionForConnection(Connection c) {
        if (!edgeSet().contains(c)) {
            return null;
        }

        Set<Stop> visited = new HashSet<>();
        while (visited.add(c.getSource())) {
            Stop curStop = c.getTarget();
            if (outDegreeOf(curStop) == 1) {
                // it's an end station
                return curStop;
            }
            for (Connection outedge : outgoingEdgesOf(curStop)) {
                if (!visited.contains(outedge.getTarget())) {
                    c = outedge;
                    break;
                }
            }
        }
        // circular line
        return c.getTarget();
    }

    public Stop getStopAfter(Connection c) {
        if (!edgeSet().contains(c)) {
            return null;
        }

        while(outgoingEdgesOf(c.getTarget()).size() > 1) {
            for (Connection outedge : outgoingEdgesOf(c.getTarget())) {
                if (outedge.getTarget() != c.getSource() && !outedge.getTarget().getStation().isExceptionallyClosed(getNetwork(), new Date())) {
                    return outedge.getTarget();
                }
            }
            for (Connection outedge : outgoingEdgesOf(c.getTarget())) {
                if(outedge.getTarget() != c.getSource()) {
                    c = outedge;
                    break;
                }
            }
        }
        return null;
    }

    private Stop firstStop;

    public Stop getFirstStop() {
        return firstStop;
    }

    public void setFirstStop(Stop stop) {
        firstStop = stop;
    }

    private int usualCarCount;

    public int getUsualCarCount() {
        return usualCarCount;
    }

    public void setUsualCarCount(int usualCarCount) {
        this.usualCarCount = usualCarCount;
    }

    private int order;

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int compareTo(final Line o) {
        return this.getId().compareTo(o.getId());
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
        return isOpen(at) && !isOpen(new Date(at.getTime() + 15*60*1000));
    }

    public boolean isOpen(Date at) {
        return Schedule.isOpen(getNetwork(), schedules, at);
    }

    public boolean isExceptionallyClosed(Date at) {
        return getNetwork().isOpen(at) && !isOpen(at);
    }

    public long getNextOpenTime() {
        return Schedule.getNextOpenTime(getNetwork(), schedules, new Date());
    }

    public long getNextOpenTime(Date curDate) {
        return Schedule.getNextOpenTime(getNetwork(), schedules, curDate);
    }

    public long getNextCloseTime() {
        return Schedule.getNextCloseTime(getNetwork(), schedules, new Date());
    }

    public long getNextCloseTime(Date curDate) {
        return Schedule.getNextCloseTime(getNetwork(), schedules, curDate);
    }

    private Map<String, WorldPath> worldPaths = new HashMap<>();

    public void addPath(WorldPath path) {
        worldPaths.put(path.getId(), path);
    }

    public Collection<WorldPath> getPaths() {
        return worldPaths.values();
    }

    public WorldPath getPath(String id) {
        return worldPaths.get(id);
    }
}
