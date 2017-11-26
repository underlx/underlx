package im.tny.segvault.disturbances;

import android.content.Context;

import com.evernote.android.job.JobManager;

import java.util.Date;

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
        initRealm(this);
        JobManager.create(this).addJobCreator(new MainService.LocationJobCreator());
    }

    public static void initRealm(Context context) {
        // Initialize Realm. Should only be done once when the application starts.
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .schemaVersion(6) // Must be bumped when the schema changes
                .migration(new MyMigration())
                .build();
        Realm.setDefaultConfiguration(config);
    }

    static class MyMigration implements RealmMigration {
        @Override
        public void migrate(DynamicRealm realm, long oldVersion, long newVersion) {

            // DynamicRealm exposes an editable schema
            RealmSchema schema = realm.getSchema();

            if (oldVersion == 0) {
                schema.get("Trip")
                        .addField("synced", boolean.class);
                oldVersion++;
            }

            if (oldVersion == 1) {
                schema.get("StationUse")
                        .addField("manualEntry", boolean.class);
                oldVersion++;
            }

            if (oldVersion == 2) {
                schema.get("Trip")
                        .addField("userConfirmed", boolean.class);
                oldVersion++;
            }

            if (oldVersion == 3) {
                schema.get("Trip")
                        .addField("submitted", boolean.class);
                oldVersion++;
            }

            if (oldVersion == 4) {
                schema.create("Feedback")
                        .addField("id", String.class)
                        .addPrimaryKey("id")
                        .addField("synced", boolean.class)
                        .addField("timestamp", Date.class)
                        .setRequired("timestamp", true)
                        .addField("type", String.class)
                        .setRequired("type", true)
                        .addField("contents", String.class)
                        .setRequired("contents", true);
                oldVersion++;
            }

            if (oldVersion == 5) {
                schema.get("Trip")
                        .addField("syncFailures", int.class);
                oldVersion++;
            }
        }
    }
}
