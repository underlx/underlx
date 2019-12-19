package im.tny.segvault.disturbances.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {
                Feedback.class,
                NotificationRule.class
        },
        exportSchema = false,
        version = 1)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract FeedbackDao feedbackDao();
    public abstract NotificationRuleDao notificationRuleDao();
}
