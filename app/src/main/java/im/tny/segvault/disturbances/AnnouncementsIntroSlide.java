package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.widget.CompoundButtonCompat;
import android.support.v7.widget.AppCompatCheckBox;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;

/**
 * Created by Gabriel on 27/07/2017.
 */

public class AnnouncementsIntroSlide extends Fragment {
    private View view;

    public static AnnouncementsIntroSlide newInstance() {
        AnnouncementsIntroSlide sampleSlide = new AnnouncementsIntroSlide();
        return sampleSlide;
    }

    public AnnouncementsIntroSlide() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_intro_announcements, container, false);

        ((Button) view.findViewById(R.id.select_sources_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                view.findViewById(R.id.description_layout).setVisibility(View.GONE);
                view.findViewById(R.id.select_sources_button).setVisibility(View.GONE);

                LinearLayout checkboxLayout = (LinearLayout) view.findViewById(R.id.checkbox_layout);

                SharedPreferences sharedPref = getContext().getSharedPreferences("notifsettings", Context.MODE_PRIVATE);
                Set<String> sourcePref = sharedPref.getStringSet("pref_notifs_announcement_sources", null);

                for (final Announcement.Source s : Announcement.getSources()) {
                    AppCompatCheckBox checkBox = new AppCompatCheckBox(view.getContext());
                    checkBox.setText(getString(s.nameResourceId));
                    checkBox.setTextColor(Color.WHITE);
                    ColorStateList colorStateList = new ColorStateList(
                            new int[][]{
                                    new int[]{-android.R.attr.state_checked}, // unchecked
                                    new int[]{android.R.attr.state_checked}, // checked
                            },
                            new int[]{s.color, s.color,}
                    );
                    CompoundButtonCompat.setButtonTintList(checkBox, colorStateList);

                    checkBox.setChecked(sourcePref == null || sourcePref.contains(s.id));

                    checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            updateShowAnnouncementNotifs(s.id, isChecked);
                        }
                    });

                    checkboxLayout.addView(checkBox);
                }

                view.findViewById(R.id.sources_layout).setVisibility(View.VISIBLE);
            }
        });

        return view;
    }

    private void updateShowAnnouncementNotifs(String sourceId, boolean show) {
        SharedPreferences sharedPref = getContext().getSharedPreferences("notifsettings", Context.MODE_PRIVATE);
        Set<String> defaultSet = new HashSet<String>();
        defaultSet.addAll(Arrays.asList(getResources().getStringArray(R.array.default_announcement_sources)));
        Set<String> sourcePref = sharedPref.getStringSet("pref_notifs_announcement_sources", defaultSet);
        if(show) {
            sourcePref.add(sourceId);
        } else {
            sourcePref.remove(sourceId);
        }
        SharedPreferences.Editor e = sharedPref.edit();
        e.putStringSet("pref_notifs_announcement_sources", sourcePref);
        e.apply();
    }
}