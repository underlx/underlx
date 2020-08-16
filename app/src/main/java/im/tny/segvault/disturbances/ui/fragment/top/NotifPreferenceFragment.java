package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.SeekBarPreference;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import com.takisoft.preferencex.PreferenceFragmentCompat;
import com.takisoft.preferencex.RingtonePreference;

import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import im.tny.segvault.disturbances.Announcement;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.LocaleUtil;
import im.tny.segvault.disturbances.MapManager;
import im.tny.segvault.disturbances.PreferenceNames;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.activity.MainActivity;
import im.tny.segvault.disturbances.ui.fragment.MainAddableFragment;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.disturbances.ui.util.CustomFAB;
import im.tny.segvault.subway.Line;

public class NotifPreferenceFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener, MainAddableFragment {
    private OnFragmentInteractionListener mListener;

    public NotifPreferenceFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
        LocaleUtil.updateResources(getContext());
        getPreferenceManager().setSharedPreferencesName("notifsettings");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        setPreferencesFromResource(R.xml.notif_settings, null);

        updatePreferences();
        bindPreferenceSummaryToValue(findPreference(PreferenceNames.NotifsRingtone));
        bindPreferenceSummaryToValue(findPreference(PreferenceNames.NotifsRegularizationRingtone));
        bindPreferenceSummaryToValue(findPreference(PreferenceNames.NotifsAnnouncementRingtone));
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
        CustomFAB fab = getActivity().findViewById(R.id.fab);
        fab.hide();
        SwipeRefreshLayout srl = getActivity().findViewById(R.id.swipe_container);
        srl.setEnabled(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_MAIN_SERVICE_BOUND);
        filter.addAction(MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(view.getContext());
        bm.registerReceiver(mBroadcastReceiver, filter);
        return view;
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
        linesPreference = findPreference(PreferenceNames.NotifsLines);

        List<CharSequence> lineNames = new ArrayList<>();
        List<CharSequence> lineIDs = new ArrayList<>();
        List<Line> lines = Coordinator.get(getContext()).getMapManager().getAllLines();
        Collections.sort(lines, (line, t1) -> Integer.valueOf(line.getOrder()).compareTo(t1.getOrder()));
        for (Line l : lines) {
            lineNames.add(Util.getLineNames(getContext(), l)[0]);
            lineIDs.add(l.getId());
        }

        linesPreference.setEntries(lineNames.toArray(new CharSequence[lineNames.size()]));
        linesPreference.setEntryValues(lineIDs.toArray(new CharSequence[lineIDs.size()]));
        updateLinesPreferenceSummary(linesPreference, linesPreference.getValues());

        linesPreference.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    MultiSelectListPreference multilistPreference = (MultiSelectListPreference) preference;
                    @SuppressWarnings("unchecked")
                    Set<String> values = (Set<String>) newValue;
                    updateLinesPreferenceSummary(multilistPreference, values);
                    return true;
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

        Preference notifsDistServiceResumed = findPreference(PreferenceNames.NotifsServiceResumed);
        Preference notifsDistRingtone = findPreference(PreferenceNames.NotifsRingtone);
        Preference notifsDistVibrate = findPreference(PreferenceNames.NotifsVibrate);
        notifsDistServiceResumed.setEnabled(values.size() != 0);
        notifsDistRingtone.setEnabled(values.size() != 0);
        notifsDistVibrate.setEnabled(values.size() != 0);
    }

    private void updateSourcesPreference() {
        MultiSelectListPreference sourcesPreference;
        sourcesPreference = (MultiSelectListPreference) findPreference(PreferenceNames.AnnouncementSources);

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
                (preference, newValue) -> {
                    MultiSelectListPreference multilistPreference = (MultiSelectListPreference) preference;
                    @SuppressWarnings("unchecked")
                    Set<String> values = (Set<String>) newValue;
                    updateSourcesPreferenceSummary(multilistPreference, values);
                    return true;
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

        Preference notifsAnnRingtone = findPreference(PreferenceNames.NotifsAnnouncementRingtone);
        Preference notifsAnnVibrate = findPreference(PreferenceNames.NotifsAnnouncementVibrate);
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
        if (key.equals(PreferenceNames.NotifsLines) || key.equals(PreferenceNames.AnnouncementSources)) {
            Coordinator.get(getContext()).reloadFCMsubscriptions();
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
        return R.id.nav_notif;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_notif";
    }

    public interface OnFragmentInteractionListener extends TopFragment.OnInteractionListener {
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MainActivity.ACTION_MAIN_SERVICE_BOUND:
                case MapManager.ACTION_UPDATE_TOPOLOGY_FINISHED:
                    updatePreferences();
                    break;
            }
        }
    };

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
        String stringValue = value.toString();

        if (preference instanceof ListPreference) {
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
    };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        final String key = preference.getKey();
        if (preference instanceof MultiSelectListPreference) {
            Set<String> summary = ((MultiSelectListPreference) preference).getValues();
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