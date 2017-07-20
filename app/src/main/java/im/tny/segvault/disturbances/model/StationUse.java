package im.tny.segvault.disturbances.model;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Required;

/**
 * Created by gabriel on 5/8/17.
 */

public class StationUse extends RealmObject {
    private RStation station;
    @Required
    private Date entryDate;
    @Required
    private Date leaveDate;

    public enum UseType {
        NETWORK_ENTRY, // the station was the first station of a trip
        NETWORK_EXIT, // the station was the last station of a trip
        INTERCHANGE, // the station was used to change lines
        GONE_THROUGH, // the user went through the station during a trip
        VISIT, // user entered and exited the station without riding the subway
    }

    private boolean manualEntry;

    // for interchange uses:
    private String sourceLine;
    private String targetLine;

    @Required
    private String type;
    @Ignore
    private transient UseType typeEnum;

    public UseType getType() {
        return UseType.valueOf(type.toUpperCase());
    }

    public void setType(UseType t) {
        type = t.name();
    }

    public RStation getStation() {
        return station;
    }

    public void setStation(RStation station) {
        this.station = station;
    }

    public Date getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(Date entryDate) {
        this.entryDate = entryDate;
    }

    public Date getLeaveDate() {
        return leaveDate;
    }

    public void setLeaveDate(Date leaveDate) {
        this.leaveDate = leaveDate;
    }

    public boolean isManualEntry() {
        return manualEntry;
    }

    public void setManualEntry(boolean manualEntry) {
        this.manualEntry = manualEntry;
    }

    public String getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(String sourceLine) {
        this.sourceLine = sourceLine;
    }

    public String getTargetLine() {
        return targetLine;
    }

    public void setTargetLine(String targetLine) {
        this.targetLine = targetLine;
    }

}
