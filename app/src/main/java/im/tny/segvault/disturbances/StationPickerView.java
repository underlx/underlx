package im.tny.segvault.disturbances;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.WorkerThread;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.subway.Line;
import im.tny.segvault.subway.Station;
import info.debatty.java.stringsimilarity.experimental.Sift4;
import io.realm.Realm;

public class StationPickerView extends LinearLayout {
    private InstantAutoComplete textView;
    private ImageButton clearButton;
    private AutoCompleteStationsAdapter adapter = null;
    private OnStationSelectedListener onStationSelectedListener = null;
    private OnSelectionLostListener onSelectionLostListener = null;
    private AllStationsSortStrategy allStationsSortStrategy = new AZSortStrategy();

    private Station selection = null;
    // weakSelection: if true, selection should clear once the textbox gets focus
    private boolean weakSelection = false;

    public Station getSelection() {
        return selection;
    }

    public void setSelection(Station station) {
        if (adapter.stations.contains(station)) {
            textView.setText(station.getName());
            selection = station;
            weakSelection = false;
            if (onStationSelectedListener != null) {
                onStationSelectedListener.onStationSelected(selection);
            }
        }
    }

    public void setWeakSelection(Station station) {
        setSelection(station);
        weakSelection = true;
    }

    public void setSelectionById(String id) {
        if (adapter == null) {
            return;
        }
        for (Station s : adapter.stations) {
            if (s.getId().equals(id)) {
                setSelection(s);
                break;
            }
        }
    }

    public void setWeakSelectionById(String id) {
        setSelectionById(id);
        weakSelection = true;
    }

    public StationPickerView(Context context) {
        super(context);
        initializeViews(context);
    }

