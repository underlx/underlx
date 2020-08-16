package im.tny.segvault.disturbances.ui.map;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.Collection;

import androidx.annotation.Nullable;

import im.tny.segvault.subway.Network;
import im.tny.segvault.subway.Station;

public abstract class MapStrategy {
    abstract public void initialize(FrameLayout parent, Network.Plan map, @Nullable Bundle savedInstanceState);

    abstract public boolean isFilterable();

    public void zoomIn() {
    }

    public void zoomOut() {
    }

    private boolean canMock = false;

    public void setMockLocation(boolean allowMock) {
        canMock = allowMock;
    }

    public boolean getMockLocation() {
        return canMock;
    }

    public void onResume() {
    }

    public void onPause() {
    }

    public void onDestroy() {
    }

    public void onLowMemory() {
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
    }

    public void setFilteredStations(Collection<Station> stations) {
    }

    public void clearFilter() {
    }
}
