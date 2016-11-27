package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.DatabaseConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.WD;
import gridwatch.plugwatch.firebase.FirebaseCrashLogger;

public class WatchdogService extends IntentService {


    public WatchdogService() {
        super("WatchdogService");
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    PlugWatchUIActivity.class));
        }
    }

    private DatabaseReference mDatabase;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            Log.e("WATCHDOG", "hit");
            mDatabase = FirebaseDatabase.getInstance().getReference();
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            long time = System.currentTimeMillis();
            long time_since_last_wit_ms = time - Long.valueOf(sp.getInt(SettingsConfig.LAST_WIT, 1));
            String measurementSize = sp.getString(SettingsConfig.WIT_SIZE, "");
            String gwSize = sp.getString(SettingsConfig.GW_SIZE, "");
            String versionNum = sp.getString(SettingsConfig.VERSION_NUM, "");
            String externalFreespace = sp.getString(SettingsConfig.FREESPACE_EXTERNAL, "");
            String internalFreespace = sp.getString(SettingsConfig.FREESPACE_INTERNAL, "");
            String phone_id = sp.getString(SettingsConfig.PHONE_ID, "");
            String group_id = sp.getString(SettingsConfig.GROUP_ID, "");
            WD cur = new WD(time, time_since_last_wit_ms, measurementSize, gwSize, versionNum, externalFreespace, internalFreespace,
                    phone_id, group_id);
            int new_wd_num = sp.getInt(SettingsConfig.NUM_WD, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_WD, new_wd_num).commit();
            mDatabase.child(phone_id).child(DatabaseConfig.WD).child(String.valueOf(new_wd_num)).setValue(cur);
        } catch (java.lang.NullPointerException e) {
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            e.printStackTrace();
        }

    }

}


