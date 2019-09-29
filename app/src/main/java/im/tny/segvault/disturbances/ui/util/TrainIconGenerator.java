package im.tny.segvault.disturbances.ui.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.maps.android.ui.RotationLayout;

import im.tny.segvault.disturbances.R;

public class TrainIconGenerator {
    private final Context mContext;
    private ViewGroup mContainer;
    private RotationLayout mRotationLayout;
    private TextView mTextView;
    private View mContentView;
    private int mRotation;
    private float mAnchorU = 0.5F;
    private float mAnchorV = 1.0F;
    private BubbleDrawable mBackground;
    public static final int STYLE_DEFAULT = 1;
    public static final int STYLE_WHITE = 2;
    public static final int STYLE_RED = 3;
    public static final int STYLE_BLUE = 4;
    public static final int STYLE_GREEN = 5;
    public static final int STYLE_PURPLE = 6;
    public static final int STYLE_ORANGE = 7;

    public TrainIconGenerator(Context context) {
        this.mContext = context;
        this.mBackground = new BubbleDrawable(this.mContext.getResources());
        this.mContainer = (ViewGroup) LayoutInflater.from(this.mContext).inflate(R.layout.trainicon_text_bubble, (ViewGroup) null);
        this.mRotationLayout = (RotationLayout) this.mContainer.getChildAt(0);
        this.mContentView = this.mTextView = (TextView) this.mRotationLayout.findViewById(R.id.amu_text);
        this.setStyle(1);
    }

    public Bitmap makeIcon(CharSequence text) {
        if (this.mTextView != null) {
            this.mTextView.setText(text);
        }

        return this.makeIcon();
    }

    public Bitmap makeIcon() {
        int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        this.mContainer.measure(measureSpec, measureSpec);
        int measuredWidth = this.mContainer.getMeasuredWidth();
        int measuredHeight = this.mContainer.getMeasuredHeight();
        this.mContainer.layout(0, 0, measuredWidth, measuredHeight);
        if (this.mRotation == 1 || this.mRotation == 3) {
            measuredHeight = this.mContainer.getMeasuredWidth();
            measuredWidth = this.mContainer.getMeasuredHeight();
        }

        Bitmap r = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888);
        r.eraseColor(0);
        Canvas canvas = new Canvas(r);
        switch (this.mRotation) {
            case 0:
            default:
                break;
            case 1:
                canvas.translate((float) measuredWidth, 0.0F);
                canvas.rotate(90.0F);
                break;
            case 2:
                canvas.rotate(180.0F, (float) (measuredWidth / 2), (float) (measuredHeight / 2));
                break;
            case 3:
                canvas.translate(0.0F, (float) measuredHeight);
                canvas.rotate(270.0F);
        }

        this.mContainer.draw(canvas);
        return r;
    }

    public void setContentView(View contentView) {
        this.mRotationLayout.removeAllViews();
        this.mRotationLayout.addView(contentView);
        this.mContentView = contentView;
        View view = this.mRotationLayout.findViewById(R.id.amu_text);
        this.mTextView = view instanceof TextView ? (TextView) view : null;
    }

    public void setContentRotation(int degrees) {
        this.mRotationLayout.setViewRotation(degrees);
    }

    public void setRotation(int degrees) {
        this.mRotation = (degrees + 360) % 360 / 90;
    }

    public float getAnchorU() {
        return this.rotateAnchor(this.mAnchorU, this.mAnchorV);
    }

    public float getAnchorV() {
        return this.rotateAnchor(this.mAnchorV, this.mAnchorU);
    }

    private float rotateAnchor(float u, float v) {
        switch (this.mRotation) {
            case 0:
                return u;
            case 1:
                return 1.0F - v;
            case 2:
                return 1.0F - u;
            case 3:
                return v;
            default:
                throw new IllegalStateException();
        }
    }

    public void setTextAppearance(Context context, int resid) {
        if (this.mTextView != null) {
            this.mTextView.setTextAppearance(context, resid);
        }

    }

    public void setTextAppearance(int resid) {
        this.setTextAppearance(this.mContext, resid);
    }

    public void setStyle(int style) {
        this.setColor(getStyleColor(style));
        this.setTextAppearance(this.mContext, getTextStyle(style));
    }

    public void setColor(int color) {
        this.mBackground.setColor(color);
        this.setBackground(this.mBackground);
    }

    public void setBackground(Drawable background) {
        this.mContainer.setBackgroundDrawable(background);
        if (background != null) {
            Rect rect = new Rect();
            background.getPadding(rect);
            this.mContainer.setPadding(rect.left, rect.top, rect.right, rect.bottom);
        } else {
            this.mContainer.setPadding(0, 0, 0, 0);
        }

    }

    public void setContentPadding(int left, int top, int right, int bottom) {
        this.mContentView.setPadding(left, top, right, bottom);
    }

    private static int getStyleColor(int style) {
        switch (style) {
            case 1:
            case 2:
            default:
                return -1;
            case 3:
                return -3407872;
            case 4:
                return -16737844;
            case 5:
                return -10053376;
            case 6:
                return -6736948;
            case 7:
                return -30720;
        }
    }

    private static int getTextStyle(int style) {
        switch (style) {
            case 1:
            case 2:
            default:
                return R.style.trainIcon_TextAppearance_Dark;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
                return R.style.trainIcon_TextAppearance_Light;
        }
    }

    class BubbleDrawable extends Drawable {
        private final Drawable mShadow;
        private final Drawable mMask;
        private int mColor = -1;

        public BubbleDrawable(Resources res) {
            this.mMask = res.getDrawable(R.drawable.trainicon_mask);
            this.mShadow = res.getDrawable(R.drawable.trainicon_shadow);
        }

        public void setColor(int color) {
            this.mColor = color;
        }

        public void draw(Canvas canvas) {
            this.mMask.draw(canvas);
            canvas.drawColor(this.mColor, PorterDuff.Mode.SRC_IN);
            this.mShadow.draw(canvas);
        }

        public void setAlpha(int alpha) {
            throw new UnsupportedOperationException();
        }

        public void setColorFilter(ColorFilter cf) {
            throw new UnsupportedOperationException();
        }

        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        public void setBounds(int left, int top, int right, int bottom) {
            this.mMask.setBounds(left, top, right, bottom);
            this.mShadow.setBounds(left, top, right, bottom);
        }

        public void setBounds(Rect bounds) {
            this.mMask.setBounds(bounds);
            this.mShadow.setBounds(bounds);
        }

        public boolean getPadding(Rect padding) {
            return this.mMask.getPadding(padding);
        }
    }

}
