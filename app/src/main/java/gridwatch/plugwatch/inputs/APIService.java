package gridwatch.plugwatch.inputs;

/*
API Doc following:
https://docs.google.com/spreadsheets/d/1d_bmM19I3wL3lGdEq6833ne66qoTkGYtSEbnl-bZfac/edit?usp=sharing

Add in callbacks for internet change, SSID change, power change

 */

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import cz.msebera.android.httpclient.Header;
import gridwatch.plugwatch.WitEnergyVersionTwo;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.database.Command;
import gridwatch.plugwatch.wit.WitEnergyBluetoothActivity;
import io.realm.Realm;
import io.realm.RealmResults;


public class APIService extends Service {

    private Realm realm;
    private String mGWID;
    private String mGroupId;

    private final static boolean ready = true;
    private final static boolean not_ready = false;


    private SensorManager mSensorManager;
    private Sensor mSensor;
    private TriggerEventListener mTriggerEventListener;

    private boolean waitForUpdate = false;

    private Context mContext;

    private boolean isText;
    private boolean isForced;
    private String mAckstate = "high";


    // TODO... bind to API
    boolean clearUploads = false;
    boolean clearAudio = false;
    boolean clearGridWatch = false;
    boolean clearWitEnergy = false;

    boolean now = false;

    public APIService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        mGWID = WitEnergyVersionTwo.getInstance().get_phone_id().getID();
        mGroupId = WitEnergyVersionTwo.getInstance().get_group_id().getID();

