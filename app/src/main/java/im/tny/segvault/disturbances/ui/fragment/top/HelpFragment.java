package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.Context;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.ui.util.RichTextUtils;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;

public class HelpFragment extends TopFragment {
    private static final String ARG_PAGE = "page";
    private OnFragmentInteractionListener mListener;

    private HtmlTextView htmltv;

    public HelpFragment() {
        // Required empty public constructor
    }

    public static HelpFragment newInstance() {
        HelpFragment fragment = new HelpFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public static HelpFragment newInstance(String page) {
        HelpFragment fragment = new HelpFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PAGE, page);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setUpActivity(getString(R.string.frag_help_title), R.id.nav_help, false, false);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_help, container, false);

        htmltv = (HtmlTextView) view.findViewById(R.id.html_view);

        if (getArguments() != null && getArguments().containsKey(ARG_PAGE)) {
            setHtmlFromHelpFile(getArguments().getString(ARG_PAGE));
        } else {
            setHtmlFromHelpFile("index");
        }

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFragmentInteractionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void setHtmlFromHelpFile(String file) {
        htmltv.setHtml(getHelpFileContents(file));
        htmltv.setText(RichTextUtils.replaceAll((Spanned) htmltv.getText(), URLSpan.class, new RichTextUtils.URLSpanConverter(), new RichTextUtils.ClickSpan.OnClickListener() {
            @Override
            public void onClick(String url) {
                if(mListener == null) {
                    return;
                }
                if (url.startsWith("help:")) {
                    mListener.onHelpLinkClicked(url.substring(5));
                } else if (url.startsWith("station:")) {
                    mListener.onStationLinkClicked(url.substring(8));
                } else if (url.startsWith("mailto:")) {
                    mListener.onMailtoLinkClicked(url.substring(7));
                } else {
                    mListener.onLinkClicked(url);
                }
            }
        }));
    }

    private String getPathForHelpFile(String file) {
        Locale l = Util.getCurrentLocale(getContext());
        String path = String.format("help/%s/%s.html", l.getLanguage(), file);
        InputStream is = null;
        try {
            is = getContext().getAssets().open(path);
            return path;
        } catch (IOException ex) {
            return String.format("help/en/%s.html", file);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getHelpFileContents(String file) {
        StringBuilder buf = new StringBuilder();
        try {
            InputStream is = getContext().getAssets().open(getPathForHelpFile(file));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String str;
            while ((str = reader.readLine()) != null) {
                buf.append(str);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buf.toString();
    }

    public interface OnFragmentInteractionListener extends OnInteractionListener {
        void onHelpLinkClicked(String destination);
        void onStationLinkClicked(String destination);
        void onMailtoLinkClicked(String address);
        void onLinkClicked(String destination);
    }
}
