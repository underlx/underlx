package im.tny.segvault.disturbances.ui.activity;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.StationUse;
import im.tny.segvault.disturbances.database.Trip;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;

public class StatsActivity extends TopActivity {

    List<Statistic> stats = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        stats.add(new TitlePseudoStatistic(R.string.act_stats_since_install));
        stats.add(new TotalTripsStatistic(0));
        stats.add(new TotalVisitsStatistic(0));
        stats.add(new TotalLengthStatistic(0));
        stats.add(new TotalTimeStatistic(0));
        stats.add(new AverageSpeedStatistic(0));
        stats.add(new AverageStationsStatistic(0));

        stats.add(new TitlePseudoStatistic(R.string.act_stats_last_7_days));
        stats.add(new TotalTripsStatistic(7));
        stats.add(new TotalVisitsStatistic(7));
        stats.add(new TotalLengthStatistic(7));
        stats.add(new TotalTimeStatistic(7));
        stats.add(new AverageSpeedStatistic(7));
        stats.add(new AverageStationsStatistic(7));

        stats.add(new TitlePseudoStatistic(R.string.act_stats_last_30_days));
        stats.add(new TotalTripsStatistic(30));
        stats.add(new TotalVisitsStatistic(30));
        stats.add(new TotalLengthStatistic(30));
        stats.add(new TotalTimeStatistic(30));
        stats.add(new AverageSpeedStatistic(30));
        stats.add(new AverageStationsStatistic(30));

        new UpdateStatsTask(this, stats, findViewById(R.id.table_layout), getLayoutInflater()).executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class UpdateStatsTask extends AsyncTask<Void, Integer, Boolean> {
        private List<Statistic> stats;
        private WeakReference<TableLayout> tableRef;
        private LayoutInflater inflater;
        private WeakReference<Context> contextRef;

        public UpdateStatsTask(Context context, List<Statistic> stats, TableLayout table, LayoutInflater inflater) {
            this.contextRef = new WeakReference<>(context);
            this.stats = stats;
            this.tableRef = new WeakReference<>(table);
            this.inflater = inflater;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            TableLayout table = tableRef.get();
            if (table != null) {
                table.removeAllViews();
            }
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            final Context context = contextRef.get();
            if (context == null) {
                return false;
            }
            AppDatabase db = Coordinator.get(context).getDB();
            db.runInTransaction(() -> {
                for (Statistic stat : stats) {
                    stat.compute(db, Coordinator.get(context).getMapManager().getNetworks());
                }
            });
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            TableLayout table = tableRef.get();
            if (table != null) {
                for (Statistic stat : stats) {
                    stat.addToTable(inflater, table);
                }
            }
        }
    }

    private abstract static class Statistic {
        abstract public void compute(AppDatabase db, Collection<Network> networks);

        abstract public int getNameStringId();

        abstract public String getValue(Context context);

        public void addToTable(LayoutInflater inflater, TableLayout table) {
            TableRow tr = (TableRow) inflater.inflate(R.layout.stat_row_template, null);
            TableRow.LayoutParams trParams = new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);

            TextView name = tr.findViewById(R.id.name_view);
            name.setText(getNameStringId());

            TextView value = tr.findViewById(R.id.value_view);
            value.setText(getValue(table.getContext()));