        if (intent != null && intent.getExtras() != null) {
            if (intent.getExtras().getString("msg") != null) { //MSG formated as <phone_id,cmd:parameters>
                Log.e("FCM msg in service", intent.getExtras().getString("msg"));

                String t = "";
                try {
                    t = intent.getExtras().getString("msg");
                    if (!t.equals("upload")) {
                        String r[] = new String[0];
                        if (t != null) {
                            r = t.split(",");
                        }
                        if (r.length == 1) {
                            Log.e("uploadservice", "poorly formatted command");
                        }
                    }
                } catch (NullPointerException e) {
                    Log.e("uploadservice", "poorly formatted command");
                    //return -1;
                }

                try {

                    //set up IDs
                    String msg = intent.getExtras().getString("msg");

                    String now_str = intent.getExtras().getString("exe");
                    if (now_str != null) {
                        Log.e("NOW", "true");
                        now = true;
                    }

                    if (msg == null) {
                        Log.e("uploadservice", "null message");
                        return START_NOT_STICKY;
                    }

                    if (msg.startsWith("@")) {
                        isForced = true;
                        Log.e("FORCING CMD DANGER ZONE", msg);
                        msg = msg.substring(1);
                    }

                    String id = msg.split(",")[0];

                    Log.e("uploadservice:phone_id", mGWID);
                    Log.e("uploadservice:group_id", mGroupId);
                    Log.e("uploadservice:msg", msg);
                    Log.e("uploadservice:id", id);
                    do_api(id, msg, mGWID, mGroupId);
                } catch (NullPointerException e) {
                    Log.e("uploadservice", "null");
                    e.printStackTrace();
                }
            }
        }
        return START_NOT_STICKY;
    }


    @Override
    public void onCreate(){
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                WitEnergyBluetoothActivity.class));

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sp.edit().putLong("reboot", -1).apply();

        //startBackgroundPerformExecutor(); //run upload queue

        realm = Realm.getDefaultInstance();
        mContext = getBaseContext();

        registerReceiver(mConnectivityReceiver, makeConnectivityIntentFilter());
        //registerReceiver(mStorageManagementReceiver, makeManagementFilter());
        registerReceiver(mBatteryManagmentReceiver, makeBatteryFilter());
    }


    private static IntentFilter makeConnectivityIntentFilter() {
        final IntentFilter fi = new IntentFilter();
        fi.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        return fi;
    }


    public static IntentFilter makeManagementFilter() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
        ifilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
        return ifilter;
    }


    public static IntentFilter makeBatteryFilter() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_BATTERY_LOW);
        return ifilter;
    }

    public static IntentFilter makeScreenFilter() {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_SCREEN_ON);
        ifilter.addAction(Intent.ACTION_SCREEN_OFF);
        return ifilter;
    }


    private final BroadcastReceiver mBatteryManagmentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {

        }
    };


    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
                    //startBackgroundPerformExecutor();
                }
            }
        }
    };


    private void do_api(String id, String msg, String phone_id, String group_id) {
        // and here we go...
        String cmd = msg.split(",")[1];


        if (id.equals(phone_id) || id.equals("-1") || id.equals(group_id)) {
            Log.e("FCM ID MATCH", "action being taken: " + msg.split(",")[1]);
            Command cur_cmd = new Command(System.currentTimeMillis(), msg, isText, phone_id, group_id);
            realm.beginTransaction();
            realm.copyToRealm(cur_cmd);
            realm.commitTransaction();

            if (cmd.contains("uploadday")) {
                if (isText) {
                    return_err("invalid cmd for SMS " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("forcing " + cmd);
                    }
                }
                String parameters = cmd.split(":")[1];
                String uploadtype = parameters.split("\\?")[0];
                String num = parameters.split("\\?")[1];
                do_upload_day(uploadtype, num);
            } else if (cmd.contains("uploadall")) {
                if (isText) {
                    return_err("invalid cmd for SMS " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                String uploadtype = cmd.split(":")[1];
                do_upload_all(uploadtype);
            } else if (cmd.contains("httpendpoint")) {
                String url = cmd.split(":")[1];
                set_http_endpoint(url);
            } else if (cmd.contains("smsendpoint")) {
                String phonenumber = cmd.split(":")[1];
                set_sms_endpoint(phonenumber);
            } else if (cmd.contains("setgroupid")) {
                String group = cmd.split(":")[1];
                set_group(group);
            } else if (cmd.contains("report")) {
                get_report(false);
            } else if (cmd.equals("free_space")) {
                get_free_space();
            } else if (cmd.equals("stats")) {
                get_stats();
            } else if (cmd.equals("dump")) {
                send_db();
            } else if (cmd.contains("live")) {
                if (id.equals("-1")) {
                    return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                String parameters = cmd.split(":")[1];
                String livestate = parameters.split("\\?")[0];
                String livetype = parameters.split("\\?")[1];
                set_livestate(livestate, livetype);
            } else if (cmd.contains("interrogate")) {
                if (id.equals("-1") || id.startsWith("g")) {
                    return_err("too large of an id for cmd " + cmd);
                } else {
                    String interrogatestate = cmd.split(":")[1];
                    set_interrogatestate(interrogatestate);
                }
            } else if (cmd.equals("location")) {
                get_location();
            } else if (cmd.equals("accelerometer")) {
                get_acceleration();
            } else if (cmd.equals("audio")) {
                if (isText) {
                    return_err("invalid cmd for SMS " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                if (id.equals("-1") || id.startsWith("g")) {
                    return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                get_audio();
            } else if (cmd.equals("ssids")) {
                get_ssids();
            } else if (cmd.equals("celltowers")) {
                get_celltowers();
            } else if (cmd.equals("temp")) {
                get_temperature();
            } else if (cmd.equals("fft")) {
                get_ffts();
            } else if (cmd.contains("settings")) {
                String parameters = cmd.split(":")[1];
                String settingscmd = parameters.split("\\?")[0];
                String modifier = parameters.split("\\?")[1];
                set_settings(settingscmd, modifier);
            } else if (cmd.equals("reboot")) {
                if (id.equals("-1") || id.startsWith("g")) {
                    //return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }

                do_reboot();
            } else if (cmd.contains("deleteall")) {
                if (isText) {
                    return_err("invalid cmd for SMS " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                if (id.equals("-1") || id.startsWith("g")) {
                    return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                String deletetype = cmd.split(":")[1];
                do_delete_all(deletetype);
            } else if (cmd.contains("deleteday")) {
                if (isText) {
                    return_err("invalid cmd for SMS " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                if (id.equals("-1") || id.startsWith("g")) {
                    return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                String parameters = cmd.split(":")[1];
                String deletetype = parameters.split("\\?")[0];
                String num = parameters.split("\\?")[1];
                do_delete_day(deletetype, num);
            } else if (cmd.equals("getcmds")) {
                get_cmds();

            } else if (cmd.equals("getcrashcount")) {
                get_crashcnt();

            } else if (cmd.equals("getcrashes")) {
                if (id.equals("-1") || id.startsWith("g")) {
                    return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                get_crashes();
            } else if (cmd.equals("wifi")) {
                String parameters = cmd.split(":")[1];
                String ssid = parameters.split("\\?")[0];
                String type = parameters.split("\\?")[1];
                String username = parameters.split("\\?")[2];
                String password = parameters.split("\\?")[3];
                do_wifi(ssid, type, username, password);
            } else if (cmd.equals("getdumpcount")) {
                get_dumpcnt();
            } else if (cmd.equals("getversion")) {
                get_version();
            } else if (cmd.equals("debugALL")) {
                do_upload_day("all", "1");
                do_upload_all("all");
                get_report(false);
                get_free_space();
                get_stats();
                set_livestate("off", "all");
                set_interrogatestate("off");
                get_location();
            } else if (cmd.equals("wipe")) {
                if (isText) {
                    return_err("invalid cmd for SMS " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                if (id.equals("-1") || id.startsWith("g")) {
                    return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                wipe();
            } else if (cmd.equals("wd")) {
                get_report(true);
            } else if (cmd.equals("sim")) {
                get_sim();
                get_phonenum();

            } else if (cmd.contains("topup")) {
                String parameters = cmd.split(":")[1];
                String name = parameters.split("\\?")[0];
                String amount = parameters.split("\\?")[1];
                String location = parameters.split("\\?")[2];
                do_topup(name, amount, location);
            } else if (cmd.contains("check")) {
                String parameters = cmd.split(":")[1];
                String name = parameters.split("\\?")[0];
                String location = parameters.split("\\?")[1];
                do_balance_check(name, location);
            } else if (cmd.equals("ackstate")) {
                String ackstate = cmd.split(":")[1];
                if (id.equals("-1")) {
                    return_err("too large of an id for cmd " + cmd);
                    if (!isForced) {
                        return;
                    } else {
                        return_err("Forcing " + cmd);
                    }
                }
                set_ackstate(ackstate);
            } else if (cmd.contains("setmaxcrash")) {
                String max_crash = cmd.split(":")[1];
                set_max_crash(max_crash);
            } else {
                return_err("invalid command! not understood... " + cmd);
            }
        }
    }


    /* -----------------------------------------------------------
    // IMPLEMENTATIONS
    // do_upload_day(uploadtype, num);
    // do_upload_all(uploadtype);
    // set_http_endpoint(url);
    // set_sms_endpoint(phonenumber);
    // set_group(group);
    // get_report(wd);
    // get_free_space();
    // get_stats();
    // set_livestate(livestate,livetype);
    // set_interrogatestate(interrogatestate);
    // get_location();
    // get_acceleration();
    // get_audio();
    // get_celltowers();
    // get_ssids();
    // set_settings(settingscmd);
    // do_reboot();
    // do_delete_all(deletetype);
    // do_delete_day(deletetype, num);
    // get_cmds();
    // get_crashcnt();
    // get_dumpcnt();
    // set_ackstate(ackstate)
    // set_max_crash(maxcrash)
    // do_wifi(SSID, type, password, username)
    // do_topup(carrier_name, voucher_num, location)
    // do_balance_check(carrier_name, location)
    -------------------------------------------------------------- */

    public void do_wifi(String SSID, String type, String password, String username) {
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + SSID + "\"";
        if (type.equals("wep")) {
            conf.wepKeys[0] = "\"" + password + "\"";
            conf.wepTxKeyIndex = 0;
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        } else if (type.equals("wpa")) {
            conf.preSharedKey = "\"" + password + "\"";
        } else {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        WifiManager wifiManager = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.addNetwork(conf);
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration i : list) {
            if (i.SSID != null && i.SSID.equals("\"" + SSID + "\"")) {
                wifiManager.disconnect();
                wifiManager.enableNetwork(i.networkId, true);
                wifiManager.reconnect();
                break;
            }
        }
    }

    public void do_topup(String name, String voucher_num, String location) {
        Log.e("doing topup", "hit");
        String number = "tel:";
        if (name.equals("zantel")) {
            number = "";
        } else if (name.equals("airtel")) {
            if (location.equals("tanzania")) {
                number = "*104*"; //tanzania
            } else if (location.equals("kenya")) {
                number = "*130*"; //kenya
            }
        } else if (name.equals("safaricom")) {
            number = "*141*" + voucher_num + "#";
        }
        Intent callIntent = new Intent(Intent.ACTION_CALL, ussdToCallableUri(number));
        if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        startActivity(callIntent);
    }


    private Uri ussdToCallableUri(String ussd) {
        String uriString = "";
        if(!ussd.startsWith("tel:"))
            uriString += "tel:";
        for(char c : ussd.toCharArray()) {
            if(c == '#')
                uriString += Uri.encode("#");
            else
                uriString += c;
        }
        return Uri.parse(uriString);
    }

    public void do_balance_check(String name, String location) {
        String number = "";
        if (name.equals("zantel")) {
            number = "*102#";
        } else if (name.equals("airtel")) {
            if (location.equals("tanzania")) {
                number = "*102#";
            } else if (location.equals("kenya")) {

            }
        } else if (name.equals("safaricom")) {
            String airtime = "*144#";
            String data = "*544#";
            //sendSMS(data);
            //sendSMS(airtime);
        }
    }

    public void return_err(String msg) {
        Log.e("error", msg);
        RequestParams resp = new RequestParams();
        resp.put("error", msg);
        resp.put("command", "return_err");
        resp.put("wifi_only", "no");
        resp.put("ack", "low");
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void get_sim() {
        TelephonyManager telemamanger = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String getSimSerialNumber = telemamanger.getSimSerialNumber();
        Log.e("get_phonenum", "hit");
        RequestParams resp=new RequestParams();
        resp.put("command", "get_phonenum");
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        resp.put("sim_serial", getSimSerialNumber);
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void get_phonenum() {
        TelephonyManager telemamanger = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String getSimNumber = telemamanger.getLine1Number();
        Log.e("get_phonenum", "hit");
        RequestParams resp=new RequestParams();
        resp.put("command", "get_phonenum");
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        resp.put("sim_serial", getSimNumber);
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void do_upload_day(String uploadtype, String num) {
        Log.e("do_upload_day", uploadtype + "?" + num);
        int num_days = -1;
        long num_seconds = -1;
        try {
            num_days = Integer.valueOf(num);
            num_seconds = num_days * 86400 * 1000;
        } catch (NumberFormatException e) {
            return_err("invalid number of days" + num);
            return;
        }
        if (!uploadtype.equals("all") && !uploadtype.equals("gridwatch") && !uploadtype.equals("witenergy") ) {
            return_err("invalid upload type" + uploadtype);
            return;
        }
        if (uploadtype.equals("witenergy") || uploadtype.equals("all")) {
            /*
            RealmResults<WitEnergyMeasurement> res = realm.where(WitEnergyMeasurement.class).
                    greaterThan("mTime", System.currentTimeMillis() - num_seconds).
                    findAll();
            for (int i = 0 ; i < res.size(); i++) {
                WitEnergyMeasurement a = res.get(i);
                SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, a.toRequestParams(false), mContext));
            }
            */
        }
        if (uploadtype.equals("gridwatch") || uploadtype.equals("all")) {
            /*
            RealmResults<GridWatchEvent> res = realm.where(GridWatchEvent.class).
                    greaterThan("mTime", System.currentTimeMillis() - num_seconds).
                    findAll();
            for (int i = 0 ; i < res.size(); i++) {
                GridWatchEvent a = res.get(i);
                SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, a.toRequestParams(), mContext));
            }
            */
        }
        RequestParams resp=new RequestParams();
        resp.put("command", "do_upload_day " + uploadtype + "?" + num);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void do_upload_all(String uploadtype) {
        Log.e("uploading_all", uploadtype);
        if (!uploadtype.equals("all") && !uploadtype.equals("gridwatch") && !uploadtype.equals("witenergy") ) {
            return_err("invalid upload type" + uploadtype);
            return;
        }
        if (uploadtype.equals("witenergy") || uploadtype.equals("all")) {
            /*
            RealmResults<WitEnergyMeasurement> res = realm.where(WitEnergyMeasurement.class).
                    findAll();
            for (int i = 0 ; i < res.size(); i++) {
                WitEnergyMeasurement a = res.get(i);
                SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, a.toRequestParams(false), mContext));
            }
            */
        }
        if (uploadtype.equals("gridwatch") || uploadtype.equals("all")) {
            /*
            RealmResults<GridWatchEvent> res = realm.where(GridWatchEvent.class).
                    findAll();
            for (int i = 0 ; i < res.size(); i++) {
                GridWatchEvent a = res.get(i);
                SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, a.toRequestParams(), mContext));
            }
            */
        }
        RequestParams resp=new RequestParams();
        resp.put("command", "do_upload_all " + uploadtype);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }


    //Status: needs testing
    public void set_http_endpoint(String url) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sp.edit().putString("http_endpoint", url).apply();
        RequestParams resp=new RequestParams();
        resp.put("command", "http_endpoint " + url);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void set_sms_endpoint(String phonenumber) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sp.edit().putString("sms_endpoint", phonenumber).apply();
        RequestParams resp=new RequestParams();
        resp.put("command", "set_sms_endpoint " + phonenumber);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void set_group(String group) {
        DateFormat df = DateFormat.getDateTimeInstance();
        if (!group.startsWith("g")) {
            return_err("can't set group " + group);
            return;
        }
        //mGroupId.log(df.format(new Date()), group, null);
        RequestParams resp=new RequestParams();
        resp.put("command", "set_group " + group);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void get_report(boolean wd) {
        Log.e("report", "now");
        RequestParams resp=new RequestParams();
        if (wd) {
            resp.put("type", "wd");
        } else {
            resp.put("type", "cmd");
        }
        //resp.put("version", SensorTagApplicationClass.getInstance().getBuildStr());
        resp.put("command", "report");
        resp.put("wifi_only", "no");
        resp.put("ack", "low");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void get_free_space() {
        Log.e("free space", "now");
        RequestParams resp = new RequestParams();
        resp.put("free space internal", getAvailableInternalMemorySize());
        resp.put("free space external:", getAvailableExternalMemorySize());
        resp.put("command", "freeSpace");
        resp.put("wifi_only", "no");
        resp.put("ack", "low");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void get_stats() {
        //TODO add in audio settings, total data, total data of each type in realm
        Log.e("stats", "now");
        RequestParams resp=new RequestParams();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Map<String,?> keys = sp.getAll();
        for(Map.Entry<String,?> entry : keys.entrySet()){
            resp.put(entry.getKey(), entry.getValue().toString());
        }

        //RealmResults<WitEnergyMeasurement> res = realm.where(WitEnergyMeasurement.class).findAll();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getBaseContext().registerReceiver(null, ifilter);
        assert batteryStatus != null;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float) scale;
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        UUID deviceUuid = new UUID(androidId.hashCode(), ((long) tmDevice.hashCode() << 32) | tmSerial.hashCode());
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            resp.put("lat",String.valueOf(location.getLatitude()));
            resp.put("lng",String.valueOf(location.getLongitude()));
        } catch (SecurityException e) {
            Log.e("loc", e.toString());
        }
        resp.put("free space internal", getAvailableInternalMemorySize());
        resp.put("free space external:", getAvailableExternalMemorySize());
        //resp.put("app_version",BuildConfig.VERSION_NAME);
        resp.put("timestamp",String.valueOf(System.currentTimeMillis()));
        resp.put("uptime",String.valueOf(SystemClock.uptimeMillis()));
        resp.put("reboot_cnt",String.valueOf(sp.getLong("reboot", -1)));
        //resp.put("num_measurments",String.valueOf(res.size()));
        resp.put("battery_life",String.valueOf(batteryPct));
        resp.put("android_id",deviceUuid.toString());
        resp.put("fcm_id",sp.getString("token", "-1"));
        resp.put("fcm_counter", String.valueOf(sp.getLong("fcm_cnt", -1)));
        resp.put("command", "stats");
        resp.put("wifi_only", "no");
        resp.put("ack", "low");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(not_ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void set_livestate(String livestate, String livetype) {
        Log.e("set_livestate", livestate+","+livetype);
        if (!livetype.equals("all") && !livetype.equals("gridwatch") && !livetype.equals("witenergy") ) {
            return_err("invalid livetype type" + livetype);
            return;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        RequestParams resp = new RequestParams();
        if (livestate.equals("on")) {
            sp.edit().putBoolean("live"+livetype, true).apply();
            Log.e("live", "setting live for " + livetype + " on");
            //resp.put("resp", mGWID.get_last_value() + livetype + " on");
        } else if (livestate.equals("off")){
            sp.edit().putBoolean("live"+livetype, false).apply();
            Log.e("live", "setting live for " + livetype + " off");
            //resp.put("resp", mGWID.get_last_value() + livetype + " off");
        } else {
            return_err("invalid livestate " + livestate);
        }
        resp.put("command", "set_livestate," + livestate + "?" + livetype);
        resp.put("ack", "high");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    //Status: needs testing
    public void set_interrogatestate(String interrogatestate) {
        Log.e("set_interrogatestate", interrogatestate);
        RequestParams resp = new RequestParams();
        resp.put("command", "set_interrogatestate," + interrogatestate);
        resp.put("ack", "high");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();

        //TODO explore rebooting bluetooth...
        //Intent i = new Intent(getBaseContext(), LocalTransfer.class);
        //i.putExtra("from", "api");
        //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //startActivity(i);
    }

    //Status: needs testing
    public void get_location() {
        Log.e("api", "get_location");
        RequestParams resp=new RequestParams();
        resp.put("command", "get_location");
        resp.put("wifi_only", "no");
        resp.put("ack", "low");
        Log.i("resp", resp.toString());
        /*
        Event e  = new Event(not_ready, resp, mContext);
        e.set_needs(false, false, false, false, true, true, false, false);
        SensorTagApplicationClass.getInstance().upload_queue.add(e);
        startBackgroundPerformExecutor();
        */
    }

    public void get_audio() {
        RequestParams resp=new RequestParams();
        resp.put("command", "get_audio");
        resp.put("wifi_only", "yes");
        resp.put("ack", "medium");
        Log.i("resp", resp.toString());
        /*
        Event e  = new Event(not_ready, resp, mContext);
        e.set_needs(false, true, true, false, false, false, false, false);
        SensorTagApplicationClass.getInstance().upload_queue.add(e);
        startBackgroundPerformExecutor();
        */
    }

    public void get_ffts() {
        RequestParams resp=new RequestParams();
        resp.put("command", "get_ffts");
        resp.put("wifi_only", "yes");
        resp.put("ack", "medium");
        Log.i("resp", resp.toString());
        /*
        Event e  = new Event(not_ready, resp, mContext);
        e.set_needs(false, true, false, false, false, false, false, false);
        SensorTagApplicationClass.getInstance().upload_queue.add(e);
        if (now) {
            Log.e("ffts", "trying now");
        }
        startBackgroundPerformExecutor();
        */
    }

    public void get_ssids() {
        RequestParams resp=new RequestParams();
        resp.put("command", "get_ssids");
        resp.put("wifi_only", "yes");
        resp.put("ack", "medium");
        Log.i("resp", resp.toString());
        /*
        Event e  = new Event(not_ready, resp, mContext);
        e.set_needs(false, false, false, true, false, false, false, false);
        SensorTagApplicationClass.getInstance().upload_queue.add(e);
        startBackgroundPerformExecutor();
        */
    }

    public void get_celltowers() {
        RequestParams resp=new RequestParams();
        resp.put("command", "get_celltowers");
        resp.put("wifi_only", "yes");
        resp.put("ack", "medium");
        Log.i("resp", resp.toString());
        /*
        Event e  = new Event(not_ready, resp, mContext);
        e.set_needs(false,false,false,false,false,false,true,false);
        SensorTagApplicationClass.getInstance().upload_queue.add(e);
        startBackgroundPerformExecutor();
        */
    }

    public void get_acceleration() {
        RequestParams resp=new RequestParams();
        resp.put("command", "get_acceleration");
        resp.put("wifi_only", "yes");
        resp.put("ack", "medium");
        Log.i("resp", resp.toString());
        /*
        Event e  = new Event(not_ready, resp, mContext);
        e.set_needs(true,false,false,false,false,false,false,false);
        SensorTagApplicationClass.getInstance().upload_queue.add(e);
        startBackgroundPerformExecutor();
        */
    }

    public void get_temperature() {
        RequestParams resp=new RequestParams();
        resp.put("command", "get_temperature");
        resp.put("wifi_only", "yes");
        resp.put("ack", "medium");
        Log.i("resp", resp.toString());
        /*
        Event e  = new Event(not_ready, resp, mContext);
        e.set_needs(false,false,false,false,false,false,false,true);
        SensorTagApplicationClass.getInstance().upload_queue.add(e);
        startBackgroundPerformExecutor();
        */
    }



    public void set_settings(String settingscmd, String modifier) {
        Log.e("set_settings", settingscmd);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        RequestParams resp = new RequestParams();
        if (modifier.equals("on")) {
            sp.edit().putBoolean(settingscmd, true).apply();
        } else if (modifier.equals("off")) {
            sp.edit().putBoolean(settingscmd, false).apply();
        } else if (settingscmd.equals("mic_sample_freq") ||
                settingscmd.equals("bit_rate") ||
                settingscmd.equals("mic_sample_time"))  {
            try {
                sp.edit().putFloat(settingscmd, Float.valueOf(modifier));
            } catch (NumberFormatException e) {
                return_err("invalid float modifier " + settingscmd + "?" + modifier);
            }
        } else if (settingscmd.equals("channels")) {
            if (modifier.equals("stereo") || modifier.equals("mono")) {
                sp.edit().putString("channels", modifier).apply();
            } else {
                return_err("invalid channel modifier " + settingscmd + "?" + modifier);
            }
        } else {
            return_err("invalid set_settings " + settingscmd + "?" + modifier);
        }
        resp.put("command", "set_settings,"+settingscmd + "?" + modifier);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready,resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void do_reboot() {
        Log.e("reboot", "now");
        //JsonHttpResponseHandler h = check_update();
        do_reboot_timer.postDelayed(check_do_reboot, 20000); //need this to let the get from the server have time to exe

    }

    Handler do_reboot_timer = new Handler();
    private Runnable check_do_reboot = new Runnable() {
        @Override
        public void run() {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            boolean cur_wait = sp.getBoolean("cur_wait",false);
            if (!cur_wait) {
                sp.edit().putLong("reboot", System.currentTimeMillis()).apply();
                int i = sp.getInt("reboot_cnt",0);
                i += 1;
                sp.edit().putInt("reboot_cnt", i).apply();
                reboot_cmd();
            }
        }
    };
    public void reboot_cmd() {
        try {
            Process proc = Runtime.getRuntime()
                    .exec(new String[]{ "su", "-c", "reboot" });
            proc.waitFor();
            Runtime.getRuntime().exec(new String[]{"/system/bin/su","-c","reboot now"});
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void do_delete_all(String deletetype) {
        Log.e("do_delete_all", deletetype);
        if (!deletetype.equals("all") && !deletetype.equals("gridwatch") && !deletetype.equals("witenergy") ) {
            return_err("invalid delete type " + deletetype);
            return;
        }
        if (deletetype.equals("witenergy") || deletetype.equals("all")) {
            //RealmResults<WitEnergyMeasurement> res = realm.where(WitEnergyMeasurement.class).findAll();
            realm.beginTransaction();
            //res.deleteAllFromRealm();
            realm.commitTransaction();
        }
        if (deletetype.equals("gridwatch") || deletetype.equals("all")) {
            //RealmResults<GridWatchEvent> res = realm.where(GridWatchEvent.class).findAll();
            realm.beginTransaction();
            //res.deleteAllFromRealm();
            realm.commitTransaction();
        }
        RequestParams resp = new RequestParams();
        resp.put("command", "do_delete_all,"+deletetype);
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void wipe() {
        /*
        RealmConfiguration realmConfig = new RealmConfiguration.Builder(getBaseContext()).build();
        try {
            Realm.deleteRealm(realmConfig); // Clean slate
        }
        catch (IllegalStateException e) {
            Realm db = Realm.getDefaultInstance();
            if (db!=null) {
                db.close();
                Realm.deleteRealm(realmConfig);
            }
        }
        Realm.setDefaultConfiguration(realmConfig);
        RequestParams resp = new RequestParams();
        resp.put("command", "wipe");
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        */
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void do_delete_day(String deletetype, String num) {
        Log.e("do_delete_day", deletetype + "?" + num);
        int num_days = -1;
        long num_seconds = -1;
        try {
            num_days = Integer.valueOf(num);
            num_seconds = num_days * 86400 * 1000;
        } catch (NumberFormatException e) {
            return_err("invalid number of days" + num);
            return;
        }
        if (!deletetype.equals("all") && !deletetype.equals("gridwatch") && !deletetype.equals("witenergy") ) {
            return_err("invalid delete type " + deletetype);
            return;
        }
        if (deletetype.equals("witenergy") || deletetype.equals("all")) {
            /*
            RealmResults<WitEnergyMeasurement> res = realm.where(WitEnergyMeasurement.class).
                    greaterThan("mTime", System.currentTimeMillis() - num_seconds).
                    findAll();
            realm.beginTransaction();
            res.deleteAllFromRealm();
            realm.commitTransaction();
            */
        }
        if (deletetype.equals("gridwatch") || deletetype.equals("all")) {
            /*
            RealmResults<GridWatchEvent> res = realm.where(GridWatchEvent.class).
                    greaterThan("mTime", System.currentTimeMillis() - num_seconds).
                    findAll();
            realm.beginTransaction();
            res.deleteAllFromRealm();
            realm.commitTransaction();
            */
        }
        RequestParams resp = new RequestParams();
        resp.put("command", "do_delete_day,"+deletetype + "?" + num);
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void get_cmds() {
        RealmResults<Command> res = realm.where(Command.class).findAll();
        for (int i = 0; i < res.size(); i++) {
            Command a = res.get(i);
            //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready,a.toRequestParams(), mContext));
        }
        RequestParams resp = new RequestParams();
        resp.put("command", "get_cmds");
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void get_crashcnt() {
        //RealmResults<Crash> res = realm.where(Crash.class).findAll();
        RequestParams resp = new RequestParams();
        //resp.put("crash_cnt", res.size());
        resp.put("command", "get_crashcnt");
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void set_default() {

    }

    public void get_crashes() {
        /*
        RealmResults<Crash> res = realm.where(Crash.class).findAll();
        for (int i = 0; i < res.size(); i++) {
            Crash a = res.get(i);
            SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, a.toRequestParams(), mContext));
        }
        RequestParams resp = new RequestParams();
        resp.put("command", "get_crashes");
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        startBackgroundPerformExecutor();
        */
    }
    public void get_version() {
        RequestParams resp = new RequestParams();
        resp.put("command", "get_version");
        //resp.put("version", SensorTagApplicationClass.getInstance().getVersion());
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void get_dumpcnt() {
        //RealmResults<Dump> res = realm.where(Dump.class).findAll();
        RequestParams resp = new RequestParams();
        //resp.put("dump_cnt", res.size());
        resp.put("command", "get_dumpcnt");
        resp.put("ack", "low");
        resp.put("wifi_only", "no");
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void set_ackstate(String ackstate) {
        if (ackstate.equals("high") || ackstate.equals("medium") || ackstate.equals("low")) {
            mAckstate = ackstate;
        } else {
            return_err("invalid ackstate " + ackstate);
            return;
        }
        RequestParams resp = new RequestParams();
        resp.put("command", "ackstate,"+ackstate);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    public void set_max_crash(String max_crash) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sp.edit().putInt("max_crash", Integer.valueOf(max_crash)).apply();
        RequestParams resp = new RequestParams();
        resp.put("command", "max_crash,"+max_crash);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
    }

    /* -----------------------------------------------------------
    //
    //  HTTP Uploading
    //
    //
    -------------------------------------------------------------- */
    public class BackgroundPerformExecutor implements Executor {
        private Context context;
        public BackgroundPerformExecutor(Context context) {
            this.context = context;
        }

        @Override public void execute(Runnable command) {
            if (isOnline()) {
                command.run();
            }
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mConnectivityReceiver);
    }


    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getBaseContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }



    /* -----------------------------------------------------------
    //
    // Helpers
    //
    //
    -------------------------------------------------------------- */
    public static String getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return formatSize(availableBlocks * blockSize);
    }

    public static String getAvailableExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return formatSize(availableBlocks * blockSize);
        } else {
            return "FAILED TO GET AVAILABLE EXTERNAL MEM";
        }
    }

    public void sendSMS(String to_send) {
        SmsManager smsManager = SmsManager.getDefault();
        Log.e("texting msg", to_send);
        smsManager.sendTextMessage("12012317237", null, to_send, null, null);
    }

    public static boolean externalMemoryAvailable() {
        return Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
    }

    public static String formatSize(long size) {
        String suffix = null;
        if (size >= 1024) {
            suffix = "KB";
            size /= 1024;
            if (size >= 1024) {
                suffix = "MB";
                size /= 1024;
            }
        }
        StringBuilder resultBuffer = new StringBuilder(Long.toString(size));
        int commaOffset = resultBuffer.length() - 3;
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',');
            commaOffset -= 3;
        }
        if (suffix != null) resultBuffer.append(suffix);
        return resultBuffer.toString();
    }


    public void send_db() {
        String filePath = Realm.getDefaultInstance().getPath();
        zipFileAtPath(filePath, filePath+"_gw.zip");
        uploadFile(filePath+"_gw.zip");
    }

    public void uploadFile(String path) {
        File file = new File(path);
        RequestParams params = new RequestParams();
        try {
            params.put("uploadedfile", file);
        } catch(FileNotFoundException e) {
            Log.e("testcase", "file not found");
        }
        Log.e("filename", path);
        AsyncHttpClient client = new AsyncHttpClient();
        client.post("http://54.175.208.137/gcm_chat/v2/exp", params, new
                AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int a, Header[] h, byte[] b) {
                    }

                    @Override
                    public void onFailure(int a, Header[] h, byte[] b, Throwable e) {
                    }
                });
    }


    public boolean zipFileAtPath(String sourcePath, String toLocation) {
        final int BUFFER = 2048;

        File sourceFile = new File(sourcePath);
        try {
            BufferedInputStream origin = null;
            FileOutputStream dest = new FileOutputStream(toLocation);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                    dest));

            byte data[] = new byte[BUFFER];
            FileInputStream fi = new FileInputStream(sourcePath);
            origin = new BufferedInputStream(fi, BUFFER);
            ZipEntry entry = new ZipEntry(getLastPathComponent(sourcePath));
            out.putNextEntry(entry);
            int count;
            while ((count = origin.read(data, 0, BUFFER)) != -1) {
                out.write(data, 0, count);
            }

            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public String getLastPathComponent(String filePath) {
        String[] segments = filePath.split("/");
        if (segments.length == 0)
            return "";
        String lastPathComponent = segments[segments.length - 1];
        return lastPathComponent;
    }




}


