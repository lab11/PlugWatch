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
import gridwatch.plugwatch.logs.RestartNumWriter;
import gridwatch.plugwatch.utilities.Rebooter;
import gridwatch.plugwatch.utilities.Restart;

public class ConnectionCheckService extends IntentService {

    RestartNumWriter numWriter;
    Context mContext;

    @Override
    protected void onHandleIntent(Intent intent) {
        run();
    }

    public ConnectionCheckService() {
        super("ConnectionCheckService");
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(),
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

        numWriter = new RestartNumWriter(getBaseContext());

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        int plugwatchservice_pid = sp.getInt(SettingsConfig.PID, -1);

        long last = sp.getLong(SettingsConfig.LAST_WIT, -1);
        long diffInMs = System.currentTimeMillis() - last;

        Log.e("numWriter last val", numWriter.get_last_value());
        int num_previous_reboots = Integer.valueOf(numWriter.get_last_value());
        Log.i("ConnectionCheckService", "restart checking " + String.valueOf(last) + " num previous reboots: " + String.valueOf(num_previous_reboots));

        if (num_previous_reboots + 1 > SensorConfig.REBOOT_THRESHOLD) { //Something went wrong...
            num_previous_reboots = SensorConfig.REBOOT_THRESHOLD;
            Log.i("ConnectionCheckService", "restart capping number of reboots to " + String.valueOf(SensorConfig.REBOOT_THRESHOLD));
        }
        int incremented_num_previous_reboots = num_previous_reboots + 1;
        int new_connection_threshold = incremented_num_previous_reboots*SensorConfig.CONNECTION_THRESHOLD; //increase to allow for multiple timeouts
        Log.i("ConnectionCheckService", "restart new connection threshold is: " + String.valueOf(new_connection_threshold));

        if (diffInMs > new_connection_threshold && last != -1) {
            if (incremented_num_previous_reboots > SensorConfig.REBOOT_THRESHOLD) {
                numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));
                FirebaseCrash.log("ConnectionCheckService: rebooting due to max timeout");
                Rebooter r = new Rebooter(getApplicationContext(), new Throwable("restart rebooting due to max timeout"));
            } else {
                numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(incremented_num_previous_reboots));
                Restart r = new Restart();
                r.do_restart(getBaseContext(), PlugWatchUIActivity.class, new Throwable("restarting due to timeout"), plugwatchservice_pid);
            }

        } else {
            Log.i("ConnectionCheckService", "restart not restarting " + String.valueOf(diffInMs));
        }
    }


    private void old_run() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sp.edit().clear();
        long last = sp.getLong(SettingsConfig.LAST_WIT, -1);
        long diffInMs = System.currentTimeMillis() - last;
        int num_previous_reboots_file = Integer.valueOf(numWriter.get_last_value());
        Log.e("ConnectionCheckService", "checking " + String.valueOf(last) + " num previous reboots: " + String.valueOf(num_previous_reboots_file));
        if (num_previous_reboots_file + 1 > SensorConfig.REBOOT_THRESHOLD) {
            //Something went wrong...
            num_previous_reboots_file = SensorConfig.REBOOT_THRESHOLD;
            Log.e("ConnectionCheckService", "capping number of reboots to " + String.valueOf(SensorConfig.REBOOT_THRESHOLD));
        }
        int new_connection_threshold = (num_previous_reboots_file + 1)*SensorConfig.CONNECTION_THRESHOLD; //increase to allow for multiple timeouts


        Log.e("ConnectionCheckService", "new connection threshold is: " + String.valueOf(new_connection_threshold));
        if (diffInMs > new_connection_threshold && last != -1) {
            Log.e("reboot test", String.valueOf(num_previous_reboots_file));

            int num_previous_reboots = sp.getInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0);
            Log.e("ConnectionCheckService", "restarting " + String.valueOf(diffInMs));

            if (num_previous_reboots_file + 1 > SensorConfig.REBOOT_THRESHOLD) { //time to nuke it all
                try {
                    sp.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0).commit();
                    //sp.edit().putLong(SettingsConfig.LAST_WIT, System.currentTimeMillis()+SensorConfig.REBOOT_BUFFER).commit();
                    numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));
                } catch (java.lang.NullPointerException e) {

                }
                Rebooter r = new Rebooter(getApplicationContext(), new Throwable("rebooting due to max timeout"));
            } else { //just reboot the app
                try {
                    sp.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, num_previous_reboots + 1).commit();
                    sp.edit().putLong(SettingsConfig.LAST_WIT, System.currentTimeMillis()+SensorConfig.RESTART_BUFFER).commit();

                    numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(num_previous_reboots_file+1));
                    Restart r = new Restart();
                    //r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, new Throwable("restarting due to timeout"));
                } catch (java.lang.NullPointerException e) {}
            }
        } else {
            Log.e("ConnectionCheckService", "not restarting " + String.valueOf(diffInMs));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
