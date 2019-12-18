package im.tny.segvault.disturbances.database;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.R;

@Entity(tableName = "notification_rule")
public class NotificationRule {
    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    public String name = "";

    public boolean enabled;

    public long startTime;

    public long endTime;

    @NonNull
    public int[] weekDays = new int[0];

    public String getDescription(Context context) {
        DateFormatSymbols symbols = new DateFormatSymbols();
        String[] dayNames = symbols.getShortWeekdays();
        ArrayList<String> enabledDays = new ArrayList<>();
        if (weekDays.length == 0 || !enabled) {
            return context.getString(R.string.act_notif_schedule_rule_disabled);
        }
        for (int day : weekDays) {
            enabledDays.add(dayNames[day].substring(0, 3));
        }
        Formatter f = new Formatter();
        DateUtils.formatDateRange(context, f, startTime, startTime, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON, Time.TIMEZONE_UTC);
        String startString = f.toString();
        f = new Formatter();
        DateUtils.formatDateRange(context, f, endTime, endTime, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON, Time.TIMEZONE_UTC);
        String endString = f.toString();
        return String.format(context.getString(R.string.act_notif_schedule_rule_format),
                TextUtils.join(", ", enabledDays),
                startString,
                endString,
                endTime >= TimeUnit.HOURS.toMillis(24) ? context.getString(R.string.act_notif_schedule_next_day) : "");
    }

    public boolean applies(Date at) {
        long now = at.getTime();
        for(int day : weekDays) {
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
            if(todayPassed >= startTime && todayPassed < endTime) {
                return true;
            }
        }
        return false;
    }
}
