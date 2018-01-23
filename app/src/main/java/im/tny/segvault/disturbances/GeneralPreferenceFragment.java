package im.tny.segvault.disturbances;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import net.xpece.android.support.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.XpPreferenceFragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class GeneralPreferenceFragment extends XpPreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
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
        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.hide();
        SwipeRefreshLayout srl = (SwipeRefreshLayout) getActivity().findViewById(R.id.swipe_container);
        srl.setEnabled(false);
        return view;
    }

    public void onCreatePreferences2(final Bundle savedInstanceState, final String rootKey) {
        TopActivity.initializeLocale(getContext());
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
        if (key.equals("pref_location_enable")) {
            boolean locationEnabled = sharedPreferences.getBoolean("pref_location_enable", true);
            if (locationEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MainActivity.PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
            }
        } else if (key.equals("pref_locale")) {
            updateLanguagePreferenceSummary();
            TopActivity.flagLocaleNeedsReloading();
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
        ListPreference languagePreference = (ListPreference) findPreference("pref_locale");
        languagePreference.setSummary(languagePreference.getEntry());
    }

    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
        MainService getMainService();
    }
}