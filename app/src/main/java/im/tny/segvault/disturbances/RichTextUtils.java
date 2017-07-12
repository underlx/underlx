package im.tny.segvault.disturbances;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;

public class RichTextUtils {
    public static <A extends CharacterStyle, B extends CharacterStyle> Spannable replaceAll(Spanned original,
                                                                                            Class<A> sourceType,
                                                                                            SpanConverter<A, B> converter,
                                                                                            final ClickSpan.OnClickListener listener) {
        SpannableString result = new SpannableString(original);
        A[] spans = result.getSpans(0, result.length(), sourceType);

        for (A span : spans) {
            int start = result.getSpanStart(span);
            int end = result.getSpanEnd(span);
            int flags = result.getSpanFlags(span);

            result.removeSpan(span);
            result.setSpan(converter.convert(span, listener), start, end, flags);
        }

        return (result);
    }

    public interface SpanConverter<A extends CharacterStyle, B extends CharacterStyle> {
        B convert(A span, ClickSpan.OnClickListener listener);
    }

    public static class ClickSpan extends ClickableSpan {
        private String url;
        private OnClickListener mListener;

        public ClickSpan(String url, OnClickListener mListener) {
            this.url = url;
            this.mListener = mListener;
        }

        @Override
        public void onClick(View widget) {
            if (mListener != null) mListener.onClick(url);
        }

        public interface OnClickListener {
            void onClick(String url);
        }
    }

    public static class URLSpanConverter implements RichTextUtils.SpanConverter<URLSpan, ClickSpan> {

        @Override
        public ClickSpan convert(URLSpan span, ClickSpan.OnClickListener listener) {
            return (new ClickSpan(span.getURL(), listener));
        }
    }
}