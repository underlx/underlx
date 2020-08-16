package im.tny.segvault.subway;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by gabriel on 4/5/17.
 */

public class Lobby implements IIDable, Comparable<Lobby>, Serializable {
    public Lobby(Station station, String id, String name) {
        setId(id);
        setName(name);
        this.station = station;
        this.exits = new HashMap<>();
        this.schedules = new HashMap<>();
    }

    private Station station;

    public Station getStation() {
        return station;
    }

    private Map<Integer, Exit> exits = null;

    public Collection<Exit> getExits() {
        return exits.values();
    }

    public Exit getExit(int id) {
        return exits.get(id);
    }

    public void addExit(Exit exit) {
        exits.put(exit.id, exit);
    }

    private Map<Integer, Schedule> schedules = null;

    public Collection<Schedule> getSchedules() {
        return schedules.values();
    }

    public Schedule getSchedule(int day) {
        return schedules.get(day);
    }

    public void addSchedule(Schedule schedule) {
        Schedule.addSchedule(schedules, schedule);
    }

    public boolean isAlwaysClosed() {
        for (Schedule s : getSchedules()) {
            if (s.open) {
                return false;
            }
        }
        return true;
    }

    public boolean isOpen(Network network, Date at) {
        return Schedule.isOpen(network, schedules, at);
    }

    public long getNextOpenTime(Network network) {
        return Schedule.getNextOpenTime(network, schedules, new Date());
    }

    public long getNextOpenTime(Network network, Date curDate) {
        return Schedule.getNextOpenTime(network, schedules, curDate);
    }

    public long getNextCloseTime(Network network) {
        return Schedule.getNextCloseTime(network, schedules, new Date());
    }

    public long getNextCloseTime(Network network, Date curDate) {
        return Schedule.getNextCloseTime(network, schedules, curDate);
    }

    private String name;

    public String getName() {
        return name;
    }

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

    @Override
    public String toString() {
        return String.format("Lobby: %s", getName());
    }

    @Override
    public int compareTo(final Lobby o) {
        int idcomp = this.getId().compareTo(o.getId());
        if (idcomp == 0) {
            return this.getName().compareTo(o.getName());
        }
        return idcomp;
    }

    static public class Exit implements Serializable {
        public int id;
        public float[] worldCoord;
        public List<String> streets;
        public String type;

        public Exit(int id, float[] worldCoord, List<String> streets, String type) {
            this.id = id;
            this.worldCoord = worldCoord;
            this.streets = streets;
            this.type = type;
        }

        public String getExitsString() {
            return TextUtils.join(", ", streets);
        }
    }
}
