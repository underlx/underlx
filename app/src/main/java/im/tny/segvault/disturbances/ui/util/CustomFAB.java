package im.tny.segvault.disturbances.ui.util;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CustomFAB extends FloatingActionButton {
    public CustomFAB(Context context) {
        super(context);
    }

    public CustomFAB(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomFAB(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private Matrix imageMatrix;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        imageMatrix = getImageMatrix();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        setImageMatrix(imageMatrix);
    }
}
