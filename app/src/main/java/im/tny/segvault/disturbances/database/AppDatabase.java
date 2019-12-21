package im.tny.segvault.disturbances.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(
        entities = {
                Feedback.class,
                NotificationRule.class,
                StationPreference.class,
                Trip.class,
                StationUse.class,
        },
        exportSchema = false,
        version = 3)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract FeedbackDao feedbackDao();

    public abstract NotificationRuleDao notificationRuleDao();

    public abstract StationPreferenceDao stationPreferenceDao();

    public abstract TripDao tripDao();

    public abstract StationUseDao stationUseDao();
}
