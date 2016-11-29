package gridwatch.plugwatch.callbacks;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Log;

import com.evernote.android.job.Job;

import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.utilities.Rebooter;
import gridwatch.plugwatch.wit.PlugWatchUIActivity;

/**
 * Created by nklugman on 11/21/16.
 */

public class ConnectivityJob extends Job {

    public static final String TAG = "connectivity_job";

    @Override
    @NonNull
    protected Job.Result onRunJob(Params params) {
        SharedPreferences meta_data = getContext().getSharedPreferences(SettingsConfig.SETTINGS_META_DATA, 0);
        long last_time = meta_data.getLong(SettingsConfig.LAST_WIT, -1);
        long cur_time = System.currentTimeMillis();
        Log.e("wd: connectivity", String.valueOf(cur_time - last_time));
        Log.e("wd", String.valueOf(cur_time));
        Log.e("wd", String.valueOf(last_time));

        if (last_time != -1) {
            if (cur_time - last_time < SensorConfig.CONNECTION_THRESHOLD) {
                Intent intent = new Intent(getContext(), PlugWatchUIActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //FirebaseCrash.log(s);
                long connection_timeouts = meta_data.getLong(SettingsConfig.NUM_CONNECTION_TIMEOUTS, 0);
                meta_data.edit().putLong(SettingsConfig.NUM_CONNECTION_TIMEOUTS, connection_timeouts + 1).apply();
                if (connection_timeouts < SensorConfig.REBOOT_THRESHOLD) {
                    Log.e("wd: err", "connection timeout");
                    BluetoothAdapter.getDefaultAdapter().enable();
                    getContext().startActivity(intent);
                    Process.killProcess(Process.myPid());
                    System.exit(0);
                } else {
                    Log.e("wd: err", "connection timeout reboot");
                    meta_data.edit().putLong(SettingsConfig.NUM_CONNECTION_TIMEOUTS, 0).apply();
                    int reboot_timeouts = meta_data.getInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0);
                    meta_data.edit().putLong(SettingsConfig.NUM_CONNECTION_REBOOTS, reboot_timeouts + 1).apply();
                    Rebooter r = new Rebooter(getContext(), new Throwable("rebooting from connectivity job"));
                }
            }
        }
        return Job.Result.SUCCESS;
    }

}