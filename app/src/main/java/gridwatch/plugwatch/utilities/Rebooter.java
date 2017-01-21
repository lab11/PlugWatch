package gridwatch.plugwatch.utilities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.logs.RebootBackOffNumWriter;
import gridwatch.plugwatch.logs.RebootCauseWriter;
import gridwatch.plugwatch.wit.App;
import gridwatch.plugwatch.wit.PlugWatchService;

/**
 * Created by nklugman on 11/17/16.
 */

public class Rebooter {

    final Handler handler = new Handler();

    Context mContext;
    RebootBackOffNumWriter numWriter;
    Throwable cause;
    String class_name;

    boolean mImmediate;



    public Rebooter(Context context, String calling_class_name, boolean immediate, Throwable n) {
        mContext = context;
        numWriter = new RebootBackOffNumWriter(getClass().getName());

        mImmediate = immediate;

        n.printStackTrace();
        cause = n;
        class_name = calling_class_name;

        if (mContext == null) {
            mContext = App.getContext();
        }

        //SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        //if (sp.getBoolean(IntentConfig.NO_REBOOT, false)) {
            setup_reboot();
        //}



    }

    Runnable rebooter = new Runnable() {
        @Override
        public void run() {

            Reboot reboot = new Reboot();
            reboot.do_reboot();
        }
    };

    private void setup_reboot() {



            int num_previous_reboots_cnt = Integer.parseInt(numWriter.get_last_value());
            int incremented_num_previous_reboots = num_previous_reboots_cnt + 1;
            Log.e("restart rebooter", "num previous reboots: " + String.valueOf(num_previous_reboots_cnt));
            Log.e("restart rebooter", "threshold is: " + String.valueOf(incremented_num_previous_reboots * SensorConfig.REBOOT_MIN_WAIT));
            Log.e("restart rebooter", "time is: " + String.valueOf(System.currentTimeMillis()));

            if (incremented_num_previous_reboots > SensorConfig.MAX_NUM_REBOOT_BACKOFF) {
                numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));
            } else {
                numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(incremented_num_previous_reboots));
            }

            int interval = SensorConfig.REBOOT_MIN_WAIT;
            interval = interval * incremented_num_previous_reboots;
            Log.e("restart rebooter new interval", String.valueOf(interval));
            Log.e("restart rebooter num_previous_reboots", String.valueOf(incremented_num_previous_reboots));

            Log.e("restart rebooter", "scheduling reboot at: " + String.valueOf(interval));


            if (interval >= SensorConfig.MAX_DELAY_BEFORE_REBOOT) {
                interval = SensorConfig.MAX_DELAY_BEFORE_REBOOT;
            }

            /*
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
            */

        RebootCauseWriter r = new RebootCauseWriter(mContext, getClass().getName());
        r.log(String.valueOf(System.currentTimeMillis()), class_name + ":" + cause.getMessage(), "reboot");


        Log.e("restart reboot", "scheduling reboot " + String.valueOf(interval) + " ms in the future");
        //handler.postDelayed(rebooter, interval);
            if (mImmediate) {
                reboot(0);
            } else {
                reboot(interval);

            }

            send_dead_packet();

    }

    private void reboot(int interval) {
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        Intent queryIntent = new Intent(mContext, Reboot.class);
        PendingIntent pendingQueryIntent = PendingIntent.getService(mContext, 0, queryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + interval, pendingQueryIntent);
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
