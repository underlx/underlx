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
public interface StationDao {
    @Query("SELECT * FROM station")
    List<DbStation> getAll();

    @Query("SELECT * FROM station WHERE id = :id LIMIT 1")
    DbStation getById(String id);

    @Insert
    void insert(DbStation... station);

    @Insert
    void insert(List<DbStation> stations);

    @Update
    void update(DbStation... station);

    @Update
    void update(List<DbStation> stations);

    @Delete
    void delete(DbStation... station);

    @Delete
    void delete(List<DbStation> stations);
}
