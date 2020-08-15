package im.tny.segvault.disturbances.ui.util;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.AttributeSet;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;

import im.tny.segvault.disturbances.Coordinator;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.Util;
import im.tny.segvault.disturbances.database.AppDatabase;

public class ClearAllTripsPreference extends DialogPreference
{
    private AlertDialog dialog;
    public ClearAllTripsPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    @Override
    protected void onClick()
    {
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext());
        dialog.setTitle(getContext().getString(R.string.frag_settings_all_trips_delete_confirmation_title));
        dialog.setMessage(getContext().getString(R.string.frag_settings_all_trips_delete_confirmation_desc));
        dialog.setCancelable(true);
        dialog.setPositiveButton(getContext().getString(R.string.frag_settings_all_trips_delete_confirmation_delete), (dialog1, which) -> new ClearAllTripsTask().executeOnExecutor(Util.LARGE_STACK_THREAD_POOL_EXECUTOR));

        dialog.setNegativeButton(android.R.string.cancel, (dlg, which) -> dlg.cancel());

        AlertDialog al = dialog.create();
        this.dialog = al;
        al.show();
    }

    private class ClearAllTripsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            AppDatabase db = Coordinator.get(getContext()).getDB();
            db.runInTransaction(() -> {
                db.stationUseDao().deleteAll();
                db.tripDao().deleteAll();
            });
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            dialog.dismiss();
        }
    }
}
