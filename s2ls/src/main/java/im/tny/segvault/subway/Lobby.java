package im.tny.segvault.subway;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gabriel on 4/5/17.
 */

public class Lobby implements INameable, IIDable, Comparable<Lobby>, Serializable {
    public Lobby(String id, String name) {
        setId(id);
        setName(name);
        this.exits = new ArrayList<>();
    }

    private List<Exit> exits = null;

    public List<Exit> getExits() {
        return exits;
    }

    public boolean addExit(Exit exit) {
        return exits.add(exit);
    }

    private List<Schedule> schedules = null;

    public List<Schedule> getSchedules() {
        return schedules;
    }

    public boolean addSchedule(Schedule schedule) {
        return schedules.add(schedule);
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

        public Schedule(boolean holiday, int day, boolean open, long openTime) {
            this.holiday = holiday;
            this.day = day;
            this.open = open;
            this.openTime = openTime;
        }
    }

}
