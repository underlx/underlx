package im.tny.segvault.disturbances.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import java.util.Date;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
        tableName = "station_use",
        foreignKeys = @ForeignKey(
                entity = Trip.class,
                parentColumns = "id",
                childColumns = "trip_id",
                onDelete = CASCADE
        ),
        primaryKeys = {"trip_id", "station_id", "order"}
)
public class StationUse {
    @NonNull
    @ColumnInfo(name = "trip_id")
    public String tripID = "";

    @NonNull
    @ColumnInfo(name = "station_id")
    public String stationID = "";

    @ColumnInfo(name = "order")
    public int order;

    @NonNull
    @ColumnInfo(name = "entry_date")
    public Date entryDate = new Date();

    @NonNull
    @ColumnInfo(name = "leave_date")
    public Date leaveDate = new Date();

    @ColumnInfo(name = "manual_entry")
    public boolean manualEntry;

    @ColumnInfo(name = "source_line")
    public String sourceLine;

    @ColumnInfo(name = "target_line")
    public String targetLine;

    public enum UseType {
        NETWORK_ENTRY, // the station was the first station of a trip
        NETWORK_EXIT, // the station was the last station of a trip
        INTERCHANGE, // the station was used to change lines
        GONE_THROUGH, // the user went through the station during a trip
        VISIT, // user entered and exited the station without riding the subway
    }

    @NonNull
    @ColumnInfo(name = "type")
    public UseType type = UseType.VISIT;
}
