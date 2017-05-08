package im.tny.segvault.disturbances;

import android.app.Application;

import com.facebook.stetho.Stetho;
import com.uphyca.stetho_realm.RealmInspectorModulesProvider;

/**
 * Created by gabriel on 5/8/17.
 */

public class StethoUtils {
    public static void install(Application application) {
        Stetho.initialize(
                Stetho.newInitializerBuilder(application)
                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(application))
                        .enableWebKitInspector(RealmInspectorModulesProvider.builder(application).build())
                        .build());
    }
}
