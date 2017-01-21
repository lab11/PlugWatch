package gridwatch.plugwatch.wit;

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
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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

public class SMSWatchdogService extends IntentService {


    public SMSWatchdogService() {
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

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            double battery = level / (double) scale;

            try {
                mDatabase = FirebaseDatabase.getInstance().getReference();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long time = System.currentTimeMillis();
                long time_since_last_wit_ms = time - sp.getLong(SettingsConfig.LAST_WIT, 1);
                String measurementSize = String.valueOf(sp.getLong(SettingsConfig.WIT_SIZE, 1));
                String gwSize = String.valueOf(sp.getLong(SettingsConfig.GW_SIZE, 1));
                String versionNum = sp.getString(SettingsConfig.VERSION_NUM, "");
                String externalFreespace = sp.getString(SettingsConfig.FREESPACE_EXTERNAL, "");
                String internalFreespace = sp.getString(SettingsConfig.FREESPACE_INTERNAL, "");
                PhoneIDWriter r = new PhoneIDWriter(getApplicationContext(), getClass().getName());
                String phone_id = r.get_last_value();
                GroupIDWriter w = new GroupIDWriter(getApplicationContext(), getClass().getName());
                String group_id = w.get_last_value();
                String num_realms = String.valueOf(sp.getInt(SettingsConfig.NUM_REALMS, -1));
                long network_size = sp.getLong(SettingsConfig.TOTAL_DATA, -1);
                LatLngWriter l = new LatLngWriter(getClass().getName());
                String loc = l.get_last_value();
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                String iemi = telephonyManager.getDeviceId();
                boolean is_online = checkOnline();

                WD cur = new WD(time, time_since_last_wit_ms, measurementSize, gwSize, num_realms, versionNum, externalFreespace, internalFreespace,
                        phone_id, group_id, battery, network_size, loc, iemi, is_online, SensorConfig.WD_TYPE_SMSWD);
                int new_wd_num = sp.getInt(SettingsConfig.NUM_WD, 0) + 1;
                sp.edit().putInt(SettingsConfig.NUM_WD, new_wd_num).commit();
                mDatabase.child(phone_id).child(DatabaseConfig.WD).child(String.valueOf(new_wd_num)).setValue(cur);
                sendSMS(cur.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (NullPointerException e) {
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            e.printStackTrace();
        }

    }

    public void sendSMS(String to_send) {
        SmsManager smsManager = SmsManager.getDefault();
        Log.e("texting msg", to_send);
        smsManager.sendTextMessage(SensorConfig.SMS_ENDPOINT, null, to_send, null, null);
    }

    public boolean checkOnline() {
        ConnectivityManager cm = (ConnectivityManager) getBaseContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        boolean isOnline = netInfo != null && netInfo.isConnectedOrConnecting();
        return isOnline;
    }

}


