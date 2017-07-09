package im.tny.segvault.disturbances.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

/**
 * Created by gabriel on 7/9/17.
 */

@Dao
public interface StationUseDao {
    @Query("SELECT * FROM station_use")
    List<StationUse> getAll();

    @Query("SELECT * FROM station_use WHERE station_id = :stationId")
    List<StationUse> getForStation(String stationId);

    @Query("SELECT * FROM station_use WHERE trip_id = :tripId")
    List<StationUse> getForTrip(String tripId);

    @Insert
    void insert(StationUse... use);

    @Insert
    void insert(List<StationUse> uses);

    @Update
    void update(StationUse... use);

    @Update
    void update(List<StationUse> uses);

    @Delete
    void delete(StationUse... use);

    @Delete
    void delete(List<StationUse> uses);
}
