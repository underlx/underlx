package im.tny.segvault.disturbances.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

@Dao
public interface StationUseDao {
    @Query("SELECT * FROM station_use WHERE trip_id = :tripID ORDER BY `order`")
    List<StationUse> getOfTrip(String tripID);

    @Query("SELECT COUNT(*) FROM station_use WHERE entry_date <= :end AND leave_date >= :start")
    int countOverlapping(Date start, Date end);

    @Query("SELECT station_id FROM" +
            "(SELECT station_id, COUNT(station_id) AS c " +
            "FROM station_use " +
            "WHERE type = \"NETWORK_ENTRY\" OR type = \"NETWORK_EXIT\" OR type = \"INTERCHANGE\" " +
            "GROUP BY station_id ORDER BY c DESC, entry_date DESC LIMIT :limit) " +
            "WHERE c > 0")
    List<String> getMostUsedStations(int limit);

    @Query("SELECT COUNT(*) FROM station_use WHERE type = :type AND station_id = :stationID")
    int countStationUsesOfType(String stationID, StationUse.UseType type);

    @Query("SELECT COUNT(*) FROM station_use WHERE type = \"INTERCHANGE\" AND station_id = :stationID AND source_line = :sourceLineID")
    int countStationInterchangesToLine(String stationID, String sourceLineID);

    @Insert
    void insertAll(StationUse... stationUses);

    @Update
    void updateAll(StationUse... stationUses);

    @Delete
    void delete(StationUse stationUse);

    @Query("DELETE FROM station_use WHERE trip_id = :tripID")
    void deleteOfTrip(String tripID);

    @Query("DELETE FROM station_use")
    void deleteAll();

}
