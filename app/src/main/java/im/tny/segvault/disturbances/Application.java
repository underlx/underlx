package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.res.Configuration;

import com.evernote.android.job.JobManager;

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
}
