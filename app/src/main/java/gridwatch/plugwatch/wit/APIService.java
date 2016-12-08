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
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.Ack;
import gridwatch.plugwatch.database.Command;
import gridwatch.plugwatch.database.WD;
import gridwatch.plugwatch.firebase.FirebaseCrashLogger;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.LatLngWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;
import gridwatch.plugwatch.utilities.SharedPreferencesToString;
import io.realm.Realm;

import static gridwatch.plugwatch.configs.SMSConfig.AIRTIME;
import static gridwatch.plugwatch.configs.SMSConfig.INTERNET;
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
        PhoneIDWriter r = new PhoneIDWriter(getApplicationContext(), getClass().getName());
        cur_phone_id = r.get_last_value();
        GroupIDWriter p = new GroupIDWriter(getClass().getName());
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
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(), getClass().getName(),
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

        if (isText) {
            sendSMS(cur_cmd.toString());
        }

        if (cmd.contains("uploadall")) { //good
            do_upload_all();
        } else if (cmd.contains("upload_specific")) { //err
            do_upload_specific(cmd);
        } else if (cmd.contains("getrealmsizes")) { //good
            do_realm_sizes();
        } else if (cmd.contains("deletedb_specific")) {
            do_delete_db_specific(cmd);
        }


        else if (cmd.contains("uploadaudio")) { //good
            do_upload_audio();
        } else if (cmd.contains("deleteaudio_specific")) { //good
            do_delete_audio_specific(cmd);
        } else if (cmd.contains("get_audio_size")) { //good
            do_get_audio_size();
        } else if (cmd.contains("nuke_audio")) { //good
            if (!isIndividual) {
                return_err("too large of an id for cmd " + cmd);
                if (!isForced) {
                    return;
                } else {
                    return_err("Forcing " + cmd);
                }
            }
            delete_audiofiles();
        }


        else if (cmd.contains("uploadlogs")) { //good
            do_upload_logs();
        } else if (cmd.contains("uploadlog_specific")) {
            do_upload_logs_specific(cmd);
        } else if (cmd.contains("deletelog_specific")) {
            do_delete_log_specific(cmd);
        } else if (cmd.contains("getlogsizes")) {
            do_log_sizes();
        } else if (cmd.contains("get_specific_log_size")) {
            do_specific_log_size(cmd);
        } else if (cmd.contains("ping")) {
            do_ping(cmd);
        } else if (cmd.contains("sendsms")) {
            send_sms();
        }  else if (cmd.contains("num_realms")) {
            get_num_realms();
        } else if (cmd.contains("location")) {
            get_location();
        } else if (cmd.contains("isonline")) {
            isOnline();
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
        } else if (cmd.equals("getversion")) {
            get_version();
        } else if (cmd.equals("get_settings")) {
            get_settings();
        } else if (cmd.equals("sim")) {
            get_sim();
            get_phonenum();
        } else if (cmd.contains("checkairtime")) {
            check_airtime(cmd);
        } else if (cmd.contains("checkinternet")) {
            check_internet(cmd);
        } else if (cmd.contains("topupairtime")) {
            topup_airtime(cmd);
        } else if (cmd.contains("topupinternet")) {
            topup_internet(cmd);
        } else {
            return_err("invalid command! not understood... " + cmd);
        }
    }


    /* -----------------------------------------------------------
    // IMPLEMENTATIONS

    -------------------------------------------------------------- */

    public void check_internet(String cmd) {
        try {
            if (cmd.contains("kenya")) {
                sp.edit().putString(SettingsConfig.USSD_ACTION, SettingsConfig.USSD_CHECK_INTERNET).commit();
                do_call(INTERNET);
            } else {
                return_err("bad cmd: " + cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " check_internet " + e.getMessage());
        }
    }

    public void check_airtime(String cmd) {
        try {
            if (cmd.contains("kenya")) {
                sp.edit().putString(SettingsConfig.USSD_ACTION, SettingsConfig.USSD_CHECK_AIRTIME).commit();
                do_call(AIRTIME);
            } else {
                return_err("bad cmd: " + cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " check_airtime " + e.getMessage());
        }
    }

    public void topup_airtime(String cmd) {
        try {
            if (cmd.contains("kenya")) {
                try {
                    String pin = cmd.split(":")[1];
                    sp.edit().putString(SettingsConfig.USSD_ACTION, SettingsConfig.USSD_TOPUP_AIRTIME).commit();
                    sp.edit().putString(SettingsConfig.USSD_PIN, pin).commit();
                    do_call(AIRTIME);
                } catch (Exception e) {
                    return_err("bad cmd: " + cmd);
                }
            } else {
                return_err("bad cmd: " + cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " topup_airtime " + e.getMessage());
        }
    }

    public void topup_internet(String cmd) {
        try {
            if (cmd.contains("kenya")) {
                try {
                    String pin = cmd.split(":")[1];
                    sp.edit().putString(SettingsConfig.USSD_ACTION, SettingsConfig.USSD_TOPUP_INTERNET).commit();
                    sp.edit().putString(SettingsConfig.USSD_PIN, pin).commit();
                    do_call(INTERNET);
                } catch (Exception e) {
                    return_err("bad cmd: " + cmd);
                }
            } else {
                return_err("bad cmd: " + cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " topup_internet " + e.getMessage());
        }
    }


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
        Ack a = new Ack(System.currentTimeMillis(), "error: " + msg, cur_phone_id, cur_group_id);
        int new_err_num = sp.getInt(SettingsConfig.NUM_ERR, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_ERR, new_err_num).commit();
        mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.ERR).child(String.valueOf(new_err_num)).setValue(a);
        if (isText) {
            sendSMS(a.toString());
        }
    }

    public void get_sim() {
        try {
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
            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " get_sim " + e.getMessage());
        }
    }

    public void get_phonenum() {
        try {
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
            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " get_phonenum " + e.getMessage());
        }

    }


    public void do_upload_audio() {
        try {
            List<File> files_to_upload = getListFiles(setupFilePaths(), ".wav");
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("uploading", files_to_upload.get(i).getName());
                upload(files_to_upload.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " do_upload_audio " + e.getMessage());
        }
    }




    //Status: needs testing
    public void do_upload_all() {
        try {
            List<File> files_to_upload = getListFiles(openRealm(), "realm");
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("uploading", files_to_upload.get(i).getName());
                upload(files_to_upload.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " do_upload_all " + e.getMessage());
        }

    }




    public void do_get_audio_size() {
        try {
            int num_wavs = 0;
            long size = 0;
            try {
                File dir = setupFilePaths();
                if (dir.exists()) {
                    File[] files = dir.listFiles();
                    if (null != files) {
                        for (int i = 0; i < files.length; i++) {
                            if (files[i].getName().contains(".wav")) {
                                size += files[i].length();
                                num_wavs++;
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            }
            Ack a = new Ack(System.currentTimeMillis(), String.valueOf(size) + " " + String.valueOf(num_wavs), cur_phone_id, cur_group_id);
            int new_num_audio_size = sp.getInt(SettingsConfig.NUM_AUDIO_SIZE, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_AUDIO_SIZE, new_num_audio_size).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_AUDIO_SIZE).child(String.valueOf(new_num_audio_size)).setValue(a);
            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " do_get_audio_size " + e.getMessage());
        }
    }

    public void do_realm_sizes() {
        try {

            long size = 0;
            List<File> files_to_upload = getListFiles(openRealm(), "realm");
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("size", String.valueOf(files_to_upload.get(i).length()));
                size += files_to_upload.get(i).length();
            }
            Ack a = new Ack(System.currentTimeMillis(), String.valueOf(size), cur_phone_id, cur_group_id);
            int new_num_realm_size = sp.getInt(SettingsConfig.NUM_REALM_SIZE, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_REALM_SIZE, new_num_realm_size).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_REALM_SIZE).child(String.valueOf(new_num_realm_size)).setValue(a);
            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " do_realm_size " + e.getMessage());
        }
    }

    public void do_log_sizes() {
        try {

            String report = "";
            long size = 0;
            String secStore = System.getenv("SECONDARY_STORAGE");
            File root = new File(secStore);
            if (!root.exists()) {
                boolean result = root.mkdir();
                Log.i("TTT", "Results: " + result);
            }
            List<File> files_to_upload = getListFiles(root, ".log");
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("getting size", String.valueOf(files_to_upload.get(i).length()));
                report += files_to_upload.get(i).getName() + ":" + String.valueOf(files_to_upload.get(i).length()) + " ";
                size += files_to_upload.get(i).length();
            }
            Ack a = new Ack(System.currentTimeMillis(), report + " tot: " + String.valueOf(size), cur_phone_id, cur_group_id);
            int new_num_log_sizes = sp.getInt(SettingsConfig.NUM_LOG_SIZE, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_LOG_SIZE, new_num_log_sizes).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_LOG_SIZE).child(String.valueOf(new_num_log_sizes)).setValue(a);
            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " do_log_size " + e.getMessage());
        }
    }

    public void do_specific_log_size(String cmd) {
        try {
            long size = 0;
            String filename = cmd.split(":")[1];
            String secStore = System.getenv("SECONDARY_STORAGE");
            File root = new File(secStore);
            if (!root.exists()) {
                boolean result = root.mkdir();
                Log.i("TTT", "Results: " + result);
            }
            List<File> files_to_upload = getListFiles(root, ".log");
            for (int i = 0; i < files_to_upload.size(); i++) {
                if (files_to_upload.get(i).getName().contains(filename)) {
                    Log.e("uploading", String.valueOf(files_to_upload.get(i).length()));
                    size += files_to_upload.get(i).length();
                }
            }
            Ack a = new Ack(System.currentTimeMillis(), filename + " size: " + String.valueOf(size), cur_phone_id, cur_group_id);
            int new_num_specific_log_size = sp.getInt(SettingsConfig.NUM_SPECIFIC_LOG_SIZE, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_SPECIFIC_LOG_SIZE, new_num_specific_log_size).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_SPECIFIC_LOG_SIZE).child(String.valueOf(new_num_specific_log_size)).setValue(a);
            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + cmd + " " + e.getMessage());
        }
    }

    public void get_num_realms() {
        try {
            List<File> files_to_upload = getListFiles(openRealm(), "realm");
            Ack a = new Ack(System.currentTimeMillis(), String.valueOf(files_to_upload.size()), cur_phone_id, cur_group_id);
            int new_realm_num = sp.getInt(SettingsConfig.NUM_REALMS, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_REALMS, new_realm_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_REALMS).child(String.valueOf(new_realm_num)).setValue(a);
            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " get_num_realms " + e.getMessage());
        }

    }



    public void do_upload_logs() {
        try {
            String secStore = System.getenv("SECONDARY_STORAGE");
            File root = new File(secStore);
            if (!root.exists()) {
                boolean result = root.mkdir();
                Log.i("TTT", "Results: " + result);
            }
            List<File> files_to_upload = getListFiles(root, ".log");
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("uploading", files_to_upload.get(i).getName());
                upload(files_to_upload.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " do_upload_logs " + e.getMessage());
        }
    }


    public void do_upload_logs_specific(String cmd) {
        try {
            String secStore = System.getenv("SECONDARY_STORAGE");
            File root = new File(secStore);
            if (!root.exists()) {
                boolean result = root.mkdir();
                Log.i("TTT", "Results: " + result);
            }
            String date = cmd.split(":")[1];
            List<File> files_to_upload = getListFiles(root, date);
            for (int i = 0; i < files_to_upload.size(); i++) {
                Log.e("uploading", files_to_upload.get(i).getName());
                upload(files_to_upload.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + cmd + " " + e.getMessage());        }
    }

    public void do_delete_log_specific(String cmd) {
        try {
            String filename = "";
            String secStore = System.getenv("SECONDARY_STORAGE");
            File root = new File(secStore);
            if (!root.exists()) {
                boolean result = root.mkdir();
                Log.i("TTT", "Results: " + result);
            }
            String date = cmd.split(":")[1];
            Log.e("deleting", date);
            List<File> file_to_delete = getListFiles(root, date);
            for (int i = 0; i < file_to_delete.size(); i++) {
                Log.e("checking", file_to_delete.get(i).getName());
                if (cmd.equals(file_to_delete.get(i).getName())) {
                    filename += file_to_delete.get(i).getName() + ",";
                    file_to_delete.get(i).delete();
                    Log.e("deleting", file_to_delete.get(i).getName());
                }
            }

            Ack a = new Ack(System.currentTimeMillis(), filename, cur_phone_id, cur_group_id);
            int new_delete_log_specific_num = sp.getInt(SettingsConfig.NUM_DELETE_LOG_SPECIFIC, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_DELETE_LOG_SPECIFIC, new_delete_log_specific_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_DELETE_LOG_SPECIFIC).child(String.valueOf(new_delete_log_specific_num)).setValue(a);

        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + cmd + " " + e.getMessage());        }
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
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + cmd + " " + e.getMessage());
        }
    }

    public void do_delete_db_specific(String cmd) {
        try {
            String filename = "";
            String db = cmd.split(":")[1];
            List<File> file_to_delete = getListFiles(openRealm(), cmd);
            for (int i = 0; i < file_to_delete.size(); i++) {
                if (file_to_delete.get(i).getName().contains(cmd)) {
                    Log.e("deleting", file_to_delete.get(i).getName());
                    filename += file_to_delete.get(i).getName() + ",";
                    file_to_delete.get(i).delete();
                }
            }
            Ack a = new Ack(System.currentTimeMillis(), filename, cur_phone_id, cur_group_id);
            int new_delete_db_specific_num = sp.getInt(SettingsConfig.NUM_DELETE_DB_SPECIFIC, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_DELETE_DB_SPECIFIC, new_delete_db_specific_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_DELETE_DB_SPECIFIC).child(String.valueOf(new_delete_db_specific_num)).setValue(a);
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + cmd + " " + e.getMessage());
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
        try {
            GroupIDWriter r = new GroupIDWriter(getClass().getName());
            r.log(String.valueOf(System.currentTimeMillis()), group, "api");

            Ack a = new Ack(System.currentTimeMillis(), group, cur_phone_id, cur_group_id);
            int new_set_group_num = sp.getInt(SettingsConfig.NUM_SET_GROUP, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_SET_GROUP, new_set_group_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_SET_GROUP).child(String.valueOf(new_set_group_num)).setValue(a);
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + group + " " + e.getMessage());
        }
    }
    //Status: needs testing
    public void get_report() {
        try {

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = mContext.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            double battery = level / (double) scale;


            long time = System.currentTimeMillis();

            Log.e("PID", String.valueOf(android.os.Process.myPid()));

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
                    phone_id, group_id, battery, network_size, loc, iemi, is_online, SensorConfig.WD_TYPE_API);
            int new_wd_num = sp.getInt(SettingsConfig.NUM_WD, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_WD, new_wd_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.WD).child(String.valueOf(new_wd_num)).setValue(cur);

            if (isText) {
                sendSMS(cur.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " get_report " + e.getMessage());
        }

    }

    //Status: needs testing
    public void get_free_space() {
        try {
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

            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " get_free_space " + e.getMessage());
        }

    }

    //Status: needs testing
    public void get_stats() {
        try {
            //TODO add in audio settings, total data, total data of each type in realm
            Log.e("stats", "now");
            RequestParams resp = new RequestParams();
            Map<String, ?> keys = sp.getAll();
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
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
                resp.put("lat", String.valueOf(location.getLatitude()));
                resp.put("lng", String.valueOf(location.getLongitude()));
            } catch (SecurityException e) {
                Log.e("loc", e.toString());
            }
            resp.put("free space internal", getAvailableInternalMemorySize());
            resp.put("free space external:", getAvailableExternalMemorySize());
            //resp.put("app_version",BuildConfig.VERSION_NAME);
            resp.put("timestamp", String.valueOf(System.currentTimeMillis()));
            resp.put("uptime", String.valueOf(SystemClock.uptimeMillis()));
            resp.put("reboot_cnt", String.valueOf(sp.getLong("reboot", -1)));
            //resp.put("num_measurments",String.valueOf(res.size()));
            resp.put("battery_life", String.valueOf(batteryPct));
            resp.put("android_id", deviceUuid.toString());
            resp.put("fcm_id", sp.getString("token", "-1"));
            resp.put("fcm_counter", String.valueOf(sp.getLong("fcm_cnt", -1)));
            resp.put("command", "stats");
            resp.put("wifi_only", "no");
            resp.put("ack", "low");
            Log.i("resp", resp.toString());

            Ack a = new Ack(System.currentTimeMillis(), resp.toString(), cur_phone_id, cur_group_id);
            int new_stats = sp.getInt(SettingsConfig.NUM_STATS, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_STATS, new_stats).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.GET_STATS).child(String.valueOf(new_stats)).setValue(a);

            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " get_stats " + e.getMessage());
        }

    }

    //Status: needs testing
    public void get_location() {
        try {
            Log.e("api", "get_location");

            LatLngWriter r = new LatLngWriter(getClass().getName());
            String loc = r.get_last_value();
            Ack a = new Ack(System.currentTimeMillis(), loc, cur_phone_id, cur_group_id);
            int new_loc = sp.getInt(SettingsConfig.NUM_LOCATION, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_LOCATION, new_loc).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.GET_LOCATION).child(String.valueOf(new_loc)).setValue(a);

            if (isText) {
                sendSMS(a.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " get_location " + e.getMessage());
        }

    }



    public void delete_audiofiles() {
        try {
            long size = 0;
            String filename = "";
            File dir = setupFilePaths();
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (null != files) {
                    for (int i = 0; i < files.length; i++) {
                        filename += files[i].getName() + ",";
                        size += files[i].length();
                        files[i].delete();
                    }
                }
            }

            Ack a = new Ack(System.currentTimeMillis(), "deleted: " + filename + " tot size: " + String.valueOf(size), cur_phone_id, cur_group_id);
            int new_num_delete_audio_files_num = sp.getInt(SettingsConfig.NUM_DELETE_AUDIO_FILES, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_DELETE_AUDIO_FILES, new_num_delete_audio_files_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_DELETE_AUDIO_FILES).child(String.valueOf(new_num_delete_audio_files_num)).setValue(a);
        }
        catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad cmd " + " delete_audiofiles " + e.getMessage());

        }
    }

    public void get_settings() {


        try {
            SharedPreferencesToString b = new SharedPreferencesToString(getApplicationContext());
            String to_ret = b.getString();
            Ack a = new Ack(System.currentTimeMillis(), to_ret, cur_phone_id, cur_group_id);
            int new_num_get_settings = sp.getInt(SettingsConfig.NUM_GET_SETTINGS, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_GET_SETTINGS, new_num_get_settings).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_GET_SETTINGS).child(String.valueOf(new_num_get_settings)).setValue(a);
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad parameters " + " get_settings " + " " + e.getMessage());
        }


    }

    public void do_delete_audio_specific(String cmd) {
        try {
            String filename = "";
            String match = cmd.split(":")[1];
            File dir = setupFilePaths();
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (null != files) {
                    for (int i = 0; i < files.length; i++) {
                        if (files[i].getName().contains(match)) {
                            filename += files[i].getName() + ",";
                            files[i].delete();
                        }
                    }
                }
            }

            Ack a = new Ack(System.currentTimeMillis(), "deleted: " + filename, cur_phone_id, cur_group_id);
            int new_num_delete_specific_audio_files_num = sp.getInt(SettingsConfig.NUM_DELETE_SPECIFIC_AUDIO_FILES, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_DELETE_SPECIFIC_AUDIO_FILES, new_num_delete_specific_audio_files_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_DELETE_SPECIFIC_AUDIO_FILES).child(String.valueOf(new_num_delete_specific_audio_files_num)).setValue(a);
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad parameters " + cmd + " " + e.getMessage());
        }
    }


    private File setupFilePaths() {
        String tmpFilePath = "";

        String secStore = System.getenv("SECONDARY_STORAGE");
        File f_secs = new File(secStore);
        if (!f_secs.exists()) {
            boolean result = f_secs.mkdir();
            Log.i("TTT", "Results: " + result);
        }
        tmpFilePath = f_secs.getPath();
        return new File(tmpFilePath, recordingFolder);
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
                    if (isText) {
                        sendSMS("ping: " +String.valueOf(res));
                    }
                }

                @Override
                public void onFinished() {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad parameters " + cmd + " " + e.getMessage());
        }


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
                if (isText) {
                    sendSMS("rebooting");
                }
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
        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            return_err("bad parameters " + "reboot_cmd " + e.getMessage());
        }
    };


    @Override
    public void onDestroy() {
        unregisterReceiver(mConnectivityReceiver);
    }


    public void get_version() {
        try {
            String versionNum = sp.getString(SettingsConfig.VERSION_NUM, "");
            Ack a = new Ack(System.currentTimeMillis(), versionNum, cur_phone_id, cur_group_id);
            int new_num_get_version = sp.getInt(SettingsConfig.NUM_GET_VERSION, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_GET_VERSION, new_num_get_version).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_GET_VERSION).child(String.valueOf(new_num_get_version)).setValue(a);
        } catch (Exception e) {
            return_err("bad parameters " + "get_version " + e.getMessage());
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
        }

    }

    public void isOnline() {
        try {
            Ack a = new Ack(System.currentTimeMillis(), String.valueOf(checkOnline()), cur_phone_id, cur_group_id);
            int new_num_is_online = sp.getInt(SettingsConfig.NUM_IS_ONLINE, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_IS_ONLINE, new_num_is_online).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.NUM_IS_ONLINE).child(String.valueOf(new_num_is_online)).setValue(a);

        }
        catch (Exception e) {
            return_err("bad parameters " + "isOnline " + e.getMessage());
            e.printStackTrace();
            FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
        }
    }

    public boolean checkOnline() {
        ConnectivityManager cm = (ConnectivityManager) getBaseContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        boolean isOnline = netInfo != null && netInfo.isConnectedOrConnecting();
        return isOnline;
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
        smsManager.sendMultipartTextMessage("12012317237", null, smsManager.divideMessage(to_send), null, null);
        smsManager.sendTextMessage("20880", null, "GridWatch :" + cur_phone_id + "," + to_send, null, null);
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
        //File file = this.getExternalFilesDir("/db/");
        String secStore = System.getenv("SECONDARY_STORAGE");
        File f_secs = new File(secStore);
        if (!f_secs.exists()) {
            boolean result = f_secs.mkdir();
            Log.i("TTT", "Results: " + result);
        }
        return f_secs;
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


    private void do_call(String phonenum) {
        Intent callIntent = new Intent(Intent.ACTION_CALL, ussdToCallableUri(phonenum));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        startActivity(callIntent);
    }

}


