package im.tny.segvault.disturbances.ui.map;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.fasterxml.jackson.databind.ObjectMapper;

import androidx.annotation.Nullable;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.ui.activity.StationActivity;
import im.tny.segvault.disturbances.ui.util.CustomWebView;
import im.tny.segvault.subway.Network;

public class WebViewMapStrategy extends MapStrategy {
    private WebView webview;
    private Context context;
    private String networkId;

    public WebViewMapStrategy(Context context, String networkId) {
        this.context = context;
        this.networkId = networkId;
    }

    @Override
    public void initialize(FrameLayout parent, Network.Plan map, @Nullable Bundle savedInstanceState) {
        try {
            webview = new CustomWebView(context);
        } catch (Resources.NotFoundException e) {
            // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
            // Creating with the application Context fixes this, but is not generally recommended for view creation
            webview = new CustomWebView(context.getApplicationContext());
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webview.setLayoutParams(lp);
        parent.addView(webview);

        WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webview.addJavascriptInterface(new MapWebInterface(context), "android");

        webview.getSettings().setLoadWithOverviewMode(true);
        webview.getSettings().setSupportZoom(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setDisplayZoomControls(false);

        Network.HtmlDiagram m = (Network.HtmlDiagram) map;
        webview.getSettings().setUseWideViewPort(m.needsWideViewport());
        webview.loadUrl(m.getUrl());
    }

    @Override
    public void onResume() {
        super.onResume();
        webview.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        webview.onPause();
    }

    @Override
    public boolean isFilterable() {
        return false;
    }

    @Override
    public void zoomIn() {
        webview.zoomIn();
    }

    @Override
    public void zoomOut() {
        webview.zoomOut();
    }

    public void switchMap(Network.HtmlDiagram map) {
        webview.getSettings().setUseWideViewPort(map.needsWideViewport());
        webview.loadUrl(map.getUrl());
    }

    public class MapWebInterface {
        Context mContext;
        ObjectMapper mapper = new ObjectMapper();

        /**
         * Instantiate the interface and set the context
         */
        MapWebInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onStationClicked(String id) {
            if (getMockLocation()) {
                Network net = Coordinator.get(context).getMapManager().getNetwork(networkId);
                if (net != null) {
                    Coordinator.get(context).mockLocation(net.getStation(id));
                }
            } else {
                Intent intent = new Intent(context, StationActivity.class);
                intent.putExtra(StationActivity.EXTRA_STATION_ID, id);
                intent.putExtra(StationActivity.EXTRA_NETWORK_ID, networkId);
                context.startActivity(intent);
            }
        }
    }
}
