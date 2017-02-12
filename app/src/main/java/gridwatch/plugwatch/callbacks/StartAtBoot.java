package gridwatch.plugwatch.callbacks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import net.grandcentrix.tray.AppPreferences;

import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.wit.PlugWatchUIActivity;


/**
 * Created by nklugman on 6/24/16.
 */
public class StartAtBoot extends BroadcastReceiver {

    AppPreferences appPreferences;


    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            appPreferences = new AppPreferences(context);
            appPreferences.put(SettingsConfig.LAST_WIT, String.valueOf(System.currentTimeMillis()+5000));



            Intent activityIntent = new Intent(context, PlugWatchUIActivity.class);
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
            SharedPreferences pref = context.getSharedPreferences(SettingsConfig.SETTINGS_META_DATA, 0);
            int i = pref.getInt(SettingsConfig.BOOT_CNT, -1);
            if (i == -1) {
                pref.edit().putInt(SettingsConfig.BOOT_CNT, 1).apply();
            } else {
                pref.edit().putInt(SettingsConfig.BOOT_CNT, i+1).apply();
            }
            Log.e("START AT BOOT: boot number ", String.valueOf(pref.getInt(SettingsConfig.BOOT_CNT, -1)));
        }
    }
}