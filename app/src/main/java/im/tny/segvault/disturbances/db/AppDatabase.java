package im.tny.segvault.disturbances.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;

/**
 * Created by gabriel on 7/9/17.
 */

@Database(entities = {DbStation.class, TripEntity.class, StationUse.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract StationDao stationDao();
    public abstract StationUseDao stationUseDao();
}
