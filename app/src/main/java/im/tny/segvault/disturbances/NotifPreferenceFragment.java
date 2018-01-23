package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.XpPreferenceFragment;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.xpece.android.support.preference.ListPreference;
import net.xpece.android.support.preference.MultiSelectListPreference;
import net.xpece.android.support.preference.RingtonePreference;
import net.xpece.android.support.preference.SeekBarPreference;
import net.xpece.android.support.preference.SharedPreferencesCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

public class NotifPreferenceFragment extends XpPreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private OnFragmentInteractionListener mListener;

    public NotifPreferenceFragment() {
        // Required empty public constructor
    }

    public static NotifPreferenceFragment newInstance() {
        NotifPreferenceFragment fragment = new NotifPreferenceFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        getActivity().setTitle(getString(R.string.frag_notif_title));
        if (mListener != null) {
            mListener.checkNavigationDrawerItem(R.id.nav_notif);
        }
        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.hide();
        SwipeRefreshLayout srl = (SwipeRefreshLayout) getActivity().findViewById(R.id.swipe_container);
        srl.setEnabled(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MainService.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(view.getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);
        return view;
    }

    @Override
    public void onCreatePreferences2(final Bundle savedInstanceState, final String rootKey) {
        TopActivity.initializeLocale(getContext());
        getPreferenceManager().setSharedPreferencesName("notifsettings");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        setPreferencesFromResource(R.xml.notif_settings, null);

        updatePreferences();
        bindPreferenceSummaryToValue(findPreference("pref_notifs_ringtone"));
        bindPreferenceSummaryToValue(findPreference("pref_notifs_regularization_ringtone"));
        bindPreferenceSummaryToValue(findPreference("pref_notifs_announcement_ringtone"));
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

    private void updatePreferences() {
        updateLinesPreference();
        updateSourcesPreference();
    }

    private void updateLinesPreference() {
        MultiSelectListPreference linesPreference;
        linesPreference = (MultiSelectListPreference) findPreference("pref_notifs_lines");

        List<CharSequence> lineNames = new ArrayList<>();
        List<CharSequence> lineIDs = new ArrayList<>();
        List<Line> lines = new LinkedList<>();
        if (mListener != null && mListener.getMainService() != null) {
            for (Network n : mListener.getMainService().getNetworks()) {
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
        updateLinesPreferenceSummary(linesPreference, linesPreference.getValues());

        linesPreference.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        MultiSelectListPreference multilistPreference = (MultiSelectListPreference) preference;
                        @SuppressWarnings("unchecked")
                        Set<String> values = (Set<String>) newValue;
                        updateLinesPreferenceSummary(multilistPreference, values);
                        return true;
                    }
                });
    }

    private void updateLinesPreferenceSummary(MultiSelectListPreference preference, Set<String> values) {
        List<String> sortedValues = new ArrayList<String>(values);
        Collections.sort(sortedValues);

        if (!values.isEmpty()) {
            CharSequence summary = getSelectedEntries(sortedValues, preference).toString();
            summary = summary.subSequence(1, summary.length() - 1);
            preference.setSummary(String.format(getString(R.string.frag_notif_summary_lines), summary));
        } else {
            preference.setSummary(getString(R.string.frag_notif_summary_no_lines));
        }

        Preference notifsDistServiceResumed = findPreference("pref_notifs_service_resumed");
        Preference notifsDistRingtone = findPreference("pref_notifs_ringtone");
        Preference notifsDistVibrate = findPreference("pref_notifs_vibrate");
        notifsDistServiceResumed.setEnabled(values.size() != 0);
        notifsDistRingtone.setEnabled(values.size() != 0);
        notifsDistVibrate.setEnabled(values.size() != 0);
    }

    private void updateSourcesPreference() {
        MultiSelectListPreference sourcesPreference;
        sourcesPreference = (MultiSelectListPreference) findPreference("pref_notifs_announcement_sources");

        List<CharSequence> entryValues = new ArrayList<>();
        List<CharSequence> entries = new ArrayList<>();
        for (Announcement.Source source : Announcement.getSources()) {
            entryValues.add(source.id);
            entries.add(getString(source.nameResourceId));
        }
        sourcesPreference.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
        sourcesPreference.setEntries(entries.toArray(new CharSequence[entries.size()]));
        updateSourcesPreferenceSummary(sourcesPreference, sourcesPreference.getValues());

        sourcesPreference.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        MultiSelectListPreference multilistPreference = (MultiSelectListPreference) preference;
                        @SuppressWarnings("unchecked")
                        Set<String> values = (Set<String>) newValue;
                        updateSourcesPreferenceSummary(multilistPreference, values);
                        return true;
                    }
                });
    }

    private void updateSourcesPreferenceSummary(MultiSelectListPreference preference, Set<String> values) {
        List<String> sortedValues = new ArrayList<String>(values);
        Collections.sort(sortedValues);

        if (!values.isEmpty()) {
            CharSequence summary = getSelectedEntries(sortedValues, preference).toString();
            summary = summary.subSequence(1, summary.length() - 1);
            preference.setSummary(String.format(getString(R.string.frag_notif_summary_sources), summary));
        } else {
            preference.setSummary(getString(R.string.frag_notif_summary_no_sources));
        }

        Preference notifsAnnRingtone = findPreference("pref_notifs_announcement_ringtone");
        Preference notifsAnnVibrate = findPreference("pref_notifs_announcement_vibrate");
        notifsAnnRingtone.setEnabled(values.size() != 0);
        notifsAnnVibrate.setEnabled(values.size() != 0);
    }

    private List<CharSequence> getSelectedEntries(List<String> values, MultiSelectListPreference multilistPreference) {
        List<CharSequence> labels = new ArrayList<>();
        for (String value : values) {
            int index = multilistPreference.findIndexOfValue(value);
            if (index >= 0) {
                labels.add(multilistPreference.getEntries()[index]);
            }
        }
        return labels;
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
        updatePreferences();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d("NotifPreferenceFrag", "onSharedPreferenceChanged " + key);
        if (key.equals("pref_notifs_lines") || key.equals("pref_notifs_announcement_sources")) {
            if (mListener != null && mListener.getMainService() != null) {
                mListener.getMainService().reloadFCMsubscriptions();
            }
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

    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
        MainService getMainService();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MainService.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    updatePreferences();
                    break;
            }
        }
    };

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof SeekBarPreference) {
                SeekBarPreference pref = (SeekBarPreference) preference;
                int progress = (int) value;
                pref.setInfo(progress + "%");
            } else if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else if (preference instanceof MultiSelectListPreference) {
                String summary = stringValue.trim().substring(1, stringValue.length() - 1); // strip []
                preference.setSummary(summary);
            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.frag_notif_summary_silent);
                } else {
                    final Uri selectedUri = Uri.parse(stringValue);
                    try {
                        final Ringtone ringtone = RingtoneManager.getRingtone(
                                preference.getContext(), selectedUri);
                        if (ringtone == null) {
                            // Clear the summary if there was a lookup error, i.e. does not exist.
                            preference.setSummary(null);
                        } else {
                            // Set the summary to reflect the new ringtone display name.
                            final String name = ringtone.getTitle(preference.getContext());
                            preference.setSummary(name);
                        }
                    } catch (SecurityException ex) {
                        // The user has selected a ringtone from external storage
                        // and then revoked READ_EXTERNAL_STORAGE permission.
                        // We have no way of guessing the ringtone title.
                        // We'd have to store the title of selected ringtone in prefs as well.
                        preference.setSummary("???");
                    }
                }

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        final String key = preference.getKey();
        if (preference instanceof MultiSelectListPreference) {
            Set<String> summary = SharedPreferencesCompat.getStringSet(
                    PreferenceManager.getDefaultSharedPreferences(preference.getContext()),
                    key, new HashSet<String>());
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, summary);
        } else if (preference instanceof SeekBarPreference) {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, ((SeekBarPreference) preference).getValue());
        } else {
            String value = PreferenceManager
                    .getDefaultSharedPreferences(preference.getContext())
                    .getString(key, "");
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, value);
        }
    }

}