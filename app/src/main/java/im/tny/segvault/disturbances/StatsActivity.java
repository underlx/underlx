package im.tny.segvault.disturbances;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import im.tny.segvault.disturbances.model.Trip;
import im.tny.segvault.s2ls.Path;
import im.tny.segvault.subway.Connection;
import im.tny.segvault.subway.Network;
import io.realm.Realm;

public class StatsActivity extends TopActivity {

    MainService locService;
    boolean locBound = false;

    List<Statistic> stats = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        Object conn = getLastCustomNonConfigurationInstance();
        if (conn != null) {
            // have the service connection survive through activity configuration changes
            // (e.g. screen orientation changes)
            mConnection = (StatsActivity.LocServiceConnection) conn;
            locService = mConnection.getBinder().getService();
            locBound = true;
        } else if (!locBound) {
            startService(new Intent(this, MainService.class));
            getApplicationContext().bindService(new Intent(getApplicationContext(), MainService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // TODO
        stats.add(new TotalTripsStatistic());
        stats.add(new TotalVisitsStatistic());
        stats.add(new TotalLengthStatistic());
        stats.add(new TotalTimeStatistic());
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

    private class UpdateStatsTask extends AsyncTask<Void, Integer, Boolean> {
        private List<Statistic> stats;
        private TableLayout table;
        private LayoutInflater inflater;

        public UpdateStatsTask(List<Statistic> stats, TableLayout table, LayoutInflater inflater) {
            this.stats = stats;
            this.table = table;
            this.inflater = inflater;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            table.removeAllViews();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Realm realm = Realm.getDefaultInstance();
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    for (Statistic stat : stats) {
                        stat.compute(realm, locService.getNetworks());
                    }
                }
            });
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            for (Statistic stat : stats) {
                stat.addToTable(inflater, table);
            }
        }
    }

    private abstract static class Statistic {
        abstract public void compute(Realm realm, Collection<Network> networks);
        abstract public int getNameStringId();
        abstract public String getValue(Context context);

        public void addToTable(LayoutInflater inflater, TableLayout table) {
            TableRow tr = (TableRow) inflater.inflate(R.layout.stat_row_template, null);
            TableRow.LayoutParams trParams = new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT);

            TextView name = (TextView)tr.findViewById(R.id.name_view);
            name.setText(getNameStringId());

            TextView value = (TextView)tr.findViewById(R.id.value_view);
            value.setText(getValue(table.getContext()));

            table.addView(tr, new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private static class TotalTripsStatistic extends Statistic {
        long value;
        @Override
        public void compute(Realm realm, Collection<Network> networks) {
            value = realm.where(Trip.class).count();
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
        long value = 0;
        @Override
        public void compute(Realm realm, Collection<Network> networks) {

            for (Trip trip : realm.where(Trip.class).findAll()) {
                Network network = null;
                for (Network n : networks) {
                    if (n.getId().equals(trip.getPath().get(0).getStation().getNetwork())) {
                        network = n;
                        break;
                    }
                }
                Path path = trip.toConnectionPath(network);
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
        long value = 0;
        @Override
        public void compute(Realm realm, Collection<Network> networks) {

            for (Trip trip : realm.where(Trip.class).findAll()) {
                Network network = null;
                for (Network n : networks) {
                    if (n.getId().equals(trip.getPath().get(0).getStation().getNetwork())) {
                        network = n;
                        break;
                    }
                }
                Path path = trip.toConnectionPath(network);
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
            return String.format(context.getString(R.string.act_stats_total_length_value), (double)value / 1000f);
        }
    }

    private static class TotalTimeStatistic extends Statistic {
        long value = 0;
        @Override
        public void compute(Realm realm, Collection<Network> networks) {

            for (Trip trip : realm.where(Trip.class).findAll()) {
                Network network = null;
                for (Network n : networks) {
                    if (n.getId().equals(trip.getPath().get(0).getStation().getNetwork())) {
                        network = n;
                        break;
                    }
                }
                Path path = trip.toConnectionPath(network);
                List<Connection> edges = path.getEdgeList();
                if (edges.size() > 0) {
                    value += trip.getPath().get(trip.getPath().size() - 1).getLeaveDate().getTime() -  trip.getPath().get(0).getEntryDate().getTime();
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

    private LocServiceConnection mConnection = new LocServiceConnection();

    private class LocServiceConnection implements ServiceConnection {
        MainService.LocalBinder binder;

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            binder = (MainService.LocalBinder) service;
            locService = binder.getService();
            locBound = true;

            new UpdateStatsTask(stats, (TableLayout)findViewById(R.id.table_layout), getLayoutInflater()).execute();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            locBound = false;
        }

        public MainService.LocalBinder getBinder() {
            return binder;
        }
    }
}
