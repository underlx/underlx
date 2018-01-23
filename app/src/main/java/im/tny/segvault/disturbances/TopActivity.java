package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import java.util.Locale;

/**
 * Created by Gabriel on 23/01/2018.
 */

public abstract class TopActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeLocale(this);
    }

    static void initializeLocale(AppCompatActivity activity) {
        initializeLocale(activity.getBaseContext());
    }

    static void initializeLocale(Context context) {
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        //Locale locale = new Locale("fr", "FR"); // get preferred locale from shared preferences or something
        Locale locale = getCurrentLocale(context);
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

    private static Locale currentLocale = null;
    private static boolean localeNeedsReloading = true;
    private static Locale getCurrentLocale(Context context) {
        if(localeNeedsReloading == true) {
            SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
            String localeString = sharedPref.getString("pref_locale", "auto");
            if(localeString.equals("auto")) {
               currentLocale = null;
            } else {
                String[] l = localeString.split("-");
                currentLocale = new Locale(l[0], l[1]);
            }
            localeNeedsReloading = false;
        }
        return currentLocale;
    }

    public static void flagLocaleNeedsReloading() {
        localeNeedsReloading = true;
    }
}
