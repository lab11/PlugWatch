package gridwatch.plugwatch.wit;

import android.app.ActivityManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.DatabaseConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.WD;
import gridwatch.plugwatch.firebase.FirebaseCrashLogger;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.LatLngWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;
import gridwatch.plugwatch.utilities.Restart;

public class WatchdogService extends IntentService {


    public WatchdogService() {
        super("WatchdogService");
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(), getClass().getName(),
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


            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                double battery = level / (double) scale;
                mDatabase = FirebaseDatabase.getInstance().getReference();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long time = System.currentTimeMillis();
                long time_since_last_wit_ms = time - sp.getLong(SettingsConfig.LAST_WIT, 1);
                String measurementSize = String.valueOf(sp.getInt(SettingsConfig.WIT_SIZE, 1));
                String gwSize = String.valueOf(sp.getInt(SettingsConfig.GW_SIZE, 1));
                String versionNum = sp.getString(SettingsConfig.VERSION_NUM, "");
                String externalFreespace = sp.getString(SettingsConfig.FREESPACE_EXTERNAL, "");
                String internalFreespace = sp.getString(SettingsConfig.FREESPACE_INTERNAL, "");
                PhoneIDWriter r = new PhoneIDWriter(getApplicationContext(), getClass().getName());
                String phone_id = r.get_last_value();
                GroupIDWriter w = new GroupIDWriter(getClass().getName());
                String group_id = w.get_last_value();
                String num_realms = String.valueOf(sp.getInt(SettingsConfig.NUM_REALMS, -1));
                long network_size = sp.getLong(SettingsConfig.TOTAL_DATA, -1);
                LatLngWriter l = new LatLngWriter(getClass().getName());
                String loc = l.get_last_value();
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                String iemi = telephonyManager.getDeviceId();

                boolean is_online = checkOnline();

                WD cur = new WD(time, time_since_last_wit_ms, measurementSize, gwSize, num_realms, versionNum, externalFreespace, internalFreespace,
                        phone_id, group_id, battery, network_size, loc, iemi, is_online, SensorConfig.WD_TYPE_WD);
                int new_wd_num = sp.getInt(SettingsConfig.NUM_WD, 0) + 1;
                sp.edit().putInt(SettingsConfig.NUM_WD, new_wd_num).commit();
                mDatabase.child(phone_id).child(DatabaseConfig.WD).child(String.valueOf(new_wd_num)).setValue(cur);
            } catch (Exception e) {
                e.printStackTrace();
            }

            ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            int cnt = 0;
            boolean is_watchdog_2_running = false;

            final List<ActivityManager.RunningAppProcessInfo> runningProcesses = manager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo process: runningProcesses) {
                //Log.e(getClass().getName(), process.processName);
                if (process.processName.contains(".PlugWatch") ||
                        process.processName.contains("gridwatch.wit")) {
                    cnt++;
                }
                if (process.processName.contains(".Watchdog")) {
                    is_watchdog_2_running = true;
                }
            }
            if (cnt != 2 || !is_watchdog_2_running) {
                Restart r = new Restart();
                r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, getClass().getName(), new Throwable("watchdog rebooting due to dead process"), -1);
            } else {
                Log.e("watchdog", "all is well!");
            }



        } catch (java.lang.NullPointerException e) {
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            e.printStackTrace();
        }

    }

    public boolean checkOnline() {
        ConnectivityManager cm = (ConnectivityManager) getBaseContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        boolean isOnline = netInfo != null && netInfo.isConnectedOrConnecting();
        return isOnline;
    }

}


