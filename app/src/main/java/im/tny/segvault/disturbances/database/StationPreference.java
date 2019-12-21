package im.tny.segvault.disturbances.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "station_preference")
public class StationPreference {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "station_id")
    public String stationID = "";

    @NonNull
    @ColumnInfo(name = "network_id")
    public String networkID = "";

    @ColumnInfo(name = "favorite")
    public boolean favorite;
}
