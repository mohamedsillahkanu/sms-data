package sl.icf.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the upload jobs after the phone is switched on. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            UploadJob.schedule(ctx);
        }
    }
}
