package im.tny.segvault.disturbances.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "station_preferences")
public class StationPreferences {
    @PrimaryKey
    @NonNull
    public String stationID = "";

    @NonNull
    public String networkID = "";

    public boolean favorite;
}
