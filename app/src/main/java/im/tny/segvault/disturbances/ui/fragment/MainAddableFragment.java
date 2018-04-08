package im.tny.segvault.disturbances.ui.fragment;

public interface MainAddableFragment {
    boolean needsTopology();
    boolean isScrollable();
    int getNavDrawerId();
    String getNavDrawerIdAsString();
}
