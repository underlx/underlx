package im.tny.segvault.disturbances.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FeedbackDao {
    @Query("SELECT * FROM feedback")
    List<Feedback> getAll();

    @Query("SELECT * FROM feedback WHERE synced = 0")
    List<Feedback> getUnsynced();

    @Query("SELECT * FROM feedback WHERE synced = 0")
    LiveData<List<Feedback>> getUnsyncedObservable();

    @Insert
    void insertAll(Feedback... feedbacks);

    @Update
    void updateAll(Feedback... feedbacks);

    @Delete
    void delete(Feedback feedback);

}
