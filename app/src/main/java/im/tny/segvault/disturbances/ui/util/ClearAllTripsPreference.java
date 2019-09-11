package im.tny.segvault.disturbances.ui.util;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import androidx.appcompat.app.AlertDialog;
import android.util.AttributeSet;

import net.xpece.android.support.preference.DialogPreference;

import im.tny.segvault.disturbances.Application;
import im.tny.segvault.disturbances.R;
import im.tny.segvault.disturbances.model.StationUse;
import im.tny.segvault.disturbances.model.Trip;
import io.realm.Realm;

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
        dialog.setPositiveButton(getContext().getString(R.string.frag_settings_all_trips_delete_confirmation_delete), (dialog1, which) -> new ClearAllTripsTask().execute());

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
            Realm realm = Application.getDefaultRealmInstance(getContext());
            realm.executeTransaction(realm1 -> {
                realm1.where(Trip.class).findAll().deleteAllFromRealm();
                realm1.where(StationUse.class).findAll().deleteAllFromRealm();
            });
            realm.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            dialog.dismiss();
        }
    }
}
