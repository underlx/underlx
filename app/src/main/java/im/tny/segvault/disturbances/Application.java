package im.tny.segvault.disturbances;

import com.evernote.android.job.JobManager;

import io.realm.DynamicRealm;
import io.realm.FieldAttribute;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmMigration;
import io.realm.RealmSchema;

/**
 * Created by gabriel on 4/14/17.
 */

public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        StethoUtils.install(this);
        JobManager.create(this).addJobCreator(new MainService.LocationJobCreator());
        // Initialize Realm. Should only be done once when the application starts.
        Realm.init(this);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .schemaVersion(1) // Must be bumped when the schema changes
                .migration(new MyMigration())
                .build();
        Realm.setDefaultConfiguration(config);
    }

    class MyMigration implements RealmMigration {
        @Override
        public void migrate(DynamicRealm realm, long oldVersion, long newVersion) {

            // DynamicRealm exposes an editable schema
            RealmSchema schema = realm.getSchema();

            if (oldVersion == 0) {
                schema.get("Trip")
                        .addField("synced", boolean.class);
                oldVersion++;
            }
        }
    }
}
