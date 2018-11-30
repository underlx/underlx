package im.tny.segvault.disturbances.ui.widget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.Formatter;
import java.util.Locale;

import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.Schedule;

public class LobbyView extends LinearLayout {
    private TextView nameView;
    private LinearLayout scheduleLayout;
    private LinearLayout exitsLayout;
    private Lobby lobby;
    private int color;

    public LobbyView(Context context, Lobby lobby, int color) {
        super(context);
        this.setOrientation(VERTICAL);
        this.lobby = lobby;
        this.color = color;
        initializeViews(context);
    }

    public LobbyView(Context context, AttributeSet attrs, Lobby lobby, int color) {
        super(context, attrs);
        this.setOrientation(VERTICAL);
        this.lobby = lobby;
        this.color = color;
        initializeViews(context);
    }

    public LobbyView(Context context, AttributeSet attrs, int defStyle, Lobby lobby, int color) {
        super(context, attrs, defStyle);
        this.setOrientation(VERTICAL);
        this.lobby = lobby;
        this.color = color;
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

        nameView = findViewById(R.id.lobby_name);
        String titleStr = String.format(context.getString(R.string.frag_station_lobby_name), lobby.getName());

        int lStart = titleStr.indexOf(lobby.getName());
        int lEnd = lStart + lobby.getName().length();
        Spannable sb = new SpannableString(titleStr);
        sb.setSpan(new ForegroundColorSpan(color), lStart, lEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        nameView.setText(sb);

        // Schedule
        scheduleLayout = findViewById(R.id.lobby_schedule_layout);

        boolean weekdaysAllTheSame = true;
        for (int i = 2; i < 6; i++) {
            if (!lobby.getSchedule(1).compare(lobby.getSchedule(i))) {
                weekdaysAllTheSame = false;
            }
        }

        boolean holidaysAllTheSame = lobby.getSchedule(-1).compare(lobby.getSchedule(0)) && lobby.getSchedule(6).compare(lobby.getSchedule(0));

        boolean allDaysTheSame = weekdaysAllTheSame && holidaysAllTheSame && lobby.getSchedule(-1).compare(lobby.getSchedule(2));

        if (allDaysTheSame) {
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
        exitsLayout = findViewById(R.id.lobby_exits_layout);
        for (final Lobby.Exit exit : lobby.getExits()) {
            LinearLayout exitLayout = new LinearLayout(context);
            exitLayout.setOrientation(HORIZONTAL);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            exitLayout.setLayoutParams(lp);

            ImageView iv = new ImageView(context);
            Drawable image = ContextCompat.getDrawable(context, Util.getDrawableResourceIdForExitType(exit.type, lobby.isAlwaysClosed())).mutate();
            image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
            Drawable imageWrap = DrawableCompat.wrap(image);
            DrawableCompat.setTint(imageWrap, color);
            iv.setImageDrawable(imageWrap);

            LayoutParams ivlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            ivlp.gravity = Gravity.CENTER_VERTICAL;
            ivlp.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, context.getResources().getDisplayMetrics());
            iv.setLayoutParams(ivlp);

            TextView tv = new TextView(context);
            tv.setText(exit.getExitsString());
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
                    try {
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        // oh well
                    }
                }
            });
            lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0);
            lp.gravity = Gravity.CENTER_VERTICAL;
            b.setLayoutParams(lp);
            exitLayout.addView(iv);
            exitLayout.addView(tv);
            exitLayout.addView(b);
            int[] attrs = new int[] { R.attr.selectableItemBackground /* index 0 */};
            TypedArray ta = getContext().obtainStyledAttributes(attrs);
            ViewCompat.setBackground(exitLayout, ta.getDrawable(0));
            ta.recycle();
            exitLayout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (interactionListener != null) {
                        interactionListener.onExitClicked(exit);
                    }
                }
            });
            exitsLayout.addView(exitLayout);
        }
    }

    private String scheduleToString(Schedule s) {
        if (!s.open) {
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

    private OnViewInteractionListener interactionListener;

    public OnViewInteractionListener getInteractionListener() {
        return interactionListener;
    }

    public void setInteractionListener(OnViewInteractionListener interactionListener) {
        this.interactionListener = interactionListener;
    }

    public interface OnViewInteractionListener {
        void onExitClicked(Lobby.Exit exit);
    }
}
