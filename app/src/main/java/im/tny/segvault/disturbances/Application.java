package im.tny.segvault.disturbances;

import com.evernote.android.job.JobManager;

/**
 * Created by gabriel on 4/14/17.
 */

public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        JobManager.create(this).addJobCreator(new LocationService.LocationJobCreator());
    }
}
