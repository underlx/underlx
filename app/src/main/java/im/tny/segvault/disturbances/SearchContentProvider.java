package im.tny.segvault.disturbances;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.transform.Result;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.POI;
import im.tny.segvault.subway.Station;
import info.debatty.java.stringsimilarity.experimental.Sift4;

public class SearchContentProvider extends ContentProvider {
    private MainService mainService;
    private boolean serviceBound = false;
    private Sift4 sift4 = new Sift4();

    public SearchContentProvider() {
        sift4.setMaxOffset(5);
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/result";
    }

    @Override
    public boolean onCreate() {
        getContext().startService(new Intent(getContext(), MainService.class));
        getContext().bindService(new Intent(getContext(), MainService.class), mConnection, Context.BIND_AUTO_CREATE);
        return false;
    }

    private final static String[] columns = {
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
    };

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (!serviceBound) {
            return null;
        }
        String query = uri.getLastPathSegment();
        final String normalizedQuery = Normalizer
                .normalize(query.toString().toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");

        final List<ResultRow> results = new ArrayList<>();

        final String locale = Util.getCurrentLocale(getContext()).getLanguage();

        for (Station station : mainService.getAllStations()) {
            double distance = getDistance(station.getName(), normalizedQuery);
            if (station.getId().equals(query)) {
                distance = -5000; // push to top of results
            }

            for (String altName : station.getAltNames()) {
                double altDistance = getDistance(altName, normalizedQuery);
                if (altDistance < distance) {
                    distance = altDistance;
                }
            }

            if (distance < Double.MAX_VALUE) {
                ResultRow row = new ResultRow();
                row.title = station.getName();
                row.subtitle = String.format(getContext().getString(R.string.search_station_subtitle), station.getNetwork().getName());
                row.drawable = R.drawable.network_pt_ml;
                row.intentData = "station:" + station.getId();
                row.distance = distance;
                results.add(row);
            }

            for (Lobby lobby : station.getLobbies()) {
                if (lobby.getId().equals(query)) {
                    for (Lobby.Exit exit : lobby.getExits()) {
                        results.add(buildResultRowForExit(station, lobby, exit, -5000));
                    }
                    break;
                }

                for (Lobby.Exit exit : lobby.getExits()) {
                    boolean added = false;
                    for (String street : exit.streets) {
                        distance = getDistance(street, normalizedQuery);
                        if (distance < Double.MAX_VALUE) {
                            results.add(buildResultRowForExit(station, lobby, exit, distance));
                            added = true;
                            break;
                        }
                    }
                    // do not add the same exit twice
                    if (added) break;
                }
            }
        }

        for (Line line : mainService.getAllLines()) {
            double distance = getDistance(line.getName(), normalizedQuery);
            if (line.getId().equals(query)) {
                distance = -5000; // push to top of results
            }

            if (distance < Double.MAX_VALUE) {
                ResultRow row = new ResultRow();
                row.title = line.getName();
                row.subtitle = String.format(getContext().getString(R.string.search_line_subtitle), line.getNetwork().getName());
                row.drawable = Util.getDrawableResourceIdForLineId(line.getId());
                row.intentData = "line:" + line.getId();
                row.distance = distance;
                results.add(row);
            }
        }

        for (POI poi : mainService.getAllPOIs()) {
            double distance = Double.MAX_VALUE;
            // it's unlikely anyone will search by POI ID, but let's support it anyway
            if (poi.getId().equals(query)) {
                distance = -5000; // push to top of results
            }

            for (String name : poi.getNames(locale)) {
                double thisDistance = getDistance(name, normalizedQuery);
                if (thisDistance < distance) {
                    distance = thisDistance;
                }
            }
            if (distance < Double.MAX_VALUE) {
                ResultRow row = new ResultRow();
                row.title = poi.getNames(locale)[0];
                row.subtitle = getContext().getString(R.string.search_poi_subtitle);
                row.drawable = R.drawable.ic_place_black_24dp;
                row.intentData = "poi:" + poi.getId();
                row.distance = distance;
                results.add(row);
            }
        }

        // TODO search train services, bus services, trivia, etc.

        Collections.sort(results, new Comparator<ResultRow>() {
            @Override
            public int compare(ResultRow row, ResultRow t1) {
                return Double.compare(row.distance, t1.distance);
            }
        });

        MatrixCursor cursor = new MatrixCursor(columns);

        int i = 0;
        for (ResultRow row : results) {
            Object[] cursorRow = {i++,
                    row.title,
                    row.subtitle,
                    row.drawable,
                    row.intentData};
            cursor.addRow(cursorRow);
        }

        return cursor;
    }

    private ResultRow buildResultRowForExit(Station station, Lobby lobby, Lobby.Exit exit, double distance) {
        ResultRow row = new ResultRow();
        row.title = exit.getExitsString();
        row.subtitle = String.format(getContext().getString(R.string.search_exit_subtitle), station.getName());
        row.drawable = R.drawable.map_marker_exit;
        if (lobby.isAlwaysClosed()) {
            row.subtitle = String.format(getContext().getString(R.string.search_closed_exit_subtitle), station.getName());
            row.drawable = R.drawable.map_marker_exit_closed;
        }
        row.intentData = "station:" + station.getId() + ":lobby:" + lobby.getId() + ":exit:" + Float.toString(exit.worldCoord[0]) + "," + Float.toString(exit.worldCoord[1]);
        row.distance = distance + 10; // apply small bias so that stations come first. Example of problematic case: "Alameda"
        return row;
    }

    private double getDistance(String possibleMatch, String normalizedQuery) {
        String norm = Normalizer
                .normalize(possibleMatch, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "").toLowerCase();
        int indexOf = norm.indexOf(normalizedQuery);
        if (indexOf >= 0) {
            return -1000.0 + indexOf;
        }

        norm = norm.substring(0, Math.min(normalizedQuery.length(), norm.length()));

        double distance = sift4.distance(norm, normalizedQuery);
        if (distance < 3.0) {
            return distance;
        }
        return Double.MAX_VALUE;
    }

    private class ResultRow {
        String title;
        String subtitle;
        int drawable;
        String intentData;
        double distance;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not supported");
    }

    private MainServiceConnection mConnection = new MainServiceConnection();

    class MainServiceConnection implements ServiceConnection {
        MainService.LocalBinder binder;

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            binder = (MainService.LocalBinder) service;
            mainService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serviceBound = false;
        }

        public MainService.LocalBinder getBinder() {
            return binder;
        }
    }
}