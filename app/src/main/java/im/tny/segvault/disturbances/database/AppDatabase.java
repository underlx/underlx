package im.tny.segvault.disturbances.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {
                Feedback.class,
                NotificationRule.class,
                StationPreference.class
        },
        exportSchema = false,
        version = 2)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract FeedbackDao feedbackDao();
    public abstract NotificationRuleDao notificationRuleDao();
    public abstract StationPreferenceDao stationPreferenceDao();
}