            table.addView(tr, new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private static class TitlePseudoStatistic extends Statistic {
        private int stringId;

        public TitlePseudoStatistic(int titleStringId) {
            stringId = titleStringId;
        }

        @Override
        public void compute(AppDatabase db, Collection<Network> networks) {
            // nothing to do here
        }

        @Override
        public int getNameStringId() {
            return stringId;
        }

        @Override
        public String getValue(Context context) {
            return null;
        }

        @Override
        public void addToTable(LayoutInflater inflater, TableLayout table) {
            TableRow tr = (TableRow) inflater.inflate(R.layout.stat_row_title_template, null);
            TableRow.LayoutParams trParams = new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);

            TextView name = tr.findViewById(R.id.name_view);
            name.setText(getNameStringId());

            table.addView(tr, new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private static class TotalTripsStatistic extends Statistic {
        int days;
        long value;

        public TotalTripsStatistic(int days) {
            this.days = days;
        }

        @Override
        public void compute(AppDatabase db, Collection<Network> networks) {
            value = getTripsFor(db, days).size();
        }

        @Override
        public int getNameStringId() {
            return R.string.act_stats_total_trips_count;
        }

        @Override
        public String getValue(Context context) {
            return Long.toString(value);
        }
    }

    private static class TotalVisitsStatistic extends Statistic {
        int days = 0;
        long value = 0;

        public TotalVisitsStatistic(int days) {
            this.days = days;
        }

        @Override
        public void compute(AppDatabase db, Collection<Network> networks) {
            for (Trip trip : getTripsFor(db, days)) {
                Network network = null;
                for (Network n : networks) {
                    if (n.getStation(db.stationUseDao().getOfTrip(trip.id).get(0).stationID) != null) {
                        network = n;
                        break;
                    }
                }
                Path path = trip.toConnectionPath(db, network);
                if (path == null) {
                    return;
                }
                List<Connection> edges = path.getEdgeList();
                if (edges.size() == 0) {
                    value++;
                }
            }
        }

        @Override
        public int getNameStringId() {
            return R.string.act_stats_total_visits_count;
        }

        @Override
        public String getValue(Context context) {
            return Long.toString(value);
        }
    }


    private static class TotalLengthStatistic extends Statistic {
        int days = 0;
        long value = 0;

        public TotalLengthStatistic(int days) {
            this.days = days;
        }

        @Override
        public void compute(AppDatabase db, Collection<Network> networks) {
            for (Trip trip : getTripsFor(db, days)) {
                Network network = null;
                for (Network n : networks) {
                    if (n.getStation(db.stationUseDao().getOfTrip(trip.id).get(0).stationID) != null) {
                        network = n;
                        break;
                    }
                }
                Path path = trip.toConnectionPath(db, network);
                if (path == null) {
                    continue;
                }
                List<Connection> edges = path.getEdgeList();
                if (edges.size() > 0) {
                    value += path.getPhysicalLength();
                }
            }
        }

        @Override
        public int getNameStringId() {
            return R.string.act_stats_total_length;
        }

        @Override
        public String getValue(Context context) {
            return String.format(context.getString(R.string.act_stats_total_length_value), (double) value / 1000f);
        }
    }

    private static class TotalTimeStatistic extends Statistic {
        int days = 0;
        long value = 0;

        public TotalTimeStatistic(int days) {
            this.days = days;
        }

        @Override
        public void compute(AppDatabase db, Collection<Network> networks) {
            for (Trip trip : getTripsFor(db, days)) {
                List<StationUse> uses = db.stationUseDao().getOfTrip(trip.id);
                Network network = null;
                for (Network n : networks) {
                    if (n.getStation(uses.get(0).stationID) != null) {
                        network = n;
                        break;
                    }
                }
                Path path = trip.toConnectionPath(db, network);
                if (path != null) {
                    value += uses.get(uses.size() - 1).leaveDate.getTime() - uses.get(0).entryDate.getTime();
                }
            }
        }

        @Override
        public int getNameStringId() {
            return R.string.act_stats_total_time;
        }

        @Override
        public String getValue(Context context) {
            long days = value / TimeUnit.DAYS.toMillis(1);
            long hours = (value % TimeUnit.DAYS.toMillis(1)) / TimeUnit.HOURS.toMillis(1);
            long minutes = (value % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1);
            if (days == 0) {
                return String.format(context.getString(R.string.act_stats_total_time_value_no_days), hours, minutes);
            } else {
                return String.format(context.getString(R.string.act_stats_total_time_value_with_days), days, hours, minutes);
            }
        }
    }

    private static List<Trip> getTripsFor(AppDatabase db, int days) {
        if (days == 0) {
            return db.tripDao().getAll();
        } else {
            return db.tripDao().getRecent(new Date(new Date().getTime() - TimeUnit.DAYS.toMillis(days)));
        }
    }

    private static class AverageSpeedStatistic extends Statistic {
        int days = 0;
        int timeableLength = 0;
        long movementMilliseconds = 0;

        public AverageSpeedStatistic(int days) {
            this.days = days;
        }

        @Override
        public void compute(AppDatabase db, Collection<Network> networks) {
            for (Trip trip : getTripsFor(db, days)) {
                Network network = null;
                for (Network n : networks) {
                    if (n.getStation(db.stationUseDao().getOfTrip(trip.id).get(0).stationID) != null) {
                        network = n;
                        break;
                    }
                }
                Path path = trip.toConnectionPath(db, network);
                if (path != null) {
                    timeableLength += path.getTimeablePhysicalLength();
                    movementMilliseconds += path.getMovementMilliseconds();
                }
            }
        }

        @Override
        public int getNameStringId() {
            return R.string.act_stats_avg_speed;
        }

        @Override
        public String getValue(Context context) {
            if (movementMilliseconds > 0) {
                return String.format(context.getString(R.string.act_stats_avg_speed_value),
                        ((double) timeableLength / (double) (movementMilliseconds / 1000)) * 3.6);
            }
            return "--";
        }
    }

    private static class AverageStationsStatistic extends Statistic {
        int days = 0;
        long stations = 0;
        long trips = 0;

        public AverageStationsStatistic(int days) {
            this.days = days;
        }

        @Override
        public void compute(AppDatabase db, Collection<Network> networks) {
            List<Trip> q = getTripsFor(db, days);
            for (Trip trip : q) {
                stations += db.stationUseDao().getOfTrip(trip.id).size();
            }
            trips = q.size();
        }

        @Override
        public int getNameStringId() {
            return R.string.act_stats_avg_stations;
        }

        @Override
        public String getValue(Context context) {
            if (trips > 0) {
                return String.format("%.01f", (double) stations / (double) trips);
            }
            return "--";
        }
    }
}
