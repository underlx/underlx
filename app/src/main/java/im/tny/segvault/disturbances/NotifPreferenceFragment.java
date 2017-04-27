package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import rikka.materialpreference.MultiSelectListPreference;
import rikka.materialpreference.Preference;
import rikka.materialpreference.PreferenceFragment;

/**
 * PreferenceFragment example include set DropDownPreference entries programmatically
 */
public class NotifPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private OnFragmentInteractionListener mListener;

    public NotifPreferenceFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (mListener != null) {
            mListener.setActionBarTitle(getString(R.string.frag_notif_title));
            mListener.checkNavigationDrawerItem(R.id.nav_notif);
        }
        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.hide();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_LOCATION_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(view.getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);
        return view;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        getPreferenceManager().setDefaultPackages(new String[]{"im.tny.segvault.disturbances."});

        getPreferenceManager().setSharedPreferencesName("notifsettings");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        setPreferencesFromResource(R.xml.notif_settings, null);

        updateLinesPreference();
    }

    private void updateLinesPreference() {
        Log.d("updateLinesPreference", "enter");
        MultiSelectListPreference linesPreference;
        linesPreference = (MultiSelectListPreference) findPreference("pref_notifs_lines");

        List<CharSequence> lineNames = new ArrayList<>();
        List<CharSequence> lineIDs = new ArrayList<>();
        List<Line> lines = new LinkedList<>();
        if (mListener != null && mListener.getLocationService() != null) {
            for (Network n : mListener.getLocationService().getNetworks()) {
                Log.d("updateLinesPreference", "addAll");
                lines.addAll(n.getLines());
            }
            Collections.sort(lines, new Comparator<Line>() {
                @Override
                public int compare(Line line, Line t1) {
                    return line.getName().compareTo(t1.getName());
                }
            });
            for (Line l : lines) {
                lineNames.add(l.getName());
                lineIDs.add(l.getId());
            }
        }

        linesPreference.setEntries(lineNames.toArray(new CharSequence[lineNames.size()]));
        linesPreference.setEntryValues(lineIDs.toArray(new CharSequence[lineIDs.size()]));
        updateMultiSelectListPreferenceSummary(linesPreference, linesPreference.getValues());

        linesPreference.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        MultiSelectListPreference multilistPreference = (MultiSelectListPreference) preference;
                        @SuppressWarnings("unchecked")
                        Set<String> values = (Set<String>) newValue;
                        updateMultiSelectListPreferenceSummary(multilistPreference, values);
                        return true;
                    }
                });
    }

    private void updateMultiSelectListPreferenceSummary(MultiSelectListPreference preference, Set<String> values) {
        List<String> sortedValues = new ArrayList<String>(values);
        Collections.sort(sortedValues);

        if (!values.isEmpty()) {
            CharSequence summary = getSelectedEntries(sortedValues, preference).toString();
            summary = summary.subSequence(1, summary.length() - 1);
            preference.setSummary(String.format(getString(R.string.frag_notif_summary_lines), summary));
        } else {
            preference.setSummary(getString(R.string.frag_notif_summary_no_lines));
        }
    }

    private List<CharSequence> getSelectedEntries(List<String> values, MultiSelectListPreference multilistPreference) {
        List<CharSequence> labels = new ArrayList<>();
        for (String value : values) {
            int index = multilistPreference.findIndexOfValue(value);
            if(index >= 0) {
                labels.add(multilistPreference.getEntries()[index]);
            }
        }
        return labels;
    }


    @Override
    public DividerDecoration onCreateItemDecoration() {
        return new CategoryDivideDividerDecoration();
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
        updateLinesPreference();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d("MainActivityFragment", "onSharedPreferenceChanged " + key);
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

    public interface OnFragmentInteractionListener extends OnTopFragmentInteractionListener {
        MainService getLocationService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_LOCATION_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    updateLinesPreference();
                    break;
            }
        }
    };
}