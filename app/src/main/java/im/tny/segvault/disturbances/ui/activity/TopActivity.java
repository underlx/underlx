package im.tny.segvault.disturbances.ui.activity;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import im.tny.segvault.disturbances.LocaleUtil;

/**
 * Created by Gabriel on 23/01/2018.
 */

public abstract class TopActivity extends AppCompatActivity {
    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocaleUtil.initializeLocale(this);
    }


}
