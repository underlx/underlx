package im.tny.segvault.disturbances.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StationPreferencesDao {
    @Query("SELECT * FROM station_preferences")
    List<StationPreferences> getAll();

    @Query("SELECT * FROM station_preferences WHERE favorite = 1")
    List<StationPreferences> getFavorite();

    @Query("SELECT * FROM station_preferences WHERE favorite = 1")
    LiveData<List<StationPreferences>> getFavoriteLive();

    @Query("SELECT COUNT(*) > 0 FROM station_preferences WHERE favorite = 1 AND stationID = :stationID")
    boolean isFavorite(String stationID);

    @Insert
    void insertAll(StationPreferences... stationPreferences);

    @Update
    void updateAll(StationPreferences... stationPreferences);

    @Delete
    void delete(StationPreferences stationPreferences);

}
