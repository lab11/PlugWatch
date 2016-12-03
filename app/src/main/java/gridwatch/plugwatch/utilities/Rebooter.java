package gridwatch.plugwatch.utilities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.logs.RebootCauseWriter;
import gridwatch.plugwatch.logs.RestartNumWriter;
import gridwatch.plugwatch.wit.PlugWatchService;

/**
 * Created by nklugman on 11/17/16.
 */

public class Rebooter {

    Context mContext;
    RestartNumWriter numWriter;
    Throwable cause;
    String class_name;


    public Rebooter(Context context, String calling_class_name, Throwable n) {
        mContext = context;
        numWriter = new RestartNumWriter(context);
        n.printStackTrace();
        cause = n;
        class_name = calling_class_name;
        setup_reboot();

    }

    private void setup_reboot() {

        //we look at the reboot number...
        //if this number is greater than a threshold reset it
        //use this number to multiply a time threshold to schedule the reboot



            int num_previous_reboots_cnt = Integer.valueOf(numWriter.get_last_value());
            int incremented_num_previous_reboots = num_previous_reboots_cnt + 1;
            Log.e("rebooter", "num previous reboots: " + String.valueOf(num_previous_reboots_cnt));
            Log.e("rebooter", "threshold is: " + String.valueOf(incremented_num_previous_reboots * SensorConfig.REBOOT_MIN_WAIT));
            Log.e("rebooter", "time is: " + String.valueOf(System.currentTimeMillis()));

            if (incremented_num_previous_reboots > SensorConfig.MAX_NUM_REBOOT_BACKOFF) {
                numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));
            } else {
                numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(incremented_num_previous_reboots));
            }

            int interval = SensorConfig.REBOOT_MIN_WAIT;
            interval = interval * incremented_num_previous_reboots;
            Log.e("rebooter", "scheduling reboot at: " + String.valueOf(interval));
            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            Calendar cal = Calendar.getInstance();
            Intent serviceIntent = new Intent(mContext, Reboot.class);
            PendingIntent servicePendingIntent =
                    PendingIntent.getService(mContext,
                            48281723,
                            serviceIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT);
            am.setExact(
                    AlarmManager.RTC_WAKEUP,
                    cal.getTimeInMillis() + interval,
                    servicePendingIntent
            );

        RebootCauseWriter r = new RebootCauseWriter(mContext);
        r.log(String.valueOf(System.currentTimeMillis()), class_name + ":" + cause.getMessage(), "reboot");

            send_dead_packet();

    }

    private void send_dead_packet() {
        try {
            Intent a = new Intent(mContext, PlugWatchService.class);
            a.putExtra(IntentConfig.FAIL_PACKET, IntentConfig.FAIL_PACKET);
            mContext.startService(a);
        } catch (java.lang.NullPointerException e) {
            e.printStackTrace();
        }
    }
}
