package im.tny.segvault.disturbances.ui.fragment.top;

import android.content.Context;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.Locale;

import im.tny.segvault.disturbances.CacheManager;
import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.InternalLinkHandler;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.ui.fragment.TopFragment;
import im.tny.segvault.disturbances.ui.util.RichTextUtils;

public class InfraFragment extends TopFragment {
    private static final String ARG_PAGE = "page";
    private OnFragmentInteractionListener mListener;

    private View rootView;

    public InfraFragment() {
        // Required empty public constructor
    }

    @Override
    public boolean needsTopology() {
        // the infra HTMLs are downloaded along with the network map
        return true;
    }

    @Override
    public int getNavDrawerId() {
        return R.id.nav_infrastructure;
    }

    @Override
    public String getNavDrawerIdAsString() {
        return "nav_infrastructure";
    }

    public static InfraFragment newInstance() {
        InfraFragment fragment = new InfraFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public static InfraFragment newInstance(String page) {
        InfraFragment fragment = new InfraFragment();
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
        setUpActivity(getString(R.string.frag_infrastructure_title), false, false);
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_help, container, false);

        if (getArguments() != null && getArguments().containsKey(ARG_PAGE)) {
            setHtmlFromInfraFile(getArguments().getString(ARG_PAGE));
        } else {
            setHtmlFromInfraFile("index");
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

    private void setHtmlFromInfraFile(String file) {
        LinearLayout linearLayout = rootView.findViewById(R.id.linear_layout);
        linearLayout.removeAllViews();

        String contents = getInfraFileContents(file);

        HtmlTextView htmltv = new HtmlTextView(getContext());
        htmltv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        htmltv.setTextAppearance(getContext(), android.R.style.TextAppearance_Medium);
        linearLayout.addView(htmltv);

        htmltv.setHtml(contents);
        htmltv.setText(RichTextUtils.replaceAll((Spanned) htmltv.getText(), URLSpan.class, new RichTextUtils.URLSpanConverter(), new InternalLinkHandler(getContext(), mListener)));
    }

    private String getPathForInfraFile(String file) {
        Locale l = Util.getCurrentLocale(getContext());
        return String.format("infra/%s/%s.html", l.getLanguage(), file);
    }

    private String getInfraFileContents(String file) {
        CacheManager dm = Coordinator.get(getContext()).getDataManager();
        Util.OverlaidFile overlaid = dm.get(String.format("overlayfs/%s", getPathForInfraFile(file)), Util.OverlaidFile.class);
        if (overlaid != null) {
            return overlaid.contents;
        }
        return getString(R.string.frag_infrastructure_unavailable);
    }

    public interface OnFragmentInteractionListener extends OnInteractionListener, InternalLinkHandler.FallbackLinkHandler {

    }
}
