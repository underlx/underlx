package im.tny.segvault.subway;

import org.jgrapht.graph.DefaultWeightedEdge;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by gabriel on 4/5/17.
 */

public class Connection extends DefaultWeightedEdge {
    private boolean virtual = false;
    private Stop virtualSource;
    private Stop virtualTarget;
    private int worldLength;
    private Times times;

    public Connection() {

    }

    public Connection(boolean virtual, Stop source, Stop target, Times times, int worldLength) {
        this.virtual = virtual;
        if(virtual) {
            virtualSource = source;
            virtualTarget = target;
        }
        this.times = times;
        this.worldLength = worldLength;
    }

    public List<Stop> getStops() {
        List<Stop> s = new ArrayList<>();
        s.add(getSource());
        s.add(getTarget());
        return s;
    }

    public Stop getSource() {
        if(virtual) {
            return virtualSource;
        }
        return (Stop) super.getSource();
    }

    public Stop getTarget() {
        if(virtual) {
            return virtualTarget;
        }
        return (Stop) super.getTarget();
    }

    public Times getTimes() {
        return times;
    }

    public void setTimes(Times times) {
        this.times = times;
    }

    public int getWorldLength() {
        return worldLength;
    }

    public void setWorldLength(int worldLength) {
        this.worldLength = worldLength;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    static public class Times implements Serializable {
        public int typWaitingSeconds;
        public int typStopSeconds;
        public int typSeconds;

        public Times(int typWaitingSeconds, int typStopSeconds, int typSeconds) {
            this.typWaitingSeconds = typWaitingSeconds;
            this.typStopSeconds = typStopSeconds;
            this.typSeconds = typSeconds;
        }
    }
}
