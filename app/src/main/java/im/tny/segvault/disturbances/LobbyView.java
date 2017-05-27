package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.Formatter;
import java.util.Locale;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;

public class LobbyView extends LinearLayout {
    private TextView nameView;
    private LinearLayout scheduleLayout;
    private LinearLayout exitsLayout;
    private Lobby lobby;

    public LobbyView(Context context, Lobby lobby) {
        super(context);
        this.setOrientation(VERTICAL);
        this.lobby = lobby;
        initializeViews(context);
    }

    public LobbyView(Context context, AttributeSet attrs, Lobby lobby) {
        super(context, attrs);
        this.setOrientation(VERTICAL);
        this.lobby = lobby;
        initializeViews(context);
    }

    public LobbyView(Context context, AttributeSet attrs, int defStyle, Lobby lobby) {
        super(context, attrs, defStyle);
        this.setOrientation(VERTICAL);
        this.lobby = lobby;
        initializeViews(context);
    }

    /**
     * Inflates the views in the layout.
     *
     * @param context the current context for the view.
     */
    private void initializeViews(final Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.lobby_view, this);

        nameView = (TextView) findViewById(R.id.lobby_name);
        nameView.setText(String.format(context.getString(R.string.frag_station_lobby_name), lobby.getName()));

        // Schedule
        scheduleLayout = (LinearLayout) findViewById(R.id.lobby_schedule_layout);

        boolean weekdaysAllTheSame = true;
        for(int i = 2; i < 6; i++) {
            if(!compareSchedule(lobby.getSchedule(1), lobby.getSchedule(i))) {
                weekdaysAllTheSame = false;
            }
        }

        boolean holidaysAllTheSame = compareSchedule(lobby.getSchedule(-1), lobby.getSchedule(0)) && compareSchedule(lobby.getSchedule(6), lobby.getSchedule(0));

        boolean allDaysTheSame = weekdaysAllTheSame && holidaysAllTheSame && compareSchedule(lobby.getSchedule(-1), lobby.getSchedule(2));

        if(allDaysTheSame) {
            TextView tv = new TextView(context);
            tv.setText(String.format(getContext().getString(R.string.lobby_schedule_all_days), scheduleToString(lobby.getSchedule(1))));
            scheduleLayout.addView(tv);
        } else {
            if (weekdaysAllTheSame) {
                TextView tv = new TextView(context);
                tv.setText(String.format(getContext().getString(R.string.lobby_schedule_weekdays), scheduleToString(lobby.getSchedule(1))));
                scheduleLayout.addView(tv);
            } else {
                for (int i = 2; i < 6; i++) {
                    TextView tv = new TextView(context);
                    tv.setText(String.format(getContext().getString(R.string.lobby_schedule_day), new DateFormatSymbols().getWeekdays()[i], scheduleToString(lobby.getSchedule(i))));
                    scheduleLayout.addView(tv);
                }
            }

            if (holidaysAllTheSame) {
                TextView tv = new TextView(context);
                tv.setText(String.format(getContext().getString(R.string.lobby_schedule_weekends_holidays), scheduleToString(lobby.getSchedule(0))));
                scheduleLayout.addView(tv);
            } else {
                TextView tv = new TextView(context);
                tv.setText(String.format(getContext().getString(R.string.lobby_schedule_day), new DateFormatSymbols().getWeekdays()[0], scheduleToString(lobby.getSchedule(0))));
                scheduleLayout.addView(tv);

                tv = new TextView(context);
                tv.setText(String.format(getContext().getString(R.string.lobby_schedule_day), new DateFormatSymbols().getWeekdays()[6], scheduleToString(lobby.getSchedule(6))));
                scheduleLayout.addView(tv);

                tv = new TextView(context);
                tv.setText(String.format(getContext().getString(R.string.lobby_schedule_holidays), scheduleToString(lobby.getSchedule(-1))));
                scheduleLayout.addView(tv);
            }
        }

        // Exits
        exitsLayout = (LinearLayout) findViewById(R.id.lobby_exits_layout);
        for (final Lobby.Exit exit : lobby.getExits()) {
            LinearLayout exitLayout = new LinearLayout(context);
            exitLayout.setOrientation(HORIZONTAL);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            exitLayout.setLayoutParams(lp);
            TextView tv = new TextView(context);
            tv.setText(TextUtils.join(", ", exit.streets));
            lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1);
            lp.gravity = Gravity.CENTER_VERTICAL;
            tv.setLayoutParams(lp);
            Button b = new Button(context, null, R.attr.viewMapButtonStyle);
            b.setText(R.string.lobby_exit_view_map);
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse(String.format(
                                    Locale.ROOT, "https://www.google.com/maps/search/?api=1&query=%f,%f",
                                    exit.worldCoord[0], exit.worldCoord[1])));
                    context.startActivity(intent);
                }
            });
            lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0);
            lp.gravity = Gravity.CENTER_VERTICAL;
            b.setLayoutParams(lp);
            exitLayout.addView(tv);
            exitLayout.addView(b);
            exitsLayout.addView(exitLayout);
        }
    }

    private boolean compareSchedule(Lobby.Schedule s1, Lobby.Schedule s2) {
        return s1.open == s2.open && s1.openTime == s2.openTime && s1.duration == s2.duration;
    }

    private String scheduleToString(Lobby.Schedule s) {
        if(!s.open) {
            return getContext().getString(R.string.lobby_schedule_closed);
        }
        Formatter f = new Formatter();
        DateUtils.formatDateRange(getContext(), f, s.openTime, s.openTime, DateUtils.FORMAT_SHOW_TIME, Time.TIMEZONE_UTC);
        f.format(getContext().getString(R.string.lobby_schedule_range_separator));
        DateUtils.formatDateRange(getContext(), f, s.openTime + s.duration, s.openTime + s.duration, DateUtils.FORMAT_SHOW_TIME, Time.TIMEZONE_UTC);
        return f.toString();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }
}
