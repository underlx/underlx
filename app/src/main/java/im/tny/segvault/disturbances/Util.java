package im.tny.segvault.disturbances;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import im.tny.segvault.subway.Line;

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

    public static BitmapDescriptor getBitmapDescriptorFromVector(Context context, int vectorResId, int tintColor) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable wrapDrawable = DrawableCompat.wrap(vectorDrawable);
        DrawableCompat.setTint(wrapDrawable, tintColor);
        wrapDrawable.draw(canvas);
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

    public static String encodeRFC3339(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(date)
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
