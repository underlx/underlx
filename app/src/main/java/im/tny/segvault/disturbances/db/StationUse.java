package im.tny.segvault.disturbances.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;

import java.util.Date;

/**
 * Created by gabriel on 7/9/17.
 */

@Entity(tableName = "station_use",
        primaryKeys = {"trip_id", "station_id", "entry_date"},
        foreignKeys = {
                @ForeignKey(
                        entity = DbStation.class,
                        parentColumns = "id",
                        childColumns = "station_id"),
                @ForeignKey(
                        entity = TripEntity.class,
                        parentColumns = "id",
                        childColumns = "trip_id")
        })
public class StationUse {
    @ColumnInfo(name = "trip_id")
    private String tripId;
    @ColumnInfo(name = "station_id")
    private String stationId;
    @ColumnInfo(name = "entry_date")
    private Date entryDate;
    @ColumnInfo(name = "leave_date")
    private Date leaveDate;

    // for interchange uses:
    @ColumnInfo(name = "source_line")
    private String sourceLine;
    @ColumnInfo(name = "target_line")
    private String targetLine;

    private String type;

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String id) {
        this.tripId = id;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String id) {
        this.stationId = id;
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

    public enum UseType {
        NETWORK_ENTRY, // the station was the first station of a trip
        NETWORK_EXIT, // the station was the last station of a trip
        INTERCHANGE, // the station was used to change lines
        GONE_THROUGH, // the user went through the station during a trip
        VISIT, // user entered and exited the station without riding the subway
    }

    public UseType getType() {
        return UseType.valueOf(type.toUpperCase());
    }

    public void setType(UseType t) {
        type = t.name();
    }
}
