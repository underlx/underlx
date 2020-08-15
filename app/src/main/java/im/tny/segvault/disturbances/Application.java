package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.res.Configuration;

import com.evernote.android.job.JobManager;

import java.util.Date;

import io.realm.DynamicRealm;
import io.realm.RealmMigration;
import io.realm.RealmSchema;

/**
 * Created by gabriel on 4/14/17.
 */

public class Application extends androidx.multidex.MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        LocaleUtil.updateResources(this);
        StethoUtils.install(this);
        JobManager.create(this).addJobCreator(new OurJobCreator());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtil.updateResources(base));
    }

    @Override
    public Context getApplicationContext() {
        return LocaleUtil.updateResources(super.getApplicationContext(), false);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleUtil.updateResources(this);
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

            if (oldVersion == 6) {
                schema.create("NotificationRule")
                        .addField("name", String.class)
                        .setRequired("name", true)
                        .addField("enabled", boolean.class)
                        .addField("startTime", long.class)
                        .addField("endTime", long.class)
                        .addRealmListField("weekDays", Integer.class)
                        .setRequired("weekDays", true);
                oldVersion++;
            }

            if (oldVersion == 7) {
                schema.get("NotificationRule")
                        .addField("id", String.class)
                        .addPrimaryKey("id");
                oldVersion++;
            }
        }
    }

}
