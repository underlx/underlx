package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by Gabriel on 11/03/2018.
 */

public class LocaleUtil {
    public static void initializeLocale(AppCompatActivity activity) {
        initializeLocale(activity.getBaseContext());
    }

    public static void initializeLocale(Context context) {
        if(context == null) {
            return;
        }
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        java.util.Locale locale = getCurrentLocale(context);
        if(locale == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale; // config.setLocale(...) requires API 17
        }
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private static java.util.Locale currentLocale = null;
    private static boolean localeNeedsReloading = true;
    private static java.util.Locale getCurrentLocale(Context context) {
        if(localeNeedsReloading == true) {
            SharedPreferences sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            String localeString = sharedPref.getString(PreferenceNames.Locale, "auto");
            if(localeString.equals("auto")) {
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
