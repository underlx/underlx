package im.tny.segvault.disturbances.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.database.AppDatabase;
import im.tny.segvault.disturbances.database.NotificationRule;
import im.tny.segvault.disturbances.ui.util.CustomFAB;

public class NotifScheduleActivity extends TopActivity {
    private ListView listView;
    private SimpleAdapter adapter;
    private List<Map<String, String>> data = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notif_schedule);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        listView = findViewById(R.id.rules_view);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String ruleId = ((Map<String, String>) parent.getItemAtPosition(position)).get("id");
            if (ruleId != null) {
                Intent intent = new Intent(NotifScheduleActivity.this, EditNotifScheduleActivity.class);
                intent.putExtra(EditNotifScheduleActivity.EXTRA_RULE_ID, ruleId);
                startActivity(intent);
            }
        });

        adapter = new SimpleAdapter(NotifScheduleActivity.this, data,
                android.R.layout.simple_list_item_1,
                new String[]{"desc"},
                new int[]{android.R.id.text1});
        listView.setAdapter(adapter);

        CustomFAB fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(NotifScheduleActivity.this, EditNotifScheduleActivity.class);
            startActivity(intent);
        });

        AppDatabase db = Coordinator.get(this).getDB();
        db.notificationRuleDao().getAllLive().observe(this, notificationRules -> {
            data.clear();
            for (NotificationRule rule : notificationRules) {
                Map<String, String> item = new HashMap<>(2);
                item.put("desc", rule.getDescription(NotifScheduleActivity.this));
                item.put("id", rule.id);
                data.add(item);
            }

            if(data.size() == 0) {
                listView.setVisibility(View.GONE);
                findViewById(R.id.empty_view).setVisibility(View.VISIBLE);
            } else {
                findViewById(R.id.empty_view).setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
            }
            adapter.notifyDataSetChanged();
        });
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
