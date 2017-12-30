package im.tny.segvault.disturbances.model;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.R;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

/**
 * Created by gabriel on 30/12/17.
 */

public class NotificationRule extends RealmObject {
    @PrimaryKey
    private String id = UUID.randomUUID().toString();

    @Required
    private String name = "";

    private boolean enabled = true;

    private long startTime = TimeUnit.HOURS.toMillis(10);

    private long endTime = TimeUnit.HOURS.toMillis(16);

    @Required
    private RealmList<Integer> weekDays = new RealmList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public RealmList<Integer> getWeekDays() {
        return weekDays;
    }

    public void setWeekDays(RealmList<Integer> weekDays) {
        this.weekDays = weekDays;
    }

    public String getDescription(Context context) {
        DateFormatSymbols symbols = new DateFormatSymbols();
        String[] dayNames = symbols.getShortWeekdays();
        ArrayList<String> enabledDays = new ArrayList<>();
        if (getWeekDays().size() == 0 || !isEnabled()) {
            return context.getString(R.string.act_notif_schedule_rule_disabled);
        }
        for (int day : getWeekDays()) {
            enabledDays.add(dayNames[day].substring(0, 3));
        }
        Formatter f = new Formatter();
        DateUtils.formatDateRange(context, f, getStartTime(), getStartTime(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON, Time.TIMEZONE_UTC);
        String startString = f.toString();
        f = new Formatter();
        DateUtils.formatDateRange(context, f, getEndTime(), getEndTime(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON, Time.TIMEZONE_UTC);
        String endString = f.toString();
        return String.format(context.getString(R.string.act_notif_schedule_rule_format),
                TextUtils.join(", ", enabledDays),
                startString,
                endString,
                getEndTime() >= TimeUnit.HOURS.toMillis(24) ? context.getString(R.string.act_notif_schedule_next_day) : "");
    }

    public boolean applies(Date at) {
        long now = at.getTime();
        for(int day : getWeekDays()) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            while(c.get(Calendar.DAY_OF_WEEK) != day) {
                c.add(Calendar.DAY_OF_YEAR, -1);
            }
            long todayPassed = now - c.getTimeInMillis();
            // todayPassed might actually be longer than 24 hours, which is what we want
            if(todayPassed >= getStartTime() && todayPassed < getEndTime()) {
                return true;
            }
        }
        return false;
    }
}
