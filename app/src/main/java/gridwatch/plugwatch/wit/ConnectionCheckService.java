package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;

import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.logs.LastGoodWitWriter;
import gridwatch.plugwatch.logs.RestartNumWriter;
import gridwatch.plugwatch.utilities.Rebooter;
import gridwatch.plugwatch.utilities.Restart;

//TODO:
// figure out why rebooter isn't working
// figure out what is going on with rapid restarts after reboot... shouldn't be possible...


public class ConnectionCheckService extends IntentService {

    RestartNumWriter numRebootWriter;
    Context mContext;
    LastGoodWitWriter witWriter;

    @Override
    protected void onHandleIntent(Intent intent) {
        run();
    }

    public ConnectionCheckService() {
        super("ConnectionCheckService");
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(), getClass().getName(),
                    PlugWatchUIActivity.class));
        }

    }


    @Override
    public void onCreate() {
        super.onCreate();
    }


    private void run() {
        Log.e("ConnectionCheckService", "hit");
        if (getBaseContext() == null) {
            Log.e("ConnectionCheckService", "null context");
        }

        numRebootWriter = new RestartNumWriter(getBaseContext(),getClass().getName());
        witWriter = new LastGoodWitWriter(getClass().getName());

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        int plugwatchservice_pid = sp.getInt(SettingsConfig.PID, -1);

        long last = Long.valueOf(witWriter.get_last_value());

        long diffInMs = System.currentTimeMillis() - last;


        Log.e("ConnectionCheckService numWriter last val", numRebootWriter.get_last_value());
        Log.e("ConnectionCheckService witWriter last val", witWriter.get_last_value());

        int num_previous_reboots = Integer.parseInt(numRebootWriter.get_last_value());
        Log.i("ConnectionCheckService", "restart checking " + String.valueOf(last) + " num previous reboots: " + String.valueOf(num_previous_reboots));

        if (num_previous_reboots + 1 > SensorConfig.REBOOT_THRESHOLD) { //Something went wrong...
            num_previous_reboots = SensorConfig.REBOOT_THRESHOLD;
            Log.i("ConnectionCheckService", "restart capping number of reboots to " + String.valueOf(SensorConfig.REBOOT_THRESHOLD));
        }
        int incremented_num_previous_reboots = num_previous_reboots + 1;
        int new_connection_threshold = incremented_num_previous_reboots*SensorConfig.CONNECTION_THRESHOLD; //increase to allow for multiple timeouts
        Log.i("ConnectionCheckService", "restart new connection threshold is: " + String.valueOf(new_connection_threshold));
        Log.i("ConntectionCheckService", "last: " + String.valueOf(last) + " cur: " + String.valueOf(System.currentTimeMillis()) + " diff: " + String.valueOf(diffInMs) + " thresh: " + String.valueOf(new_connection_threshold) + " last: " + String.valueOf(last));



        try {
            if (diffInMs > new_connection_threshold && last != -1) {
                if (incremented_num_previous_reboots > SensorConfig.REBOOT_THRESHOLD) {
                    numRebootWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));
                    Log.e("ConnectionCheckService", " setting to zero ");
                    FirebaseCrash.log("ConnectionCheckService: rebooting due to max timeout and reseting num_writer");
                    Rebooter r = new Rebooter(getApplicationContext(), this.getClass().getName(), new Throwable("restart rebooting due to max timeout"));
                    //Reboot r = new Reboot();
                    //r.do_reboot();
                } else {
                    Log.i("ConnectionCheckService", "restart not restarting " + String.valueOf(diffInMs));
                    numRebootWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(incremented_num_previous_reboots));
                    Log.e("ConnectionCheckService", " setting to " + String.valueOf(incremented_num_previous_reboots));
                    Restart r = new Restart();
                    r.do_restart(App.getInstance().getApplicationContext(), PlugWatchUIActivity.class, this.getClass().getName(), new Throwable("restarting due to timeout"), plugwatchservice_pid);
                }

            } else {
                Log.i("ConnectionCheckService", "restart not restarting " + String.valueOf(diffInMs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
