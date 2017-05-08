package im.tny.segvault.disturbances;

import com.evernote.android.job.JobManager;

import io.realm.Realm;

/**
 * Created by gabriel on 4/14/17.
 */

public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        JobManager.create(this).addJobCreator(new MainService.LocationJobCreator());
        // Initialize Realm. Should only be done once when the application starts.
        Realm.init(this);
    }
}
