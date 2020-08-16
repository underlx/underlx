package im.tny.segvault.disturbances;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.io.File;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by gabriel on 4/22/17.
 */

public class Util {
    @SuppressWarnings("deprecation")
    public static Spanned fromHtml(String html) {
        Spanned result;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            result = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(html);
        }
        return result;
    }

    public static int getDrawableResourceIdForLine(Line line) {
        return getDrawableResourceIdForLineId(line.getId());
    }

    public static final int[] lobbyColors = new int[]{
            Color.parseColor("#C040CE"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#142382"),
            Color.parseColor("#E0A63A"),
            Color.parseColor("#F15D2A")};

    public static int getDrawableResourceIdForLineId(String id) {
        switch (id) {
            case "pt-ml-amarela":
                return R.drawable.line_pt_ml_amarela;
            case "pt-ml-azul":
                return R.drawable.line_pt_ml_azul;
            case "pt-ml-verde":
                return R.drawable.line_pt_ml_verde;
            case "pt-ml-vermelha":
                return R.drawable.line_pt_ml_vermelha;
            default:
                return R.drawable.ic_menu_directions_subway;
        }
    }

    public static int getDrawableResourceIdForPOIType(String type) {
        switch (type) {
            case "dinning":
                return R.drawable.ic_silverware_black_24dp;
            case "police":
                return R.drawable.ic_police_black_24dp;
            case "fire-station":
                return R.drawable.ic_fireman_black_24dp;
            case "sports":
                return R.drawable.ic_sports_black_24dp;
            case "school":
            case "university":
                return R.drawable.ic_school_black_24dp;
            case "library":
                return R.drawable.ic_local_library_black_24dp;
            case "airport":
                return R.drawable.ic_local_airport_black_24dp;
            case "embassy":
                return R.drawable.ic_flag_black_24dp;
            case "church":
                return R.drawable.ic_human_handsup_black_24dp;
            case "business":
                return R.drawable.ic_store_mall_directory_black_24dp;
            case "zoo":
                return R.drawable.ic_elephant_black_24dp;
            case "court":
                return R.drawable.ic_gavel_black_24dp;
            case "park":
                return R.drawable.ic_local_florist_black_24dp;
            case "hospital":
                return R.drawable.ic_local_hospital_black_24dp;
            case "monument":
                return R.drawable.ic_chess_rook_black_24dp;
            case "museum":
                return R.drawable.ic_account_balance_black_24dp;
            case "shopping-center":
                return R.drawable.ic_shopping_basket_black_24dp;
            case "health-center":
                return R.drawable.ic_medical_bag_black_24dp;
            case "bank":
                return R.drawable.ic_money_bag_black_24dp;
            case "viewpoint":
                return R.drawable.ic_binoculars_black_24dp;
            case "casino":
                return R.drawable.ic_casino_black_24dp;
            case "theater":
                return R.drawable.ic_guy_fawkes_mask_black_24dp;
            case "show-room":
                return R.drawable.ic_spotlight_beam_black_24dp;
            case "organization":
                return R.drawable.ic_group_black_24dp;
            case "transportation-hub":
                return R.drawable.ic_device_hub_black_24dp;
            case "public-space":
                return R.drawable.ic_location_city_black_24dp;
            case "government":
                return R.drawable.ic_portuguese_flag_black_24dp;
            case "market":
                return R.drawable.ic_food_apple_black_24dp;
            case "public-service":
                return R.drawable.ic_room_service_black_24dp;
            case "institute":
                return R.drawable.ic_certificate_black_24dp;
            case "post-office":
                return R.drawable.ic_mail_black_24dp;
            case "cemetery":
                return R.drawable.ic_coffin_black_24dp;
            case "hotel":
                return R.drawable.ic_hotel_black_24dp;
            default:
                return R.drawable.ic_place_black_24dp;
        }
    }

    public static int getStringResourceIdForPOIType(String type) {
        switch (type) {
            case "dinning":
                return R.string.poi_type_dinning;
            case "police":
                return R.string.poi_type_police;
            case "fire-station":
                return R.string.poi_type_fire_station;
            case "sports":
                return R.string.poi_type_sports;
            case "school":
                return R.string.poi_type_school;
            case "university":
                return R.string.poi_type_university;
            case "library":
                return R.string.poi_type_library;
            case "airport":
                return R.string.poi_type_airport;
            case "embassy":
                return R.string.poi_type_embassy;
            case "church":
                return R.string.poi_type_church;
            case "business":
                return R.string.poi_type_business;
            case "zoo":
                return R.string.poi_type_zoo;
            case "court":
                return R.string.poi_type_court;
            case "park":
                return R.string.poi_type_park;
            case "hospital":
                return R.string.poi_type_hospital;
            case "monument":
                return R.string.poi_type_monument;
            case "museum":
                return R.string.poi_type_museum;
            case "shopping-center":
                return R.string.poi_type_shopping_center;
            case "health-center":
                return R.string.poi_type_health_center;
            case "bank":
                return R.string.poi_type_bank;
            case "viewpoint":
                return R.string.poi_type_viewpoint;
            case "casino":
                return R.string.poi_type_casino;
            case "theater":
                return R.string.poi_type_theater;
            case "show-room":
                return R.string.poi_type_show_room;
            case "organization":
                return R.string.poi_type_organization;
            case "transportation-hub":
                return R.string.poi_type_transportation_hub;
            case "public-space":
                return R.string.poi_type_public_space;
            case "government":
                return R.string.poi_type_government;
            case "market":
                return R.string.poi_type_market;
            case "public-service":
                return R.string.poi_type_public_service;
            case "institute":
                return R.string.poi_type_institute;
            case "post-office":
                return R.string.poi_type_post_office;
            case "cemetery":
                return R.string.poi_type_cemetery;
            case "hotel":
                return R.string.poi_type_hotel;
            default:
                return R.string.search_poi_subtitle;
        }
    }

    public static int getColorForPOIType(String type, Context context) {
        switch (type) {
            case "police":
            case "fire-station":
            case "health-center":
            case "hospital":
            case "cemetery":
                return Color.parseColor("#3E6990");

            case "school":
            case "university":
            case "library":
            case "institute":
            case "hotel":
                return Color.parseColor("#D66F37");

            case "airport":
            case "transportation-hub":
                return Color.parseColor("#381D2A");

            case "museum":
            case "viewpoint":
            case "monument":
            case "sports":
            case "park":
            case "zoo":
                return Color.parseColor("#008C70");

            case "dinning":
            case "casino":
            case "theater":
            case "show-room":
                return Color.parseColor("#D14E45");

            case "shopping-center":
            case "market":
            case "bank":
            case "organization":
            case "church":
            case "business":
                return Color.parseColor("#5A6199");

            case "embassy":
            case "court":
            case "public-space":
            case "government":
            case "public-service":
            case "post-office":
                return Color.parseColor("#AD9000");

            default:
                return ContextCompat.getColor(context, R.color.colorPrimary);
        }
    }

    public static int getDrawableResourceIdForExitType(String type, boolean closed) {
        if (closed) {
            return R.drawable.ic_no_entry_black_24dp;
        }
        switch (type) {
            case "stairs":
                return R.drawable.ic_stairs_black_24dp;
            case "escalator":
                return R.drawable.ic_escalator_black_24dp;
            case "ramp":
                return R.drawable.ic_ramp_black_24dp;
            case "lift":
                return R.drawable.ic_elevator;
            default:
                // TODO
                return R.drawable.ic_place_black_24dp;
        }
    }

    public static int getDrawableResourceIdForStationTag(String tag) {
        switch (tag) {
            case "a_baby":
                return R.drawable.ic_child_care_black_24dp;
            case "a_store":
                return R.drawable.ic_gift_black_24dp;
            case "a_wc":
                return R.drawable.ic_wc_black_24dp;
            case "a_wifi":
                return R.drawable.ic_wifi_black_24dp;
            case "c_airport":
                return R.drawable.ic_local_airport_black_24dp;
            case "c_bike":
                return R.drawable.ic_directions_bike_black_24dp;
            case "c_boat":
                return R.drawable.ic_directions_boat_black_24dp;
            case "c_bus":
                return R.drawable.ic_directions_bus_black_24dp;
            case "c_parking":
                return R.drawable.ic_local_parking_black_24dp;
            case "c_taxi":
                return R.drawable.ic_local_taxi_black_24dp;
            case "c_train":
                return R.drawable.ic_train_black_24dp;
            case "m_escalator_platform":
            case "m_escalator_surface":
                return R.drawable.ic_escalator_black_24dp;
            case "m_lift_platform":
            case "m_lift_surface":
                return R.drawable.ic_elevator;
            case "m_platform":
            case "m_stepfree":
                return R.drawable.ic_wheelchair_black_24dp;
            case "s_lostfound":
                return R.drawable.ic_account_balance_wallet_black_24dp;
            case "s_ticket1":
            case "s_ticket2":
            case "s_ticket3":
            case "s_urgent_pass":
            case "s_navegante":
                return R.drawable.ic_cards_black_24dp;
            case "s_info":
            case "s_client":
                return R.drawable.ic_info_black_24dp;
            default:
                return 0;
        }
    }

    public static String getStringForStationTag(Context context, String tag) {
        switch (tag) {
            case "a_baby":
                return context.getString(R.string.frag_station_baby_care);
            case "a_store":
                return context.getString(R.string.frag_station_stores);
            case "a_wc":
                return context.getString(R.string.frag_station_wc);
            case "a_wifi":
                return context.getString(R.string.frag_station_wifi);
            case "c_airport":
                return context.getString(R.string.frag_station_airport);
            case "c_bike":
                return context.getString(R.string.frag_station_shared_bikes);
            case "c_boat":
                return context.getString(R.string.frag_station_boat);
            case "c_bus":
                return context.getString(R.string.frag_station_bus);
            case "c_parking":
                return context.getString(R.string.frag_station_car_parking);
            case "c_taxi":
                return context.getString(R.string.frag_station_taxi);
            case "c_train":
                return context.getString(R.string.frag_station_train);
            case "m_escalator_platform":
                return context.getString(R.string.frag_station_escalator_platform);
            case "m_escalator_surface":
                return context.getString(R.string.frag_station_escalator_surface);
            case "m_lift_platform":
                return context.getString(R.string.frag_station_lift_platform);
            case "m_lift_surface":
                return context.getString(R.string.frag_station_lift_surface);
            case "m_platform":
                return context.getString(R.string.frag_station_wheelchair_platform);
            case "m_stepfree":
                return context.getString(R.string.frag_station_reduced_mobility);
            case "s_client":
                return context.getString(R.string.frag_station_client_space);
            case "s_info":
                return context.getString(R.string.frag_station_info_space);
            case "s_lostfound":
                return context.getString(R.string.frag_station_lostfound);
            case "s_navegante":
                return context.getString(R.string.frag_station_navegante_space);
            case "s_ticket1":
            case "s_ticket2":
            case "s_ticket3":
                return context.getString(R.string.frag_station_ticket_office);
            case "s_urgent_pass":
                return context.getString(R.string.frag_station_urgent_ticket);
            default:
                return "";
        }
    }

    public static Drawable getColoredDrawableResource(Context context, int id, int color) {
        Drawable background = ContextCompat.getDrawable(context, id);
        if (background == null) {
            return null;
        }
        background = background.mutate();
        if (background instanceof ShapeDrawable) {
            ((ShapeDrawable) background).getPaint().setColor(color);
        } else if (background instanceof GradientDrawable) {
            ((GradientDrawable) background).setColor(color);
        } else if (background instanceof ColorDrawable) {
            ((ColorDrawable) background).setColor(color);
        }
        return background;
    }

    public static BitmapDescriptor getBitmapDescriptorFromVector(Context context, @DrawableRes int vectorResId, int tintColor) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable wrapDrawable = DrawableCompat.wrap(vectorDrawable);
        DrawableCompat.setTint(wrapDrawable, tintColor);
        wrapDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public static BitmapDescriptor getBitmapDescriptorFromVector(Context context, @DrawableRes int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public static BitmapDescriptor createMapMarker(Context context, @DrawableRes int vectorResId, int markerColor) {
        Drawable background_background = ContextCompat.getDrawable(context, R.drawable.map_marker_background_background);
        background_background.setBounds(0, 0, background_background.getIntrinsicWidth(), background_background.getIntrinsicHeight());

        Drawable background = ContextCompat.getDrawable(context, R.drawable.map_marker_background);
        background.setBounds(0, 0, background.getIntrinsicWidth(), background.getIntrinsicHeight());
        Drawable backgroundWrap = DrawableCompat.wrap(background);
        DrawableCompat.setTint(backgroundWrap, markerColor);

        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        int one = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
        int twentyfour = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 23, context.getResources().getDisplayMetrics());
        vectorDrawable.setBounds(one, one, twentyfour, twentyfour);
        Drawable foregroundWrap = DrawableCompat.wrap(vectorDrawable);
        DrawableCompat.setTint(foregroundWrap, markerColor);

        Bitmap bitmap = Bitmap.createBitmap(backgroundWrap.getIntrinsicWidth(), backgroundWrap.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        background_background.draw(canvas);
        backgroundWrap.draw(canvas);
        foregroundWrap.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public static int manipulateColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a,
                Math.min(r, 255),
                Math.min(g, 255),
                Math.min(b, 255));
    }

    @TargetApi(Build.VERSION_CODES.N)
    public static Locale getCurrentLocale(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            return context.getResources().getConfiguration().locale;
        }
    }

    public static String getCurrentLanguage(Context context) {
        return getCurrentLocale(context).getLanguage();
    }

    public static String getOrdinal(final Context context, final int n, final boolean female) {
        switch (getCurrentLocale(context).getLanguage()) {
            case "en": {
                switch (n % 100) {
                    case 11:
                    case 12:
                    case 13:
                        return String.format("%dth", n);
                }
                switch (n % 10) {
                    case 1:
                        return String.format("%dst", n);
                    case 2:
                        return String.format("%dnd", n);
                    case 3:
                        return String.format("%drd", n);
                    default:
                        return String.format("%dth", n);
                }
            }
            case "fr":
                if (n == 1) {
                    if (female) {
                        return String.format("%dère", n);
                    } else {
                        return String.format("%der", n);
                    }
                }
                return String.format("%dème", n);
            case "de":
                return String.format("%d.", n);
            default:
                if (female) {
                    return String.format("%dª", n);
                }
                return String.format("%dº", n);
        }
    }

    public static String[] getLineNames(Context context, Line line) {
        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        boolean preferMainNames = sharedPref.getBoolean(PreferenceNames.PreferMainNames, true);
        String[] names = line.getNames(getCurrentLanguage(context));
        if (!preferMainNames && names.length > 1) {
            return new String[]{names[1], names[0]};
        }
        return names;
    }

    public static String[] getNetworkNames(Context context, Network network) {
        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        boolean preferMainNames = sharedPref.getBoolean(PreferenceNames.PreferMainNames, true);
        String[] names = network.getNames(getCurrentLanguage(context));
        if (!preferMainNames && names.length > 1) {
            return new String[]{names[1], names[0]};
        }
        return names;
    }

    public interface OnLineStatusSpanClickListener {
        void onClick(String url);
    }

    @JsonIgnore
    public static Spannable enrichLineStatus(Context context, String networkID, String lineID, String status, String msgType, Date statusTime, @Nullable final OnLineStatusSpanClickListener listener) {
        Spannable sb = new SpannableString(status);
        if (context == null || msgType == null) {
            return sb;
        }

        switch (msgType) {
            case "REPORT_BEGIN":
                return new SpannableString(context.getString(R.string.disturbance_status_report_begin));
            case "REPORT_CONFIRM":
                return new SpannableString(context.getString(R.string.disturbance_status_report_confirm));
            case "REPORT_RECONFIRM":
                return new SpannableString(context.getString(R.string.disturbance_status_report_reconfirm));
            case "REPORT_SOLVED":
                return new SpannableString(context.getString(R.string.disturbance_status_report_solved));
        }


        MapManager mm = Coordinator.get(context).getMapManager();
        Network network = mm.getNetwork(networkID);
        if (network == null) {
            return sb;
        }
        Line line = network.getLine(lineID);

        SharedPreferences sharedPref = context.getSharedPreferences("settings", MODE_PRIVATE);
        // do not translate if the locale is the same (will only break stuff)
        final boolean translateAll = sharedPref.getBoolean(PreferenceNames.TranslateAllStatus, true) && (line == null || !line.getMainLocale().equals(getCurrentLanguage(context)));

        Station firstStation = null, secondStation = null;
        if (msgType.startsWith("ML_") && msgType.contains("_BETWEEN_")) {
            int firstStationStartIdx = status.indexOf("entre as estações ") + "entre as estações ".length();
            int firstStationEndIdx = firstStationStartIdx + status.substring(firstStationStartIdx).indexOf(" e ");
            int secondStationStartIdx = firstStationEndIdx + " e ".length();
            int secondStationEndIdx = secondStationStartIdx + 3 + status.substring(secondStationStartIdx + 3).indexOf(".");
            String firstStationName = sanitizeStationName(status.substring(firstStationStartIdx, firstStationEndIdx).trim());
            String secondStationName = sanitizeStationName(status.substring(secondStationStartIdx, secondStationEndIdx).trim());
            firstStation = network.getStationByName(firstStationName);
            secondStation = network.getStationByName(secondStationName);

            final Station fs = firstStation, ss = secondStation;
            if (!translateAll && listener != null) {
                // their messages sometimes have a extra space before the name of the first station...
                while (status.charAt(firstStationStartIdx) == ' ' && firstStationStartIdx < firstStationEndIdx) {
                    firstStationStartIdx++;
                }

                if (firstStation != null) {
                    sb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View view) {
                            listener.onClick("station:" + fs.getId());
                        }
                    }, firstStationStartIdx, firstStationEndIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (secondStation != null) {
                    sb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View view) {
                            listener.onClick("station:" + ss.getId());
                        }
                    }, secondStationStartIdx, secondStationEndIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return sb;
            }
        }

        if (!translateAll || !msgType.startsWith("ML_")) {
            return sb;
        }

        // see the different message types at https://github.com/underlx/disturbancesmlx/blob/master/dataobjects/status.go

        String[] parts = msgType.split("_");
        // parts[0] is "ML"

        if (parts.length < 2) {
            return sb;
        }

        switch (parts[1]) {
            case "GENERIC":
                return new SpannableString(context.getString(R.string.disturbance_status_pt_ml_generic));
            case "SOLVED":
                return new SpannableString(context.getString(R.string.disturbance_status_pt_ml_solved));
            case "CLOSED":
                return new SpannableString(context.getString(R.string.disturbance_status_pt_ml_closed));
            case "SPECIAL":
                return new SpannableString(context.getString(R.string.disturbance_status_pt_ml_special));
        }

        if (parts.length < 4) {
            return sb;
        }

        String tripartMsgFormat = context.getString(R.string.disturbance_status_pt_ml_tripart_format);
        String partOne, partTwo = "", partThree = "";
        switch (parts[1]) {
            case "SIGNAL":
                partOne = context.getString(R.string.disturbance_status_pt_ml_part_signal);
                break;
            case "TRAIN":
                partOne = context.getString(R.string.disturbance_status_pt_ml_part_train);
                break;
            case "POWER":
                partOne = context.getString(R.string.disturbance_status_pt_ml_part_power);
                break;
            case "3RDPARTY":
                partOne = context.getString(R.string.disturbance_status_pt_ml_part_3rdparty);
                break;
            case "PASSENGER":
                partOne = context.getString(R.string.disturbance_status_pt_ml_part_passenger);
                break;
            case "STATION":
                partOne = context.getString(R.string.disturbance_status_pt_ml_part_station);
                break;
            default:
                // abort if we don't have a translation for one of the parts
                return sb;
        }

        switch (parts[2]) {
            case "SINCE": {
                int timeStartIdx = status.indexOf("desde as ") + "desde as ".length();
                int timeEndIdx = timeStartIdx + status.substring(timeStartIdx).indexOf(".");
                java.util.TimeZone tz = network == null ? java.util.TimeZone.getDefault() : network.getTimezone();
                Date time;
                SimpleDateFormat disturbanceTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                try {
                    time = disturbanceTimeFormat.parse(status.substring(timeStartIdx, timeEndIdx));
                } catch (ParseException e) {
                    return sb;
                }
                Calendar statusCal = Calendar.getInstance();
                statusCal.setTime(statusTime);
                statusCal.setTimeZone(tz);
                Calendar parsedCal = Calendar.getInstance();
                parsedCal.setTime(time);
                parsedCal.setTimeZone(tz);
                parsedCal.set(Calendar.YEAR, statusCal.get(Calendar.YEAR));
                parsedCal.set(Calendar.MONTH, statusCal.get(Calendar.MONTH));
                parsedCal.set(Calendar.DAY_OF_MONTH, statusCal.get(Calendar.DAY_OF_MONTH));
                Formatter f = new Formatter();
                DateUtils.formatDateRange(context, f, parsedCal.getTimeInMillis(), parsedCal.getTimeInMillis(), DateUtils.FORMAT_SHOW_TIME);
                partTwo = String.format(context.getString(R.string.disturbance_status_pt_ml_part_since), f.toString());
                break;
            }
            case "HALTED":
                partTwo = context.getString(R.string.disturbance_status_pt_ml_part_halted);
                break;
            case "BETWEEN":
                if (firstStation == null || secondStation == null) {
                    return sb;
                }

                partTwo = String.format(context.getString(R.string.disturbance_status_pt_ml_part_between), firstStation.getName(), secondStation.getName());
                break;
            case "DELAYED":
                partTwo = context.getString(R.string.disturbance_status_pt_ml_part_delayed);
                break;
            default:
                // abort if we don't have a translation for one of the parts
                return sb;
        }

        switch (parts[3]) {
            case "LONGHALT":
                partThree = context.getString(R.string.disturbance_status_pt_ml_part_longhalt);
                break;
            case "LONGWAIT":
                partThree = context.getString(R.string.disturbance_status_pt_ml_part_longwait);
                break;
            case "SOON":
                partThree = context.getString(R.string.disturbance_status_pt_ml_part_soon);
                break;
            case "UNDER15":
                partThree = context.getString(R.string.disturbance_status_pt_ml_part_under15);
                break;
            case "OVER15":
                partThree = context.getString(R.string.disturbance_status_pt_ml_part_over15);
                break;
            default:
                // abort if we don't have a translation for one of the parts
                return sb;
        }

        String tripartMsg = String.format(tripartMsgFormat, partOne, partTwo, partThree);
        sb = new SpannableString(tripartMsg);

        if (firstStation != null && listener != null) {
            int startIdx = tripartMsg.indexOf(firstStation.getName());
            int endIdx = startIdx + firstStation.getName().length();
            final Station fs = firstStation;
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View view) {
                    listener.onClick("station:" + fs.getId());
                }
            }, startIdx, endIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (secondStation != null && listener != null) {
            int startIdx = tripartMsg.indexOf(secondStation.getName());
            int endIdx = startIdx + secondStation.getName().length();
            final Station ss = secondStation;
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View view) {
                    listener.onClick("station:" + ss.getId());
                }
            }, startIdx, endIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return sb;
    }

    private static String sanitizeStationName(String original) {
        switch (original) {
            case "S. Sebastião":
                return "São Sebastião";
            case "Colégio Militar":
                return "Colégio Militar/Luz";
        }
        return original;
    }

    public static String encodeRFC3339(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(date)
                .replaceAll("(\\d\\d)(\\d\\d)$", "$1:$2");
    }

    public static View getToolbarNavigationIcon(Toolbar toolbar) {
        // https://gist.github.com/NikolaDespotoski/bb963f9b8f40beb954a0

        //check if contentDescription previously was set
        boolean hadContentDescription = TextUtils.isEmpty(toolbar.getNavigationContentDescription());
        String contentDescription = !hadContentDescription ? toolbar.getNavigationContentDescription().toString() : "navigationIcon";
        toolbar.setNavigationContentDescription(contentDescription);
        ArrayList<View> potentialViews = new ArrayList<View>();
        //find the view based on it's content description, set programatically or with android:contentDescription
        toolbar.findViewsWithText(potentialViews, contentDescription, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        //Nav icon is always instantiated at this point because calling setNavigationContentDescription ensures its existence
        View navIcon = null;
        if (potentialViews.size() > 0) {
            navIcon = potentialViews.get(0); //navigation icon is ImageButton
        }
        //Clear content description if not previously present
        if (hadContentDescription)
            toolbar.setNavigationContentDescription(null);
        return navIcon;
    }

    public static void setViewAndChildrenEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewAndChildrenEnabled(child, enabled);
            }
        }
    }

    public static int tryParseInteger(String string, int defaultValue) {
        try {
            return Integer.valueOf(string);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static void deleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else
            return dir != null && dir.isFile() && dir.delete();
    }

    @SuppressLint("NewApi") // retrofix takes care of removeIf
    public static List<Station> getMostUsedStations(Context context, int limit) {
        List<Station> stations = new ArrayList<>(Coordinator.get(context).getMapManager().getAllStations());

        AppDatabase db = Coordinator.get(context).getDB();

        List<String> mostUsed = db.stationUseDao().getMostUsedStations(limit);

        // this is not efficient, must be improved
        List<Station> mostUsedStations = new ArrayList<>();
        for(Station station : stations) {
            if (mostUsed.contains(station.getId())) {
                mostUsedStations.add(station);
            }
        }

        return mostUsedStations;
    }

    public static class OverlaidFile implements Serializable {
        public String contents;
    }

    // large stack thread pool executor
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE = 1;

    private static final ThreadFactory yourFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            ThreadGroup group = new ThreadGroup("threadGroup");
            return new Thread(group, r, "LargeCallStackThread", 50000);
        }
    };

    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(128);

    public static final Executor LARGE_STACK_THREAD_POOL_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, sPoolWorkQueue, yourFactory);
    // end of large stack thread pool executor
}
