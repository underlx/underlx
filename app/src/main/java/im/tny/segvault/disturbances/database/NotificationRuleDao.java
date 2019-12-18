package im.tny.segvault.disturbances.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NotificationRuleDao {
    @Query("SELECT * FROM notification_rule")
    List<NotificationRule> getAll();

    @Insert
    void insertAll(NotificationRule... rules);

    @Update
    void updateAll(NotificationRule... rules);

    @Delete
    void delete(NotificationRule rule);

}
