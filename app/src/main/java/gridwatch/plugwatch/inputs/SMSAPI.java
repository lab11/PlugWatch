package gridwatch.plugwatch.inputs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import gridwatch.plugwatch.wit.APIService;

/**
 * Created by nklugman on 6/24/16.
 */
public class SMSAPI extends BroadcastReceiver {

    public static final String SMS_BUNDLE = "pdus";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle intentExtras = intent.getExtras();
        if (intentExtras != null) {
            Object[] sms = (Object[]) intentExtras.get(SMS_BUNDLE);
            String smsBody = "";
            for (int i = 0; i < sms.length; ++i) {
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sms[i]);
                smsBody = smsMessage.getMessageBody().toString();
            }
            Log.e("SMS", smsBody);
            context.startService(new Intent(context, APIService.class).putExtra("msg", smsBody).putExtra("type", "sms"));
        }
    }
}
