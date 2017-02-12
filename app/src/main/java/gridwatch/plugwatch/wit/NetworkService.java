package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.google.firebase.crash.FirebaseCrash;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import java.util.List;

import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.MeasurementRealm;
import gridwatch.plugwatch.logs.LatLngWriter;
import gridwatch.plugwatch.logs.MacWriter;
import gridwatch.plugwatch.network.NetworkJob;
import gridwatch.plugwatch.network.WitRetrofit;
import io.realm.Realm;
import io.realm.RealmResults;

import static gridwatch.plugwatch.wit.App.getContext;

public class NetworkService extends IntentService {

    static AppPreferences appPreferences;
    boolean isGetWifi = false;
    boolean isCheckCP = false;



    public NetworkService(String name) {
        super(name);
    }

    public NetworkService() {
        super("NetworkService");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        Realm.init(this);
        Realm r = Realm.getDefaultInstance();

        appPreferences = new AppPreferences(getContext());

        String phone_id = "-1";
        String group_id = "-1";
        String cp = "-1";
        String wifi_res = "-1";

        try {
            phone_id = appPreferences.getString(SettingsConfig.PHONE_ID);
            group_id = appPreferences.getString(SettingsConfig.GROUP_ID);
        } catch (ItemNotFoundException e) {
            FirebaseCrash.log(e.getMessage());
            e.printStackTrace();
        }

        String build_str = "-1";
        try {
            build_str = appPreferences.getString(SettingsConfig.BUILD_STR);
        } catch (ItemNotFoundException e) {
            FirebaseCrash.log(e.getMessage());
            e.printStackTrace();
        }

        isCheckCP = appPreferences.getBoolean(SettingsConfig.CHECK_CP, false);
        isGetWifi = appPreferences.getBoolean(SettingsConfig.WIFI_UPLOAD, false);

        if (isCheckCP) {
            cp = checkCP();
        }

        if (isGetWifi) {
            wifi_res = getWifi();
        }

        double lat = 0.0;
        double lng = 0.0;
        try {
            LatLngWriter c = new LatLngWriter(getClass().getName());
            String latlng = c.get_last_value();
            lat = Double.valueOf(latlng.split(",")[0]);
            lng = Double.valueOf(latlng.split(",")[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        long num_wit = r.where(MeasurementRealm.class).findAll().size();
        RealmResults<MeasurementRealm> f = r.where(MeasurementRealm.class).between("mTime", System.currentTimeMillis()- SensorConfig.NETWORK_INTERVAL, System.currentTimeMillis()).findAll();
        long total_wit = -1;
        try {
            total_wit = appPreferences.getLong(SettingsConfig.TOTAL_WIT_SIZE);
        } catch (ItemNotFoundException e) {
            e.printStackTrace();
        }

        String ms_time_running = "-1";
        try {
            ms_time_running = appPreferences.getString(SettingsConfig.TIME_RUNNING);
        } catch (ItemNotFoundException e) {
            e.printStackTrace();
        }


        for (int i = 0; i < f.size(); i++) {
            MeasurementRealm cur = f.get(i);
            WitRetrofit a = new WitRetrofit(cur.getCurrent(), cur.getFrequency(), cur.getPower(), cur.getPowerFactor(),
                    cur.getVoltage(), cur.getTime(), lat,
                    lng, phone_id, group_id, build_str, String.valueOf(num_wit), cur.getMac(), cp, wifi_res, String.valueOf(total_wit),
                    ms_time_running);
            Log.i("good_data: network scheduling", a.toString());
            Log.i("good_data: number of jobs: ", String.valueOf(JobManager.instance().getAllJobRequests().size()));
            if (JobManager.instance().getAllJobRequests().size() > SensorConfig.MAX_JOBS) {
                Log.e("good_data: network", "canceling all jobs");
                JobManager.instance().cancelAll();
            }
            int jobId = new JobRequest.Builder(NetworkJob.TAG)
                    .setExecutionWindow(1_000L, 20_000L)
                    .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.EXPONENTIAL)
                    .setRequiresCharging(false)
                    .setExtras(a.toBundle())
                    .setRequiresDeviceIdle(false)
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                    .setPersisted(true)
                    .build()
                    .schedule();
        }
        r.close();
    }

    private String setup_build_str() {
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return String.valueOf(pInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "-1";
        }
    }

    private String getWifi() {
        String wifi_res = "-1";
        try {
            WifiManager mWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (mWifi.isWifiEnabled() != false) {
                List<ScanResult> results = mWifi.getScanResults();
                //Log.d("ssids", results.toString());
                for (int i = 0; i < results.size(); i++) {
                    ScanResult a = results.get(i);
                    String resStr = a.SSID + "," + String.valueOf(a.level) ;
                    wifi_res = wifi_res + resStr + ":";
                }
            } else {
                Log.d("wifi err", "wifi not enabled");
                FirebaseCrash.log("wifi not enabled");
            }
            if (wifi_res.length() > 0) {
                wifi_res = wifi_res.substring(0, wifi_res.length() - 1);
            } else {
                wifi_res = "none";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e("network wifi_res", wifi_res);
        return wifi_res;
    }

    private String checkCP() {
        String macAddress = "-1";
        String cp = "f";
        try {
            macAddress = appPreferences.getString(SettingsConfig.STICKY_MAC);
        } catch (ItemNotFoundException e) {
            FirebaseCrash.log(e.getMessage());
            e.printStackTrace();
        }

        try {
            MacWriter r = new MacWriter(getClass().getName());
            if (macAddress != null) {
                String sticky = r.get_last_sticky_value();
                if (!macAddress.equals(sticky)) {
                    cp = "t";
                } else {
                    cp = "f";
                }
            } else {
                cp = "f";
            }

        } catch (Exception e) {
            FirebaseCrash.log(e.getMessage());
            e.printStackTrace();
        }
        Log.e("network cp", cp);
        return cp;
    }


}
