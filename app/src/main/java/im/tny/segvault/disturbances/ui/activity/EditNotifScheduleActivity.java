package im.tny.segvault.disturbances.ui.activity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment;

import java.lang.ref.WeakReference;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.NotificationRule;

public class EditNotifScheduleActivity extends TopActivity implements RadialTimePickerDialogFragment.OnTimeSetListener {
    private NotificationRule rule;
    private String ruleId;
    private boolean deleted = false;

    private SimpleAdapter adapter;

    private Map<String, String> startTimeItem;
    private Map<String, String> endTimeItem;
    private Map<String, String> weekDaysItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            ruleId = getIntent().getStringExtra(EXTRA_RULE_ID);
        } else {
            ruleId = savedInstanceState.getString(STATE_RULE_ID);
        }

        setContentView(R.layout.activity_edit_notif_schedule);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        List<Map<String, String>> data = new ArrayList<>();
        weekDaysItem = new HashMap<>(3);
        weekDaysItem.put("title", getString(R.string.act_edit_notif_schedule_days));
        weekDaysItem.put("desc", "");
        data.add(weekDaysItem);

        startTimeItem = new HashMap<>(3);
        startTimeItem.put("title", getString(R.string.act_edit_notif_schedule_start_time));
        startTimeItem.put("desc", "");
        data.add(startTimeItem);

        endTimeItem = new HashMap<>(3);
        endTimeItem.put("title", getString(R.string.act_edit_notif_schedule_end_time));
        endTimeItem.put("desc", "");
        data.add(endTimeItem);

        ListView listView = findViewById(R.id.list_view);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(EditNotifScheduleActivity.this);

                DateFormatSymbols symbols = new DateFormatSymbols();
                String[] dayNames = Arrays.copyOfRange(symbols.getWeekdays(), 1, 8);
                final boolean[] checkedDays = new boolean[7];
                for (int day : rule.weekDays) {
                    checkedDays[day - 1] = true;
                }

                builder.setMultiChoiceItems(dayNames, checkedDays, (dialog, which, isChecked) -> checkedDays[which] = isChecked);
                builder.setTitle(getString(R.string.act_edit_notif_schedule_days));
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    List<Integer> days = new ArrayList<>();
                    for (int i = 0; i < 7; i++) {
                        if (checkedDays[i]) {
                            days.add(i + 1);
                        }
                    }
                    rule.weekDays = new int[days.size()];
                    for (int i = 0; i < rule.weekDays.length; i++)
                        rule.weekDays[i] = days.get(i);
                    new StoreRuleTask(this, rule, true).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
                });

                builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {

                });

                builder.create().show();
            } else if (position == 1) {
                RadialTimePickerDialogFragment rtpd = new RadialTimePickerDialogFragment()
                        .setOnTimeSetListener(EditNotifScheduleActivity.this)
                        .setStartTime((int) TimeUnit.MILLISECONDS.toHours(rule.endTime) % 24, (int) TimeUnit.MILLISECONDS.toMinutes(rule.startTime) % 60)
                        .setDoneText(getString(android.R.string.ok))
                        .setCancelText(getString(android.R.string.cancel))
                        .setThemeDark();
                rtpd.show(getSupportFragmentManager(), TAG_START_TIME_PICKER);
            } else if (position == 2) {
                RadialTimePickerDialogFragment rtpd = new RadialTimePickerDialogFragment()
                        .setOnTimeSetListener(EditNotifScheduleActivity.this)
                        .setStartTime((int) TimeUnit.MILLISECONDS.toHours(rule.endTime) % 24, (int) TimeUnit.MILLISECONDS.toMinutes(rule.endTime) % 60)
                        .setDoneText(getString(android.R.string.ok))
                        .setCancelText(getString(android.R.string.cancel))
                        .setThemeDark();
                rtpd.show(getSupportFragmentManager(), TAG_END_TIME_PICKER);
            }
        });

        adapter = new SimpleAdapter(EditNotifScheduleActivity.this, data,
                android.R.layout.simple_list_item_2,
                new String[]{"title", "desc"},
                new int[]{android.R.id.text1, android.R.id.text2});
        listView.setAdapter(adapter);

        new LoadRuleTask(this).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_notif_schedule, menu);
        return true;
    }

    private void updateUI() {
        Formatter f = new Formatter(new StringBuilder(50), Locale.getDefault());
        DateUtils.formatDateRange(this, f, rule.startTime, rule.startTime, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON, Time.TIMEZONE_UTC);
        startTimeItem.put("desc", f.toString());
        f = new Formatter(new StringBuilder(50), Locale.getDefault());
        DateUtils.formatDateRange(this, f, rule.endTime, rule.endTime, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON, Time.TIMEZONE_UTC);
        endTimeItem.put("desc", f.toString());

        DateFormatSymbols symbols = new DateFormatSymbols();
        String[] dayNames = symbols.getShortWeekdays();
        ArrayList<String> enabledDays = new ArrayList<>();
        if (rule.weekDays.length == 0 || !rule.enabled) {
            weekDaysItem.put("desc", getString(R.string.act_edit_notif_schedule_days_none));
        } else {
            for (int day : rule.weekDays) {
                enabledDays.add(dayNames[day]);
            }
            weekDaysItem.put("desc", TextUtils.join(", ", enabledDays));
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        if (!deleted) {
            new StoreRuleTask(this, rule, false).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.menu_delete:
                deleted = true;
                new DeleteRuleTask(this, rule).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
        }
        return super.onOptionsItemSelected(item);
    }

    private static final String TAG_START_TIME_PICKER = "startTime";
    private static final String TAG_END_TIME_PICKER = "endTime";

    public static final String STATE_RULE_ID = "ruleId";

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_RULE_ID, ruleId);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public static final String EXTRA_RULE_ID = "im.tny.segvault.disturbances.extra.EditNotifScheduleActivity.ruleid";

    @Override
    public void onTimeSet(final RadialTimePickerDialogFragment dialog, final int hourOfDay, final int minute) {
        if (dialog == null || dialog.getTag() == null) {
            return;
        }
        switch (dialog.getTag()) {
            case TAG_START_TIME_PICKER:
                rule.startTime = TimeUnit.HOURS.toMillis(hourOfDay) + TimeUnit.MINUTES.toMillis(minute);
                break;
            case TAG_END_TIME_PICKER:
                rule.endTime = TimeUnit.HOURS.toMillis(hourOfDay) + TimeUnit.MINUTES.toMillis(minute);
                if (rule.endTime <= rule.startTime) {
                    rule.endTime = rule.endTime + TimeUnit.HOURS.toMillis(24);
                }
                break;
        }

        new StoreRuleTask(this, rule, true).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
    }

    private static class LoadRuleTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<EditNotifScheduleActivity> parentRef;

        LoadRuleTask(EditNotifScheduleActivity activity) {
            this.parentRef = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            EditNotifScheduleActivity parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent).getDB();

            if (parent.ruleId == null) {
                parent.rule = new NotificationRule();
                parent.rule.id = UUID.randomUUID().toString();
            } else {
                parent.rule = db.notificationRuleDao().get(parent.ruleId);
                if (parent.rule == null) {
                    parent.finish();
                    return false;
                }
            }

            parent.ruleId = parent.rule.id;

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                EditNotifScheduleActivity parent = parentRef.get();
                if (parent != null) {
                    parent.updateUI();
                }
            }
        }
    }

    private static class StoreRuleTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<EditNotifScheduleActivity> parentRef;

        private NotificationRule rule;
        private boolean updateUI;

        StoreRuleTask(EditNotifScheduleActivity activity, NotificationRule rule, boolean updateUI) {
            this.parentRef = new WeakReference<>(activity);
            this.rule = rule;
            this.updateUI = updateUI;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            EditNotifScheduleActivity parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent).getDB();
            db.notificationRuleDao().insertOrUpdateAll(rule);

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (updateUI && result) {
                EditNotifScheduleActivity parent = parentRef.get();
                if (parent != null) {
                    parent.updateUI();
                }
            }
        }
    }

    private static class DeleteRuleTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<EditNotifScheduleActivity> parentRef;

        private NotificationRule rule;

        DeleteRuleTask(EditNotifScheduleActivity activity, NotificationRule rule) {
            this.parentRef = new WeakReference<>(activity);
            this.rule = rule;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            EditNotifScheduleActivity parent = parentRef.get();
            if (parent == null) {
                return false;
            }

            AppDatabase db = Coordinator.get(parent).getDB();
            db.notificationRuleDao().delete(rule);

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                EditNotifScheduleActivity parent = parentRef.get();
                if (parent != null) {
                    parent.finish();
                }
            }
        }
    }
}
