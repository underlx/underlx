package im.tny.segvault.disturbances.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

@Dao
public interface TripDao {
    @Query("SELECT * FROM trip")
    List<Trip> getAll();

    @Query("SELECT * FROM trip WHERE id IN (SELECT trip_id FROM station_use WHERE entry_date > :cutOff)")
    List<Trip> getRecent(Date cutOff);

    @Query("SELECT * FROM trip")
    LiveData<List<Trip>> getAllLive();

    @Query("SELECT * FROM trip WHERE synced = 0 AND sync_failures < 5")
    List<Trip> getUnsynced();

    @Query("SELECT * FROM trip WHERE synced = 0 AND sync_failures < 5")
    LiveData<List<Trip>> getUnsyncedLive();

    @Query("SELECT * FROM trip WHERE user_confirmed = 0 AND id IN (SELECT trip_id FROM station_use WHERE entry_date > :cutOff)")
    List<Trip> getUnconfirmed(Date cutOff);

    @Query("SELECT * FROM trip WHERE user_confirmed = 0 AND id IN (SELECT trip_id FROM station_use WHERE entry_date > :cutOff)")
    LiveData<List<Trip>> getUnconfirmedLive(Date cutOff);

    @Query("SELECT COUNT(*) FROM trip WHERE user_confirmed = 0 AND id IN (SELECT DISTINCT trip_id FROM station_use WHERE entry_date > :cutOff)")
    int getUnconfirmedCount(Date cutOff);

    @Query("SELECT * FROM trip WHERE id = :id")
    Trip get(String id);

    @Insert
    void insertAll(Trip... trips);

    @Update
    void updateAll(Trip... trips);

    @Delete
    void delete(Trip trip);

    @Query("DELETE FROM trip")
    void deleteAll();
}
