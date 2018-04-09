package im.tny.segvault.subway;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * Created by Gabriel on 28/12/2017.
 */

public class Schedule implements Serializable {
    public boolean holiday;
    public int day;
    public boolean open;
    public long openTime;
    public long duration;

    public Schedule(boolean holiday, int day, boolean open, long openTime, long duration) {
        this.holiday = holiday;
        this.day = day;
        this.open = open;
        this.openTime = openTime;
        this.duration = duration;
    }

    public boolean compare(Schedule s2) {
        return (!open && !s2.open) ||
                (open == s2.open && openTime == s2.openTime && duration == s2.duration);
    }

    public static boolean isOpen(Network network, Map<Integer, Schedule> schedule, Date at) {
        long now = at.getTime();
        Calendar c = Calendar.getInstance(network.getTimezone());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long todayPassed = now - c.getTimeInMillis();
        Schedule todaySchedule = getScheduleForDay(network, schedule, c);
        c.add(Calendar.DATE, -1);
        long yesterdayPassed = now - c.getTimeInMillis();
        Schedule yesterdaySchedule = getScheduleForDay(network, schedule, c);

        if(todaySchedule == null || yesterdaySchedule == null) {
            return true;
        }

        boolean openToday = todaySchedule.open && todayPassed >= todaySchedule.openTime && todayPassed < todaySchedule.openTime + todaySchedule.duration;
        boolean openYesterday = yesterdaySchedule.open && yesterdayPassed >= yesterdaySchedule.openTime && yesterdayPassed < yesterdaySchedule.openTime + yesterdaySchedule.duration;
        return openToday || openYesterday;
    }

    public static long getNextOpenTime(Network network, Map<Integer, Schedule> schedule, Date curDate) {
        Calendar c = Calendar.getInstance(network.getTimezone());
        c.setTime(curDate);
        Schedule todaySchedule = getScheduleForDay(network, schedule, c);
        if(todaySchedule == null) {
            return 0;
        }
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() + todaySchedule.openTime;
    }

    public static long getNextCloseTime(Network network, Map<Integer, Schedule> schedule, Date curDate) {
        long now = curDate.getTime();
        Calendar c = Calendar.getInstance(network.getTimezone());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long todayPassed = now - c.getTimeInMillis();
        Schedule todaySchedule = getScheduleForDay(network, schedule, c);
        c.add(Calendar.DATE, -1);
        Schedule yesterdaySchedule = getScheduleForDay(network, schedule, c);

        if(todaySchedule == null || yesterdaySchedule == null) {
            return 0;
        }

        boolean openToday = todaySchedule.open && todayPassed >= todaySchedule.openTime && todayPassed < todaySchedule.openTime + todaySchedule.duration;
        if(!openToday) {
            return c.getTimeInMillis() + yesterdaySchedule.openTime + yesterdaySchedule.duration;
        } else {
            c.add(Calendar.DATE, 1);
            return c.getTimeInMillis() + todaySchedule.openTime + todaySchedule.duration;
        }
    }

    private static Schedule getScheduleForDay(Network network, Map<Integer, Schedule> schedule, Calendar c) {
        int dayOfYear = c.get(Calendar.DAY_OF_YEAR);
        // first check for schedule-specific special days
        if(schedule.containsKey(100 + dayOfYear)) {
            return schedule.get(100 + dayOfYear);
        }
        // now check for network-specific holidays
        if(network.getHolidays().contains(dayOfYear)) {
            return schedule.get(-1);
        }
        // now check normal schedule by day of week
        return schedule.get(c.get(Calendar.DAY_OF_WEEK) - 1);
    }

    public static void addSchedule(Map<Integer, Schedule> schedule, Schedule entry) {
        if(entry.holiday) {
            if(entry.day <= 0) {
                schedule.put(-1, entry);
            } else {
                schedule.put(100 + entry.day, entry);
            }
        } else {
            schedule.put(entry.day, entry);
        }
    }
}
