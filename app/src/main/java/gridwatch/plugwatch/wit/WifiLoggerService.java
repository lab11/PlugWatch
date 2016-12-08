package gridwatch.plugwatch.wit;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;

import java.util.List;

import gridwatch.plugwatch.logs.WifiWriter;

public class WifiLoggerService extends Service {


    public WifiLoggerService() {



        /*
        //if (randomNum == 1) {
            Log.e("GridWatch", "doing random sample");
            if (getApplicationContext() != null) {

            }
            */

    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Context ctx = App.getContext();
            WifiWriter wifi_writer = new WifiWriter(getClass().getName());
            String wifi_res = "";
            WifiManager mWifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (mWifi.isWifiEnabled() != false) {
                List<ScanResult> results = mWifi.getScanResults();
                //Log.d("ssids", results.toString());
                for (int i = 0; i < results.size(); i++) {
                    ScanResult a = results.get(i);
                    //Log.e("SSID", a.SSID);
                    //Log.e("BSID", a.BSSID);
                    //Log.e("LEVEL", String.valueOf(a.level));
                    String resStr = a.SSID + "," + String.valueOf(a.level);
                    wifi_res = wifi_res + resStr + ":";

                }
            } else {
                Log.e("WifiLogger: wifi err", "wifi not enabled");
                FirebaseCrash.log("wifiLogger: wifi not enabled");
            }
            if (wifi_res.length() > 0) {
                wifi_res = wifi_res.substring(0, wifi_res.length() - 1);
            } else {
                wifi_res = "none";
            }
            wifi_writer.log(String.valueOf(System.currentTimeMillis()), wifi_res);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
