package gridwatch.plugwatch.wit;

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
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.stealthcopter.networktools.Ping;
import com.stealthcopter.networktools.ping.PingResult;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import cz.msebera.android.httpclient.Header;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.DatabaseConfig;
import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.Ack;
import gridwatch.plugwatch.database.Command;
import gridwatch.plugwatch.database.WD;
import gridwatch.plugwatch.firebase.FirebaseCrashLogger;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;
import io.realm.Realm;

import static gridwatch.plugwatch.configs.SensorConfig.recordingFolder;


public class APIService extends Service {


    private final static boolean ready = true;
    private final static boolean not_ready = false;


    private SensorManager mSensorManager;
    private Sensor mSensor;
    private TriggerEventListener mTriggerEventListener;

    private boolean waitForUpdate = false;

    private Context mContext;

    private boolean isText = false;
    private boolean isForced;
    private String mAckstate = "high";

    boolean now = false;

    private StorageReference mStorageRef;
    private DatabaseReference mDatabase;

    String cur_phone_id;
    String cur_group_id;

    SharedPreferences sp;


    public APIService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("api", "start");
        PhoneIDWriter r = new PhoneIDWriter(getApplicationContext());
        cur_phone_id = r.get_last_value();
        GroupIDWriter p = new GroupIDWriter(getApplicationContext());
        cur_group_id = p.get_last_value();

