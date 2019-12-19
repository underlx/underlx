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
public interface NotificationRuleDao {
    @Query("SELECT * FROM notification_rule")
    List<NotificationRule> getAll();

    @Query("SELECT * FROM notification_rule")
    LiveData<List<NotificationRule>> getAllLive();

    @Query("SELECT * FROM notification_rule WHERE id = :id")
    NotificationRule get(String id);

    @Insert
    void insertAll(NotificationRule... rules);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAll(NotificationRule... rules);

    @Update
    void updateAll(NotificationRule... rules);

    @Delete
    void delete(NotificationRule rule);

}
