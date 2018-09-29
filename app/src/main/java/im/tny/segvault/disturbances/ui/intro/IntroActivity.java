package im.tny.segvault.disturbances.ui.intro;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.github.paolorotolo.appintro.AppIntro2;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import im.tny.segvault.disturbances.LocaleUtil;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.activity.MainActivity;

/**
 * Created by Gabriel on 27/07/2017.
 */

public class IntroActivity extends AppIntro2 implements
        DisturbancesIntroSlide.OnFragmentInteractionListener,
        FinishIntroSlide.OnFragmentInteractionListener {

    private MainService mainService;
    private boolean locBound = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleUtil.initializeLocale(this);

        Object conn = getLastCustomNonConfigurationInstance();
        if (conn != null) {
            // have the service connection survive through activity configuration changes
            // (e.g. screen orientation changes)
            mConnection = (LocServiceConnection) conn;
            mainService = mConnection.getBinder().getService();
            locBound = true;
        } else if (!locBound) {
            getApplicationContext().bindService(new Intent(getApplicationContext(), MainService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

        SliderPage firstPage = new SliderPage();

        firstPage.setTitle(getString(R.string.intro_welcome_title));
        firstPage.setDescription(getString(R.string.intro_welcome_desc));
        firstPage.setImageDrawable(R.drawable.logo);
        firstPage.setBgColor(ContextCompat.getColor(this, R.color.colorPrimaryLight));
        firstPage.setTitleColor(Color.WHITE);
        firstPage.setDescColor(Color.WHITE);

        addSlide(AppIntroFragment.newInstance(firstPage));

        addSlide(DisturbancesIntroSlide.newInstance());

        addSlide(AnnouncementsIntroSlide.newInstance());

        SliderPage thirdPage = new SliderPage();

        thirdPage.setTitle(getString(R.string.intro_location_title));
        thirdPage.setDescription(getString(R.string.intro_location_desc));
        thirdPage.setImageDrawable(R.drawable.ic_compass_intro);
        thirdPage.setBgColor(Color.parseColor("#ED6A5A"));
        thirdPage.setTitleColor(Color.WHITE);
        thirdPage.setDescColor(Color.WHITE);

        addSlide(AppIntroFragment.newInstance(thirdPage));

        addSlide(FinishIntroSlide.newInstance());

        showStatusBar(true);

        showSkipButton(false);

        // Edit the color of the nav bar on Lollipop+ devices
        setNavBarColor(R.color.colorPrimaryDark);

        setZoomAnimation();
    }

    private boolean requestedLocation = false;

    @Override
    public void onSlideChanged(Fragment oldFragment, Fragment newFragment) {
        if (oldFragment instanceof AppIntroFragment) {
            AppIntroFragment aif = (AppIntroFragment) oldFragment;
            if (aif.getArguments().getInt("drawable") == R.drawable.ic_compass_intro && !requestedLocation) {
                Log.d("osc", "Requesting permission");
                requestedLocation = true;
                SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
                boolean locationEnabled = sharedPref.getBoolean(PreferenceNames.LocationEnable, true);
                if (locationEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MainActivity.PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
                }
            }
        }
        setProgressButtonEnabled(!(newFragment instanceof FinishIntroSlide));
    }

    @Override
    public void onDonePressed(Fragment fragment) {
        // Do something when users tap on Done button.
        SharedPreferences sharedPref = getSharedPreferences("settings", MODE_PRIVATE);
        SharedPreferences.Editor e = sharedPref.edit();
        e.putBoolean("fuse_first_run", false);
        e.apply();
        final Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_FROM_INTRO, true);
        startActivity(i);
        finish();
    }

    private LocServiceConnection mConnection = new LocServiceConnection();

    @Override
    public MainService getMainService() {
        return mainService;
    }

    class LocServiceConnection implements ServiceConnection {
        MainService.LocalBinder binder;

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            binder = (MainService.LocalBinder) service;
            mainService = binder.getService();
            locBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            locBound = false;
        }

        public MainService.LocalBinder getBinder() {
            return binder;
        }
    }
}