        if (intent != null && intent.getExtras() != null) {
            String cmd = intent.getStringExtra(IntentConfig.INCOMING_API_COMMAND);
            String phone_id = intent.getStringExtra(IntentConfig.INCOMING_API_PHONE_ID);
            String group_id = intent.getStringExtra(IntentConfig.INCOMING_API_GROUP_ID);
            if (intent.hasExtra(IntentConfig.IS_TEXT)) {
                isText = true;
            }
            if (cmd.startsWith("@")) {
                isForced = true;
                Log.e("api FORCING CMD DANGER ZONE", cmd);
                cmd = cmd.substring(1);
            }
            if (cur_phone_id.equals(phone_id)) {
                do_api(phone_id, group_id, cmd, true); //boolean is to restrict some commands to only individual phones
                return START_NOT_STICKY;
            } else if (cur_group_id.equals(cur_group_id) || phone_id.equals("-1")) {
                do_api(phone_id, group_id, cmd, false);
            }
        }
        return START_NOT_STICKY;
    }


    @Override
    public void onCreate(){
        super.onCreate();
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    PlugWatchUIActivity.class));
        }

        mStorageRef = FirebaseStorage.getInstance().getReference();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        mContext = getBaseContext();

        registerReceiver(mConnectivityReceiver, makeConnectivityIntentFilter());
        //registerReceiver(mStorageManagementReceiver, makeManagementFilter());
        registerReceiver(mBatteryManagmentReceiver, makeBatteryFilter());
    }




    private void do_api(String phone_id, String group_id, String cmd, boolean isIndividual) {
        // and here we go...
        Log.e("api ID MATCH", "action being taken: " + cmd);
        Command cur_cmd = new Command(System.currentTimeMillis(), cmd, isText, phone_id, group_id);
        int new_cmd_num = sp.getInt(SettingsConfig.NUM_COMMANDS, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_COMMANDS, new_cmd_num).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.COMMAND).child(String.valueOf(new_cmd_num)).setValue(cur_cmd);


        if (cmd.contains("uploadall")) {
            if (isText) {
                return_err("invalid cmd for SMS " + cmd);
                if (!isForced) {
                    return;
                } else {
                    return_err("Forcing " + cmd);
                }
            }
            do_upload_all();
        } else if (cmd.contains("upload_specific")) {
            do_upload_specific(cmd);
        } else if (cmd.contains("uploadaudio")) {
            do_upload_audio();
        } else if (cmd.contains("uploadlogs")) {
            do_upload_logs();
        } else if (cmd.contains("uploadlog_specific")) {
            do_upload_logs_specific(cmd);
        } else if (cmd.contains("deletelog_specific")) {
            do_delete_log_specific(cmd);
        } else if (cmd.contains("deletedb_specific")) {
            do_delete_db_specific(cmd);
        } else if (cmd.contains("deleteaudio_specific")) {
            do_delete_audio_specific(cmd);
        } else if (cmd.contains("ping")) {
            do_ping(cmd);
        } else if (cmd.contains("sendsms")) {
            send_sms();
        }


        else if (cmd.contains("httpendpoint")) {
            String url = cmd.split(":")[1];
            set_http_endpoint(url);
        } else if (cmd.contains("smsendpoint")) {
            String phonenumber = cmd.split(":")[1];
            set_sms_endpoint(phonenumber);
        } else if (cmd.contains("setgroupid")) {
            String group = cmd.split(":")[1];
            set_group(group);
        } else if (cmd.contains("report")) {
            get_report();
        } else if (cmd.contains("free_space")) {
            get_free_space();
        } else if (cmd.contains("stats")) {
            get_stats();
        } else if (cmd.equals("reboot")) {
            if (!isIndividual) {
                return_err("too large of an id for cmd " + cmd);
                if (!isForced) {
                    return;
                } else {
                    return_err("Forcing " + cmd);
                }
            }
            do_reboot();
        } else if (cmd.equals("getcmds")) {
            get_cmds();

        } else if (cmd.equals("getcrashes")) {
            if (!isIndividual) {
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
        } else if (cmd.equals("getversion")) {
            get_version();
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
        } else if (cmd.contains("setmaxcrash")) {
            String max_crash = cmd.split(":")[1];
            set_max_crash(max_crash);
        } else if (cmd.contains("nuke_audio")) {
            if (!isIndividual) {
                return_err("too large of an id for cmd " + cmd);
                if (!isForced) {
                    return;
                } else {
                    return_err("Forcing " + cmd);
                }
            }
            delete_audiofiles();
        } else {
            return_err("invalid command! not understood... " + cmd);
        }
    }


    /* -----------------------------------------------------------
    // IMPLEMENTATIONS
    // do_upload_day(uploadtype, num);
    // do_upload_all();
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

    public void send_sms() {
        sendSMS(cur_phone_id);
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

        Ack a = new Ack(System.currentTimeMillis(), "error: " + msg, cur_phone_id, cur_group_id);
        int new_err_num = sp.getInt(SettingsConfig.NUM_ERR, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_ERR, new_err_num).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.ERR).child(String.valueOf(new_err_num)).setValue(a);
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

        Ack a = new Ack(System.currentTimeMillis(), resp.toString(), cur_phone_id, cur_group_id);
        int new_sim_num = sp.getInt(SettingsConfig.NUM_SIM, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_SIM, new_sim_num).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.GET_SIM).child(String.valueOf(new_sim_num)).setValue(a);
        Log.i("resp", resp.toString());
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

        Ack a = new Ack(System.currentTimeMillis(), resp.toString(), cur_phone_id, cur_group_id);
        int new_phone_num = sp.getInt(SettingsConfig.NUM_PHONE, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_PHONE, new_phone_num).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.GET_PHONENUM).child(String.valueOf(new_phone_num)).setValue(a);
    }


    public void do_upload_audio() {
        List<File> files_to_upload = getListFiles(setupFilePaths(), ".wav");
        for (int i = 0; i < files_to_upload.size(); i++) {
            Log.e("uploading", files_to_upload.get(i).getName());
            upload(files_to_upload.get(i));
        }
    }


    private File setupFilePaths() {
        String tmpFilePath = "";
        if (android.os.Build.VERSION.SDK_INT>=19) {
            File[] possible_kitkat_mounts = mContext.getExternalFilesDirs(null);
            for (int x = 0; x < possible_kitkat_mounts.length; x++) {
                if (possible_kitkat_mounts[x] != null){
                    tmpFilePath = possible_kitkat_mounts[x].toString();
                }
            }
        } else {
            // Set up the tmp file before WAV conversation
            tmpFilePath = Environment.getExternalStorageDirectory().getPath();
        }
        return new File(tmpFilePath, recordingFolder);
    }

    //Status: needs testing
    public void do_upload_all() {
        List<File> files_to_upload = getListFiles(openRealm(), "realm");
        for (int i = 0; i < files_to_upload.size(); i++) {
            Log.e("uploading", files_to_upload.get(i).getName());
            upload(files_to_upload.get(i));
        }
    }




    public void do_upload_logs() {
        File root = Environment.getExternalStorageDirectory();
        List<File> files_to_upload = getListFiles(root, ".log");
        for (int i = 0; i < files_to_upload.size(); i++) {
            Log.e("uploading", files_to_upload.get(i).getName());
            upload(files_to_upload.get(i));
        }
    }


    public void do_upload_logs_specific(String cmd) {
        try {
            File root = Environment.getExternalStorageDirectory();
            String date = cmd.split(":")[1];
            Log.e("uploading", date);

            List<File> files_to_upload = getListFiles(root, date);
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("uploading", files_to_upload.get(i).getName());
                upload(files_to_upload.get(i));
            }
        } catch (Exception e) {
            return_err("bad parameters " + cmd);
        }
    }

    public void do_delete_log_specific(String cmd) {
        try {
            File root = Environment.getExternalStorageDirectory();
            String date = cmd.split(":")[1];
            Log.e("uploading", date);
            List<File> file_to_delete = getListFiles(root, date);
            for (int i = 0; i < file_to_delete.size(); i++) {
                Log.e("deleting", file_to_delete.get(i).getName());
                file_to_delete.get(i).delete();
            }
        } catch (Exception e) {
            return_err("bad parameters " + cmd);
        }
    }




    public void do_upload_specific(String cmd) {
        try {
            String date = cmd.split(":")[1];
            List<File> files_to_upload = getListFiles(openRealm(), date);
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("uploading", files_to_upload.get(i).getName());
                upload(files_to_upload.get(i));
            }
        } catch (Exception e) {
            return_err("bad parameters " + cmd);
        }
    }

    public void do_delete_db_specific(String cmd) {
        try {
            String date = cmd.split(":")[1];
            List<File> file_to_delete = getListFiles(openRealm(), cmd);
            for (int i = 0; i < file_to_delete.size(); i++) {
                Log.e("deleting", file_to_delete.get(i).getName());
                file_to_delete.get(i).delete();
            }
        } catch (Exception e) {
            return_err("bad parameters " + cmd);
        }
    }


    //Status: needs testing
    public void set_http_endpoint(String url) {
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
    public void get_report() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        double battery = level / (double) scale;



        long time = System.currentTimeMillis();

        Log.e("PID", String.valueOf(android.os.Process.myPid()));

        long time_since_last_wit_ms = time - sp.getLong(SettingsConfig.LAST_WIT, 1);

        String measurementSize = String.valueOf(sp.getInt(SettingsConfig.WIT_SIZE, -1));
        String gwSize = String.valueOf(sp.getInt(SettingsConfig.GW_SIZE, -1));
        String versionNum = sp.getString(SettingsConfig.VERSION_NUM, "");
        String externalFreespace = sp.getString(SettingsConfig.FREESPACE_EXTERNAL, "");
        String internalFreespace = sp.getString(SettingsConfig.FREESPACE_INTERNAL, "");

        WD cur = new WD(time, time_since_last_wit_ms, measurementSize, gwSize, versionNum, externalFreespace, internalFreespace,
                cur_phone_id, cur_group_id, battery);
        int new_wd_num = sp.getInt(SettingsConfig.NUM_WD, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_WD, new_wd_num).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.WD).child(String.valueOf(new_wd_num)).setValue(cur);
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

        Ack a = new Ack(System.currentTimeMillis(), resp.toString(), cur_phone_id, cur_group_id);
        int new_freespace = sp.getInt(SettingsConfig.NUM_FREE_SPACE, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_FREE_SPACE, new_freespace).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.GET_FREE_SPACE).child(String.valueOf(new_freespace)).setValue(a);
    }

    //Status: needs testing
    public void get_stats() {
        //TODO add in audio settings, total data, total data of each type in realm
        Log.e("stats", "now");
        RequestParams resp=new RequestParams();
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

        Ack a = new Ack(System.currentTimeMillis(), resp.toString(), cur_phone_id, cur_group_id);
        int new_stats = sp.getInt(SettingsConfig.NUM_STATS, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_STATS, new_stats).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.GET_STATS).child(String.valueOf(new_stats)).setValue(a);

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



    public void delete_audiofiles() {
        try {
            File dir = get_audio_file();
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (null != files) {
                    for (int i = 0; i < files.length; i++) {
                        files[i].delete();
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
        }
    }

    public void do_delete_audio_specific(String cmd) {
        try {
            File root = setupFilePaths();
            String date = cmd.split(":")[1];
            Log.e("uploading", date);
            List<File> file_to_delete = getListFiles(root, date);
            for (int i = 0; i < file_to_delete.size(); i++) {
                Log.e("deleting", file_to_delete.get(i).getName());
                file_to_delete.get(i).delete();
            }
        } catch (Exception e) {
            return_err("bad parameters " + cmd);
        }
    }

    private File get_audio_file() {
        String tmpFilePath = "";
        if (android.os.Build.VERSION.SDK_INT>=19) {
            File[] possible_kitkat_mounts = mContext.getExternalFilesDirs(null);
            for (int x = 0; x < possible_kitkat_mounts.length; x++) {
                if (possible_kitkat_mounts[x] != null){
                    Log.d("audio", "possible_kitkat_mounts " + possible_kitkat_mounts[x].toString());
                    tmpFilePath = possible_kitkat_mounts[x].toString();
                }
            }
        } else {
            // Set up the tmp file before WAV conversation
            tmpFilePath = Environment.getExternalStorageDirectory().getPath();
            Log.d("audio" + ":RECORDING PATH", tmpFilePath);
        }
        File fileFolder = new File(tmpFilePath, recordingFolder);
        return fileFolder;
    }

    public void do_ping(String cmd) {
        try {
            String url = cmd.split(":")[1];
            Ping.onAddress(url).setTimeOutMillis(1000).setTimes(2).doPing(new Ping.PingListener() {
                @Override
                public void onResult(PingResult pingResult) {
                    float res = pingResult.getTimeTaken();
                    int new_ping_num = sp.getInt(SettingsConfig.NUM_PING, 0) + 1;
                    sp.edit().putInt(SettingsConfig.NUM_PING, new_ping_num).commit();
                    mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.PING).child(String.valueOf(new_ping_num)).setValue(res);
                }

                @Override
                public void onFinished() {

                }
            });
        } catch (Exception e) {
            return_err("bad parameters " + cmd);
        }
    }

    public void set_settings(String settingscmd, String modifier) {
        Log.e("set_settings", settingscmd);
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
    };

    public void get_cmds() {
        //RealmResults<Command> res = realm.where(Command.class).findAll();
        /*
        for (int i = 0; i < res.size(); i++) {
            Command a = res.get(i);
            //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready,a.toRequestParams(), mContext));
        }
        */
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
        sp.edit().putInt("max_crash", Integer.valueOf(max_crash)).apply();
        RequestParams resp = new RequestParams();
        resp.put("command", "max_crash,"+max_crash);
        resp.put("wifi_only", "no");
        resp.put("ack", "high");
        Log.i("resp", resp.toString());
        //SensorTagApplicationClass.getInstance().upload_queue.add(new Event(ready, resp, mContext));
        //startBackgroundPerformExecutor();
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

    private List<File> getListFiles(File parentDir, String filename) {
        ArrayList<File> inFiles = new ArrayList<File>();
        File[] files = parentDir.listFiles();
        for (File file : files) {
            if (file.getName().contains(filename) || filename.equals("all")) {
                if (!file.getName().contains(".lock")) {
                    Log.e("file", file.getName());
                    if (file.isDirectory()) {
                        inFiles.addAll(getListFiles(file, filename));
                    } else {
                        inFiles.add(file);
                    }
                }
            }
        }
        return inFiles;
    }


    private File openRealm() {
        File file = this.getExternalFilesDir("/db/");
        if (!file.exists()) {
            boolean result = file.mkdir();
            Log.i("TTT", "Results: " + result);
        }
        return file;
    }


    private void upload(File to_upload) {
        //Uri file = Uri.fromFile(new File(sp.getString(SettingsConfig.REALM_FILENAME, "")));
        Uri file = Uri.fromFile(to_upload);
        StorageReference riversRef = mStorageRef.child(cur_phone_id).child(file.getLastPathSegment());
        UploadTask uploadTask = riversRef.putFile(file);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
                Log.e("api upload", "failed");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Log.e("api upload", "success");
                //Uri downloadUrl = taskSnapshot.getDownloadUrl();
            }
        });
    }


}


