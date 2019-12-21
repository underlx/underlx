package im.tny.segvault.disturbances.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StationPreferenceDao {
    @Query("SELECT * FROM station_preference")
    List<StationPreference> getAll();

    @Query("SELECT * FROM station_preference WHERE station_id = :stationID")
    StationPreference getForStation(String stationID);

    @Query("SELECT * FROM station_preference WHERE favorite = 1")
    List<StationPreference> getFavorite();

    @Query("SELECT * FROM station_preference WHERE favorite = 1")
    LiveData<List<StationPreference>> getFavoriteLive();

    @Query("SELECT COUNT(*) > 0 FROM station_preference WHERE favorite = 1 AND station_id = :stationID")
    boolean isFavorite(String stationID);

    @Insert
    void insertAll(StationPreference... stationPreferences);

    @Update
    void updateAll(StationPreference... stationPreferences);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAll(StationPreference... rules);

    @Delete
    void delete(StationPreference stationPreference);

}
