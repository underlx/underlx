package im.tny.segvault.disturbances.ui.widget;

import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;

public class InstantAutoComplete extends androidx.appcompat.widget.AppCompatAutoCompleteTextView {
    {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                /** no-op */
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                /** no-op */
            }

            @Override
            public void afterTextChanged(Editable s) {
                mSelectionFromPopUp = false;
            }
        });
    }

    public InstantAutoComplete(Context context) {
        super(context);
    }

    public InstantAutoComplete(Context arg0, AttributeSet arg1) {
        super(arg0, arg1);
    }

    public InstantAutoComplete(Context arg0, AttributeSet arg1, int arg2) {
        super(arg0, arg1, arg2);
    }

    @Override
    public boolean enoughToFilter() {
        return true;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction,
                                  Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (getWindowVisibility() == View.GONE) {
            // avoid crashing on rotation
            return;
        }
        if (focused && getAdapter() != null) {
            performFiltering(getText(), 0);
            showDropDown();
        }
    }

    /**
     * A true value indicates that the user selected a suggested completion
     * from the popup, false otherwise.
     * @see #replaceText(CharSequence)
     */
    private boolean mSelectionFromPopUp;

    @Override
    protected void replaceText(CharSequence text) {
        super.replaceText(text);
        /**
         * The user selected an item from the suggested completion list.
         * The selection got converted to String, and replaced the whole content
         * of the edit box.
         */
        mSelectionFromPopUp = true;
    }

    public boolean isSelectionFromPopUp() {
        return mSelectionFromPopUp;
    }

}