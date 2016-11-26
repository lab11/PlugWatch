package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.utilities.Reboot;
import gridwatch.plugwatch.utilities.Restart;
import gridwatch.plugwatch.utilities.RestartNumWriter;

public class ConnectionCheckService extends IntentService {

    RestartNumWriter numWriter;

    @Override
    protected void onHandleIntent(Intent intent) {
        numWriter = new RestartNumWriter(getApplicationContext());
        run();
    }

    public ConnectionCheckService() {
        super("ConnectionCheckService");

    }


    @Override
    public void onCreate() {
        super.onCreate();
    }


    private void run() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sp.edit().clear();
        long last = sp.getLong(SettingsConfig.LAST_WIT, -1);
        long diffInMs = System.currentTimeMillis() - last;
        Log.e("ConnectionCheckService", "checking " + String.valueOf(last));
        if (diffInMs > SensorConfig.CONNECTION_THRESHOLD && last != -1) {
            int num_previous_reboots_file = Integer.valueOf(numWriter.get_last_value());
            Log.e("reboot test", String.valueOf(num_previous_reboots_file));

            int num_previous_reboots = sp.getInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0);
            Log.e("ConnectionCheckService", "restarting " + String.valueOf(diffInMs));
            Log.e("ConnectionCheckService", "restart number " + String.valueOf(num_previous_reboots));

            if (num_previous_reboots_file + 1 > SensorConfig.REBOOT_THRESHOLD) { //time to nuke it all
                try {
                    sp.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0).commit();
                    sp.edit().putLong(SettingsConfig.LAST_WIT, System.currentTimeMillis()+SensorConfig.REBOOT_BUFFER).commit();
                    numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));
                } catch (java.lang.NullPointerException e) {

                }
                Reboot r = new Reboot();
                r.do_reboot(new Throwable("rebooting due to max timeout"));
            } else { //just reboot the app
                try {
                    sp.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, num_previous_reboots + 1).commit();
                    sp.edit().putLong(SettingsConfig.LAST_WIT, System.currentTimeMillis()+SensorConfig.RESTART_BUFFER).commit();

                    numWriter.log(String.valueOf(System.currentTimeMillis()), String.valueOf(num_previous_reboots_file+1));
                    Restart r = new Restart();
                    r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, new Throwable("restarting due to timeout"));
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
