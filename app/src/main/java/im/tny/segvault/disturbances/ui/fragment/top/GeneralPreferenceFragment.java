package im.tny.segvault.disturbances.ui.fragment.top;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import im.tny.segvault.disturbances.LocaleUtil;
import im.tny.segvault.disturbances.MainService;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.fragment.MainAddableFragment;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.disturbances.ui.util.CustomFAB;

public class GeneralPreferenceFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener, MainAddableFragment {
    private OnFragmentInteractionListener mListener;

    public GeneralPreferenceFragment() {
        // Required empty public constructor
    }

    public static GeneralPreferenceFragment newInstance() {
        GeneralPreferenceFragment fragment = new GeneralPreferenceFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        getActivity().setTitle(getString(R.string.frag_settings_title));
        if (mListener != null) {
            mListener.checkNavigationDrawerItem(R.id.nav_settings);
        }
        CustomFAB fab = getActivity().findViewById(R.id.fab);
        fab.hide();
        SwipeRefreshLayout srl = getActivity().findViewById(R.id.swipe_container);
        srl.setEnabled(false);
        return view;
    }

    public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
        LocaleUtil.updateResources(getContext());
        getPreferenceManager().setSharedPreferencesName("settings");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        setPreferencesFromResource(R.xml.settings, null);

        updateLanguagePreferenceSummary();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        // this solves the "preference screen background is a darker gray" problem:
        // https://github.com/consp1racy/android-support-preference/issues/22
        super.onViewCreated(view, savedInstanceState);

        final RecyclerView listView = getListView();

        // We don't want this. The children are still focusable.
        listView.setFocusable(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d("GeneralPreferenceFrag", "onSharedPreferenceChanged " + key);
        if (key.equals(PreferenceNames.LocationEnable)) {
            boolean locationEnabled = sharedPreferences.getBoolean(PreferenceNames.LocationEnable, true);
            if (locationEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MainActivity.PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
            }
        } else if (key.equals(PreferenceNames.Locale)) {
            updateLanguagePreferenceSummary();
            LocaleUtil.flagLocaleNeedsReloading();
            getActivity().recreate();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFragmentInteractionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void updateLanguagePreferenceSummary() {
        ListPreference languagePreference = (ListPreference) findPreference(PreferenceNames.Locale);
        languagePreference.setSummary(languagePreference.getEntry());
    }

    @Override
    public boolean needsTopology() {
        return false;
    }

    @Override
    public boolean isScrollable() {
        return true;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_settings;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_settings";
    }

    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
        MainService getMainService();
    }
}