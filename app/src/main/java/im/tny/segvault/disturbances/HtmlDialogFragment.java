package im.tny.segvault.disturbances;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

import org.sufficientlysecure.htmltextview.HtmlTextView;

/**
 * Created by gabriel on 7/12/17.
 */
public class HtmlDialogFragment extends DialogFragment {
    private static final String ARG_CONTENT = "content";
    private static final String ARG_HTML = "html";

    public static HtmlDialogFragment newInstance(String content, boolean isHtml) {
        HtmlDialogFragment fragment = new HtmlDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CONTENT, content);
        args.putBoolean(ARG_HTML, isHtml);
        fragment.setArguments(args);
        return fragment;
    }

    public static HtmlDialogFragment newInstance(String content) {
        return newInstance(content, true);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String content = "";
        boolean isHtml = false;
        if (getArguments() != null) {
            content = getArguments().getString(ARG_CONTENT);
            isHtml = getArguments().getBoolean(ARG_HTML);
        }
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_html, null);

        HtmlTextView htmltv = (HtmlTextView) view.findViewById(R.id.html_view);
        if (isHtml) {
            htmltv.setHtml(content);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                htmltv.setTextAppearance(R.style.TextAppearance_AppCompat_Small);
            } else {
                htmltv.setTextAppearance(getContext(), R.style.TextAppearance_AppCompat_Small);
            }
            htmltv.setText(content);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        return builder.create();
    }

    @Override
    public int show(FragmentTransaction transaction, String tag) {
        try {
            return super.show(transaction, tag);
        } catch (IllegalStateException e) {
            // ignore state loss
            return -1;
        }
    }

    @Override
    public void show(FragmentManager manager, String tag) {
        try {
            super.show(manager, tag);
        } catch (IllegalStateException e) {
            // ignore state loss
        }
    }
}
