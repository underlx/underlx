package im.tny.segvault.subway;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by gabriel on 4/5/17.
 */

public class Lobby implements INameable, IIDable, Comparable<Lobby>, Serializable {
    public Lobby(String id, String name) {
        setId(id);
        setName(name);
        this.exits = new HashMap<>();
        this.schedules = new HashMap<>();
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
        schedules.put(schedule.holiday ? -1 : schedule.day, schedule);
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

        public Exit(int id, float[] worldCoord, List<String> streets) {
            this.id = id;
            this.worldCoord = worldCoord;
            this.streets = streets;
        }
    }

    static public class Schedule implements Serializable {
        public boolean holiday;
        public int day;
        public boolean open;
        public long openTime;
        public long duration;

        public Schedule(boolean holiday, int day, boolean open, long openTime, long duration) {
            this.holiday = holiday;
            this.day = day;
            this.open = open;
            this.openTime = openTime;
            this.duration = duration;
        }
    }

}
