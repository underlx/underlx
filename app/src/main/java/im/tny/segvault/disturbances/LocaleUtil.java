package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Created by Gabriel on 11/03/2018.
 */

public class LocaleUtil {
    public static Context updateResources(Context context) {
        return updateResources(context, true);
    }
    public static Context updateResources(Context context, boolean createConfigContext) {
        Locale locale = getCurrentLocale(context);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList localeList = new LocaleList(locale);
                LocaleList.setDefault(localeList);
                config.setLocales(localeList);
            }
            if(createConfigContext) {
                // sometimes doing this is not desirable
                // (e.g. in the getApplicationContext override, the resulting context cannot be cast to Application, which breaks stuff)
                context = context.createConfigurationContext(config);
            }
        } else {
            config.locale = locale;
        }
        res.updateConfiguration(config, res.getDisplayMetrics());
        return context;
    }

    private static java.util.Locale currentLocale = null;
    private static boolean localeNeedsReloading = true;

    private static java.util.Locale getCurrentLocale(Context context) {
        if (localeNeedsReloading) {
            SharedPreferences sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            String localeString = sharedPref.getString(PreferenceNames.Locale, "auto");
            if (localeString == null || localeString.equals("auto")) {
                currentLocale = Resources.getSystem().getConfiguration().locale;
            } else {
                String[] l = localeString.split("-");
                currentLocale = new java.util.Locale(l[0], l[1]);
            }
            localeNeedsReloading = false;
        }
        return currentLocale;
    }

    public static void flagLocaleNeedsReloading() {
        localeNeedsReloading = true;
    }
}
