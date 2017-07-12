package im.tny.segvault.disturbances;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

import org.sufficientlysecure.htmltextview.HtmlTextView;

/**
 * Created by gabriel on 7/12/17.
 */
public class HtmlDialogFragment extends DialogFragment {
    private static final String ARG_HTML = "html";

    public static HtmlDialogFragment newInstance(String html) {
        HtmlDialogFragment fragment = new HtmlDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_HTML, html);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String html = "";
        if (getArguments() != null) {
            html = getArguments().getString(ARG_HTML);
        }
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_html, null);

        HtmlTextView htmltv = (HtmlTextView) view.findViewById(R.id.html_view);
        htmltv.setHtml(html);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        return builder.create();
    }
}
