package im.tny.segvault.disturbances;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Lobby;
import im.tny.segvault.subway.POI;
import im.tny.segvault.subway.Station;
import info.debatty.java.stringsimilarity.experimental.Sift4;

public class SearchContentProvider extends ContentProvider {
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
        return false;
    }

    private final static String[] columns = {
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_ICON_2,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
    };

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String query = uri.getLastPathSegment();
        final String normalizedQuery = Normalizer
                .normalize(query.toString().toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");

        final List<ResultRow> results = new ArrayList<>();

        LocaleUtil.updateResources(getContext());
        final String locale = Util.getCurrentLanguage(getContext());
        final MapManager mapm = Coordinator.get(getContext()).getMapManager();

        for (Station station : mapm.getAllStations()) {
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

            boolean addedStation = false;
            if (distance < Double.MAX_VALUE) {
                addedStation = true;
                results.add(buildResultRowForStation(station, distance));
            }

            for (Lobby lobby : station.getLobbies()) {
                if (lobby.getId().equals(query)) {
                    for (Lobby.Exit exit : lobby.getExits()) {
                        results.add(buildResultRowForExit(station, lobby, exit, -5000, null));
                    }
                    break;
                }

                for (Lobby.Exit exit : lobby.getExits()) {
                    boolean added = false;
                    for (String street : exit.streets) {
                        distance = getDistance(street, normalizedQuery);
                        if (distance < Double.MAX_VALUE) {
                            results.add(buildResultRowForExit(station, lobby, exit, distance, street));
                            added = true;
                            break;
                        }
                    }
                    // do not add the same exit twice
                    if (added) break;
                }
            }

            // let users search for the name of some station features
            if(!addedStation) {
                for (String tag : station.getAllTags()) {
                    if(query.equals(tag)) {
                        results.add(buildResultRowForStation(station, distance));
                        break;
                    }
                    switch (tag) {
                        case "a_store":
                        case "a_wc":
                        case "c_airport":
                        case "c_bike":
                        case "c_boat":
                        case "c_bus":
                        case "c_parking":
                        case "c_taxi":
                        case "c_train":
                        case "s_lostfound":
                        case "s_ticket1":
                        case "s_ticket2":
                        case "s_ticket3":
                        case "s_urgent_pass":
                            distance = getDistance(Util.getStringForStationTag(getContext(), tag), normalizedQuery);
                            break;
                        default:
                            continue;
                    }
                    if (distance < Double.MAX_VALUE) {
                        results.add(buildResultRowForStation(station, distance));
                    }
                }
            }
        }

        for (Line line : mapm.getAllLines()) {
            double distance = Double.MAX_VALUE;
            if (line.getId().equals(query)) {
                distance = -5000; // push to top of results
            }

            for (String name : Util.getLineNames(getContext(), line)) {
                double thisDistance = getDistance(name, normalizedQuery);
                if (thisDistance < distance) {
                    distance = thisDistance;
                }
            }

            if (distance < Double.MAX_VALUE) {
                ResultRow row = new ResultRow();
                row.title = Util.getLineNames(getContext(), line)[0];
                row.subtitle = String.format(getContext().getString(R.string.search_line_subtitle), Util.getNetworkNames(getContext(), line.getNetwork())[0]);
                row.drawable = Util.getDrawableResourceIdForLineId(line.getId());
                row.intentData = "line:" + line.getId();
                row.distance = distance;
                results.add(row);
            }
        }

        for (POI poi : mapm.getAllPOIs()) {
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
                row.subtitle = String.format("%s \u2022 %s", getContext().getString(R.string.search_poi_subtitle), getContext().getString(Util.getStringResourceIdForPOIType(poi.getType())));
                row.drawable = R.drawable.ic_place_black_24dp;
                row.drawable2 = Util.getDrawableResourceIdForPOIType(poi.getType());
                row.intentData = "poi:" + poi.getId();
                row.distance = distance;
                results.add(row);
            }
        }

        // TODO search train services, bus services, trivia, etc.

        Collections.sort(results, (row, t1) -> Double.compare(row.distance, t1.distance));

        if (results.size() == 0) {
            ResultRow row = new ResultRow();
            row.title = getContext().getString(R.string.search_no_results);
            row.drawable = R.drawable.ic_sentiment_dissatisfied_black_24dp;
            row.intentData = "no-results";
            row.distance = 0;
            results.add(row);
        }

        MatrixCursor cursor = new MatrixCursor(columns);

        int i = 0;
        for (ResultRow row : results) {
            Object[] cursorRow = {i++,
                    row.title,
                    row.subtitle,
                    row.drawable,
                    row.drawable2,
                    row.intentData};
            cursor.addRow(cursorRow);
        }

        return cursor;
    }

    private ResultRow buildResultRowForStation(Station station, double distance) {
        ResultRow row = new ResultRow();
        row.title = station.getName();
        row.subtitle = String.format(getContext().getString(R.string.search_station_subtitle), Util.getNetworkNames(getContext(), station.getNetwork())[0]);
        row.drawable = R.drawable.network_pt_ml;
        row.intentData = "station:" + station.getId();
        row.distance = distance;
        return row;
    }

    private ResultRow buildResultRowForExit(Station station, Lobby lobby, Lobby.Exit exit, double distance, String matchStreet) {
        ResultRow row = new ResultRow();
        if (matchStreet == null) {
            row.title = exit.getExitsString();
        } else {
            // like exit.getExitsString() but always get our match first
            List<String> otherStreets = new ArrayList<>(exit.streets);
            otherStreets.remove(matchStreet);
            otherStreets.add(0, matchStreet);
            row.title = TextUtils.join(", ", otherStreets);
        }
        if (lobby.isAlwaysClosed()) {
            row.subtitle = String.format(getContext().getString(R.string.search_closed_exit_subtitle), station.getName());
        } else {
            row.subtitle = String.format(getContext().getString(R.string.search_exit_subtitle), station.getName());
        }
        row.drawable = Util.getDrawableResourceIdForExitType(exit.type, lobby.isAlwaysClosed());
        row.intentData = "station:" + station.getId() + ":lobby:" + lobby.getId() + ":exit:" + Integer.toString(exit.id);
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

        String[] normWords = norm.split(" ");
        for (String word : normWords) {
            if (Math.min(normalizedQuery.length(), word.length()) < 3) {
                continue;
            }
            word = word.substring(0, Math.min(normalizedQuery.length(), word.length()));

            double distance = sift4.distance(word, normalizedQuery);
            if (distance < 3.0) {
                return distance;
            }
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
        int drawable2;
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
}
