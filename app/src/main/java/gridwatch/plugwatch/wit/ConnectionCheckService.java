package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import gridwatch.plugwatch.PlugWatchApp;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.utilities.Reboot;
import gridwatch.plugwatch.utilities.Restart;

public class ConnectionCheckService extends IntentService {

    private SharedPreferences settings;

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            settings = PreferenceManager.getDefaultSharedPreferences(this);
        } catch (java.lang.NullPointerException e) {

        }
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
        //SharedPreferences settings = getBaseContext().getSharedPreferences(SettingsConfig.SETTINGS_META_DATA, 0);
        //long last = settings.getLong(SettingsConfig.LAST_WIT, -1);

        //long last = PlugWatchApp.getInstance().get_last_time();
        long diffInMs = System.currentTimeMillis() - last;
        Log.e("ConnectionCheckService", "checking");
        if (diffInMs > SensorConfig.CONNECTION_THRESHOLD && last != -1) {
            int num_previous_reboots = settings.getInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0);
            Log.e("ConnectionCheckService", "restarting " + String.valueOf(diffInMs));
            Log.e("ConnectionCheckService", "restart number " + String.valueOf(num_previous_reboots));

            if (num_previous_reboots + 1 > SensorConfig.REBOOT_THRESHOLD) { //time to nuke it all
                try {
                    settings.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0).commit();
                } catch (java.lang.NullPointerException e) {

                }
                Reboot r = new Reboot();
                r.do_reboot(new Throwable("rebooting due to max timeout"));
            } else { //just reboot the app
                try {
                    settings.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, num_previous_reboots + 1).commit();
                } catch (java.lang.NullPointerException e) {

                }
                Restart r = new Restart();
                r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, new Throwable("restarting due to timeout"));
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