    public StationPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeViews(context);
        setHintFromAttrs(attrs);
    }

    public StationPickerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initializeViews(context);
        setHintFromAttrs(attrs);
    }

    public void setStations(List<Station> stations) {
        adapter = new AutoCompleteStationsAdapter(getContext(), stations, R.id.text_station);
        textView.setAdapter(adapter);
    }

    /**
     * Inflates the views in the layout.
     *
     * @param context the current context for the view.
     */
    private void initializeViews(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.station_picker_view, this);

        textView = (InstantAutoComplete) findViewById(R.id.text_station);
        clearButton = (ImageButton) findViewById(R.id.button_clear);

        textView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View arg1, int pos,
                                    long id) {
                selection = (Station) parent.getItemAtPosition(pos);
                weakSelection = false;
                if (onStationSelectedListener != null) {
                    onStationSelectedListener.onStationSelected(selection);
                }
            }
        });
        clearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                textView.setText("");
                focusOnEntry();
            }
        });

        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                selection = null;
                if (onSelectionLostListener != null) {
                    onSelectionLostListener.onSelectionLost();
                }
                weakSelection = false;
                showHideClearButton();
            }
        });
        textView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (weakSelection && hasFocus) {
                    textView.setText("");
                }
                showHideClearButton();
            }
        });
    }

    private void showHideClearButton() {
        if (textView.getEditableText().length() > 0 && textView.isFocused()) {
            clearButton.setVisibility(VISIBLE);
        } else {
            clearButton.setVisibility(GONE);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    private void setHintFromAttrs(AttributeSet attrs) {
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.StationPickerView);
        String hint = ta.getString(R.styleable.StationPickerView_hint);
        ((TextInputLayout) findViewById(R.id.input_station)).setHint(hint);
        ta.recycle();
    }

    class StationsFilter extends Filter {
        AutoCompleteStationsAdapter adapter;
        List<Station> originalList;

        Sift4 sift4 = new Sift4();

        public StationsFilter(AutoCompleteStationsAdapter adapter, List<Station> originalList) {
            super();
            this.adapter = adapter;
            this.originalList = originalList;
            sift4.setMaxOffset(5);
        }

        @Override
        @WorkerThread
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Station> filteredList = new ArrayList<>();
            final FilterResults results = new FilterResults();

            final Map<Station, Double> distances = new HashMap<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(originalList);
                allStationsSortStrategy.sortStations(filteredList);
            } else {
                final String filterPattern = Normalizer
                        .normalize(constraint.toString().toLowerCase().trim(), Normalizer.Form.NFD)
                        .replaceAll("[^\\p{ASCII}]", "");

                for (final Station station : originalList) {
                    String norm = Normalizer
                            .normalize(station.getName(), Normalizer.Form.NFD)
                            .replaceAll("[^\\p{ASCII}]", "").toLowerCase();
                    int indexOf = norm.indexOf(filterPattern);
                    if (indexOf >= 0) {
                        filteredList.add(station);
                        distances.put(station, -1000.0 + indexOf);
                        continue;
                    }

                    norm = norm.substring(0, Math.min(filterPattern.length(), norm.length()));

                    double distance = sift4.distance(norm, filterPattern);
                    if (distance < 3.0) {
                        filteredList.add(station);
                        distances.put(station, distance);
                    }
                }
                Collections.sort(filteredList, new Comparator<Station>() {
                    @Override
                    public int compare(Station station, Station t1) {
                        return distances.get(station).compareTo(distances.get(t1));
                    }
                });
            }
            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (results.values != null) {
                adapter.filteredStations = (ArrayList<Station>) results.values;
                adapter.notifyDataSetChanged();
            }
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            return ((Station) resultValue).getName();
        }
    }

    class AutoCompleteStationsAdapter extends ArrayAdapter<Station> {
        private final List<Station> stations;

        private List<Station> filteredStations = new ArrayList<>();

        public AutoCompleteStationsAdapter(Context context, List<Station> stations, int textViewResourceId) {
            super(context, 0, textViewResourceId);
            this.stations = stations;
        }

        @Override
        public int getCount() {
            return filteredStations.size();
        }

        @Override
        public Filter getFilter() {
            return new StationsFilter(this, stations);
        }

        @Override
        public Station getItem(int index) {
            return filteredStations.get(index);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item from filtered list.
            Station station = filteredStations.get(position);
            List<Line> lines = new ArrayList<>(station.getLines());
            Collections.sort(lines, Collections.reverseOrder(new Comparator<Line>() {
                @Override
                public int compare(Line l1, Line l2) {
                    return l1.getName().compareTo(l2.getName());
                }
            }));

            Line line = lines.get(0);

            int color = line.getColor();

            // Inflate your custom row layout as usual.
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.row_station, parent, false);

            TextView name = (TextView) convertView.findViewById(R.id.text_name);
            name.setText(station.getName());

            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.circle);
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);

            FrameLayout icon = (FrameLayout) convertView.findViewById(R.id.frame_circle);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                icon.setBackgroundDrawable(drawable);
            } else {
                icon.setBackground(drawable);
            }

            ImageView subicon = (ImageView) convertView.findViewById(R.id.image_icon);

            Drawable mWrappedDrawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLine(line)).mutate();
            mWrappedDrawable = DrawableCompat.wrap(mWrappedDrawable);
            DrawableCompat.setTint(mWrappedDrawable, Color.WHITE);
            DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN);

            subicon.setImageDrawable(mWrappedDrawable);

            if (station.getLines().size() > 1) {
                line = lines.get(1);

                color = line.getColor();

                drawable = ContextCompat.getDrawable(getContext(), R.drawable.circle);
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);

                Bitmap bmp = getBitmapFromVectorDrawable(getContext(), drawable, 2, 1);

                icon = (FrameLayout) convertView.findViewById(R.id.frame_half_circle);
                icon.setVisibility(VISIBLE);
                if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    icon.setBackgroundDrawable(new BitmapDrawable(getResources(), bmp));
                } else {
                    icon.setBackground(new BitmapDrawable(getResources(), bmp));
                }

                subicon = (ImageView) convertView.findViewById(R.id.image_half_icon);

                mWrappedDrawable = ContextCompat.getDrawable(getContext(), Util.getDrawableResourceIdForLine(line)).mutate();
                mWrappedDrawable = DrawableCompat.wrap(mWrappedDrawable);
                DrawableCompat.setTint(mWrappedDrawable, Color.WHITE);
                DrawableCompat.setTintMode(mWrappedDrawable, PorterDuff.Mode.SRC_IN);

                bmp = getBitmapFromVectorDrawable(getContext(), mWrappedDrawable, 2, 1);
                subicon.setImageBitmap(bmp);
            }
            return convertView;
        }
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, Drawable drawable, int widthDiv, int heightDiv) {
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        canvas.clipRect(0, 0, canvas.getWidth() / widthDiv, canvas.getHeight() / heightDiv);
        drawable.draw(canvas);

        return bitmap;
    }


    @Override
    public boolean isFocused() {
        return textView.isFocused();
    }

    public boolean focusOnEntry() {
        textView.setFocusableInTouchMode(true);
        return textView.requestFocus();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        String stationId = "";
        if (selection != null) {
            stationId = selection.getId();
        }
        return new SavedState(super.onSaveInstanceState(), stationId, textView.getText().toString());
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        textView.setText(savedState.getInput());
        if (!savedState.getStationId().isEmpty()) {
            setSelectionById(savedState.getStationId());
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray container) {
        super.dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray container) {
        super.dispatchThawSelfOnly(container);
    }

    protected static class SavedState extends BaseSavedState {
        private final String stationId;
        private final String input;

        private SavedState(Parcelable superState, String stationId, String input) {
            super(superState);
            this.stationId = stationId;
            this.input = input;
        }

        private SavedState(Parcel in) {
            super(in);
            stationId = in.readString();
            input = in.readString();
        }

        public String getStationId() {
            return stationId;
        }

        public String getInput() {
            return input;
        }

        @Override
        public void writeToParcel(Parcel destination, int flags) {
            super.writeToParcel(destination, flags);
            destination.writeString(stationId);
            destination.writeString(input);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }

        };

    }

    public void setOnStationSelectedListener(OnStationSelectedListener listener) {
        onStationSelectedListener = listener;
    }

    public void setOnSelectionLostListener(OnSelectionLostListener listener) {
        onSelectionLostListener = listener;
    }

    public void setAllStationsSortStrategy(AllStationsSortStrategy strategy) {
        allStationsSortStrategy = strategy;
    }

    public interface OnStationSelectedListener {
        void onStationSelected(Station station);
    }

    public interface OnSelectionLostListener {
        void onSelectionLost();
    }

    public interface AllStationsSortStrategy {
        void sortStations(List<Station> stations);
    }

    public static class AZSortStrategy implements AllStationsSortStrategy {
        @Override
        public void sortStations(List<Station> stations) {
            Collections.sort(stations, new Comparator<Station>() {
                @Override
                public int compare(Station station, Station t1) {
                    return station.getName().compareTo(t1.getName());
                }
            });
        }
    }

    private static abstract class RealmBasedSortStrategy implements AllStationsSortStrategy {
        protected Realm realm = null;
        protected Map<String, Double> scores = new HashMap<>();
        @Override
        public void sortStations(List<Station> stations) {
            // ensure this is created in the right thread
            realm = Realm.getDefaultInstance();
            Collections.sort(stations, new Comparator<Station>() {
                @Override
                public int compare(Station station, Station t1) {
                    // order by decreasing score, then A-Z
                    int result = Double.compare(getScore(t1), getScore(station));
                    if (result == 0) {
                        return station.getName().compareTo(t1.getName());
                    }
                    return result;
                }
            });
            realm.close();
        }

        abstract protected double getScore(Station station);
    }

    public static class EnterFrequencySortStrategy extends RealmBasedSortStrategy {
        protected double getScore(Station station) {
            Double score = scores.get(station.getId());
            if (score == null) {
                long entryCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.NETWORK_ENTRY.name()).count();
                score = new Double(entryCount);
                scores.put(station.getId(), score);
            }
            return score;
        }
    }

    public static class ExitFrequencySortStrategy extends RealmBasedSortStrategy {
        protected double getScore(Station station) {
            Double score = scores.get(station.getId());
            if (score == null) {
                long exitCount = realm.where(StationUse.class).equalTo("station.id", station.getId()).equalTo("type", StationUse.UseType.NETWORK_EXIT.name()).count();
                score = new Double(exitCount);
                scores.put(station.getId(), score);
            }
            return score;
        }
    }
}
