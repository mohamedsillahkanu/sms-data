package sl.icf.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Listens for incoming SMS. Messages that start with the field app prefix
 * (ITN1* by default) are saved in the queue and an upload is scheduled.
 * Long messages arrive in several parts; they are joined here.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;

        Map<String, StringBuilder> bySender = new LinkedHashMap<>();
        for (SmsMessage part : parts) {
            if (part == null) continue;
            String sender = part.getDisplayOriginatingAddress();
            if (sender == null) sender = "";
            StringBuilder b = bySender.get(sender);
            if (b == null) {
                b = new StringBuilder();
                bySender.put(sender, b);
            }
            String text = part.getDisplayMessageBody();
            if (text != null) b.append(text);
        }

        String prefix = Config.prefix(ctx).toUpperCase();
        long now = System.currentTimeMillis();
        boolean queued = false;
        for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
            String body = e.getValue().toString().trim();
            if (prefix.isEmpty() || body.toUpperCase().startsWith(prefix)) {
                Db.get(ctx).add(e.getKey(), body, now);
                queued = true;
            }
        }
        if (queued) UploadJob.schedule(ctx);
    }
}
