package im.tny.segvault.disturbances;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;

import java.util.Locale;

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

    @TargetApi(Build.VERSION_CODES.N)
    public static Locale getCurrentLocale(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            return context.getResources().getConfiguration().locale;
        }
    }
}
