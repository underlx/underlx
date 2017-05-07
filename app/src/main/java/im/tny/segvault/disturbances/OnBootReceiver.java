package im.tny.segvault.disturbances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by gabriel on 5/7/17.
 */

public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        /***** For start Service  ****/
        Intent myIntent = new Intent(context, MainService.class);
        context.startService(myIntent);
    }
}
