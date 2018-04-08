package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

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

    private View rootView;

    public HelpFragment() {
        // Required empty public constructor
    }

    @Override
    public boolean needsTopology() {
        return false;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_help;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_help";
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
        setUpActivity(getString(R.string.frag_help_title), false, false);
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_help, container, false);

        if (getArguments() != null && getArguments().containsKey(ARG_PAGE)) {
            setHtmlFromHelpFile(getArguments().getString(ARG_PAGE));
        } else {
            setHtmlFromHelpFile("index");
        }

        return rootView;
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
        LinearLayout linearLayout = (LinearLayout)rootView.findViewById(R.id.linear_layout);
        linearLayout.removeAllViews();

        String[] contents = getHelpFileContents(file).split("<contactlinks/>");

        for(int i = 0; i < contents.length; i++) {
            HtmlTextView htmltv = new HtmlTextView(getContext());
            htmltv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            htmltv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
            linearLayout.addView(htmltv);

            htmltv.setHtml(contents[i]);
            htmltv.setText(RichTextUtils.replaceAll((Spanned) htmltv.getText(), URLSpan.class, new RichTextUtils.URLSpanConverter(), new RichTextUtils.ClickSpan.OnClickListener() {
                @Override
                public void onClick(String url) {
                    if (mListener == null) {
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

            if(contents.length > 1 && i < contents.length - 1) {
                View view = getLayoutInflater().inflate(R.layout.help_contactlinks_view, linearLayout, true);
                view.findViewById(R.id.facebook_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.facebook_project_url)));
                        startActivity(browserIntent);
                    }
                });
                view.findViewById(R.id.twitter_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.twitter_project_url)));
                        startActivity(browserIntent);
                    }
                });
                view.findViewById(R.id.discord_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discord_server_url)));
                        startActivity(browserIntent);
                    }
                });
                view.findViewById(R.id.github_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_project_url)));
                        startActivity(browserIntent);
                    }
                });
            }
        }
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
