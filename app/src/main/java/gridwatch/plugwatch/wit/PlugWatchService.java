package gridwatch.plugwatch.wit;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.google.firebase.FirebaseApp;

import gridwatch.plugwatch.IPlugWatchService;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.gridWatch.GridWatch;
import gridwatch.plugwatch.utilities.PhoneIDWriter;

public class PlugWatchService extends Service {

    private String phone_id;
    private String group_id;
    SharedPreferences settings;




    @Override
    public void onCreate() {
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    PlugWatchUIActivity.class));
        }
        registerReceiver(mPowerReceiver, makePowerIntentFilter());
        settings = getSharedPreferences(SettingsConfig.SETTINGS_META_DATA, 0);
        FirebaseApp.initializeApp(getBaseContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        WitConnector connector = new WitConnector();
        connector.start(getApplicationContext());
        return START_STICKY;
    }


    private void do_gw(Intent intent) {
        GridWatch g = null;
        try {
            if (phone_id == null) {
                PhoneIDWriter a = new PhoneIDWriter(getBaseContext());
                phone_id = a.get_last_value();
            }
        } catch (java.lang.IndexOutOfBoundsException e) {
            Log.e("error", "couldn't find phone id");
            phone_id = "-1";
        }
        if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
            g = new GridWatch(getBaseContext(), SensorConfig.PLUGGED, phone_id);
        } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
            g = new GridWatch(getBaseContext(), SensorConfig.UNPLUGGED, phone_id);
        }
        if (g != null) {
            g.run();
        }
    }

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            do_gw(intent);
        }
    };

    public static IntentFilter makePowerIntentFilter() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_POWER_CONNECTED);
        ifilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ifilter.addAction(Intent.ACTION_DOCK_EVENT);
        return ifilter;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IPlugWatchService.Stub mBinder = new IPlugWatchService.Stub() {
        @Override
        public long get_last_time() throws RemoteException {
            return 0;
        }

        @Override
        public boolean get_is_connected() throws RemoteException {
            return false;
        }

        @Override
        public int get_num_wit() throws RemoteException {
            return 0;
        }

        @Override
        public int get_num_gw() throws RemoteException {
            return 0;
        }

        @Override
        public void set_phone_id(int phone_id) throws RemoteException {

        }

        @Override
        public void set_group_id(int group_id) throws RemoteException {

        }
    };


}