package gridwatch.plugwatch.inputs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;

import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.wit.APIService;

/**
 * Created by nklugman on 6/24/16.
 */
public class SMSAPI extends BroadcastReceiver {

    public static final String SMS_BUNDLE = "pdus";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("sms", "rx");
        Bundle intentExtras = intent.getExtras();
        if (intentExtras != null) {
            Object[] sms = (Object[]) intentExtras.get(SMS_BUNDLE);
            String smsBody = "";
            for (int i = 0; i < sms.length; ++i) {
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sms[i]);
                smsBody = smsMessage.getMessageBody().toString();
            }
            try {
                Log.e("sms", "body:" + smsBody);
                if (smsBody.contains("Current Data")) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    try {
                        String r = smsBody.split(" ")[3].replace("MB.", "");
                        Log.e("sms: data balance", r);
                        sp.edit().putString(SettingsConfig.INTERNET, r).commit();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else {
                    String cmd = smsBody.split(",")[2];
                    String group_id = smsBody.split(",")[1];
                    String phone_id = smsBody.split(",")[0];
                    Intent a = new Intent(context, APIService.class);
                    a.putExtra(IntentConfig.INCOMING_API_COMMAND, cmd);
                    a.putExtra(IntentConfig.INCOMING_API_PHONE_ID, phone_id);
                    a.putExtra(IntentConfig.INCOMING_API_GROUP_ID, group_id);
                    a.putExtra(IntentConfig.IS_TEXT, IntentConfig.IS_TEXT);
                    context.startService(a);
                }
            } catch (Exception e){
                FirebaseCrash.log(e.getMessage());

            }
        }
    }
}
