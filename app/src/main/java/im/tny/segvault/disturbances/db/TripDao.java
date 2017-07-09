package im.tny.segvault.disturbances.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import java.util.List;

/**
 * Created by gabriel on 7/9/17.
 */

@Dao
public abstract class TripDao {
    @Query("SELECT * from trip")
    abstract public List<Trip> getAll();

    @Query("SELECT * FROM trip WHERE id = :id LIMIT 1")
    abstract public Trip getById(String id);
}
