package gridwatch.plugwatch.wit;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.LocationRequest;
import com.google.firebase.crash.FirebaseCrash;
import com.stealthcopter.networktools.Ping;
import com.stealthcopter.networktools.ping.PingResult;
import com.vistrav.pop.Pop;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnFocusChange;
import gridwatch.plugwatch.IFailsafeTimerService;
import gridwatch.plugwatch.IPlugWatchService;
import gridwatch.plugwatch.R;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.LatLngWriter;
import gridwatch.plugwatch.logs.MacWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;
import gridwatch.plugwatch.logs.WifiWriter;
import gridwatch.plugwatch.utilities.Restart;
import gridwatch.plugwatch.utilities.RootChecker;
import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static gridwatch.plugwatch.configs.SMSConfig.AIRTIME;
import static gridwatch.plugwatch.configs.SMSConfig.INTERNET;
import static gridwatch.plugwatch.wit.APIService.externalMemoryAvailable;
import static gridwatch.plugwatch.wit.APIService.formatSize;

public class PlugWatchUIActivity extends Activity {

    public String buildStr;

    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy HH:mm:ss");

    private String phone_num;

    int cnt = 0;

    private boolean deploy_mode;

    private boolean is_sms_good;

    private boolean amPinging;

    private static int num_realms = -1;

    private static long last_time = -1;
    private static int num_wit = -1;
    private static int num_gw = -1;
    private static boolean is_connected = false;
    private static long total_network_data = -1;
    private static int plugwatchservice_pid = -1;
    private static String realm_filename = "";

    private static WifiWriter wifi_writer;

    private static int failsafe_pid = -1;

    private static boolean is_cross_pair = false;


    private String email;

    private static boolean is_mac_whitelisted = false;
    private static String mac_whitelist = "";

    SharedPreferences sp;

    IPlugWatchService mIPlugWatchService;
    boolean mBoundPlugWatchService = false;

    IFailsafeTimerService mIFailsafeTimerService;
    boolean mBoundFailsafeTimerService = false;

    private PendingIntent servicePendingIntent;
    private Handler handler = new Handler(Looper.getMainLooper()); //this is fine for UI
    private Handler loc_handler = new Handler(Looper.getMainLooper()); //this is fine for UI

    private Context ctx;
    private AlarmManager am;

    private boolean is_google_play;

    private boolean is_lpm;

    private String mac_address_str;

    private String wifi_res = "";

    private String phone_id;
    private String group_id;
    private PhoneIDWriter phoneIDWriter;
    private GroupIDWriter groupIDWriter;

    private boolean isRooted;

    private LocationRequest location_rec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    PlugWatchUIActivity.class));
        }

        if (AppConfig.DEBUG) {
            Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
            // Vibrate for 500 milliseconds
            v.vibrate(500);
        }

        //AppUpdater appUpdater = new AppUpdater(this);
        //appUpdater.start();



        wifi_writer = new WifiWriter(getApplicationContext());
        phoneIDWriter = new PhoneIDWriter(getApplicationContext());
        groupIDWriter = new GroupIDWriter(getApplicationContext());
        //TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //phone_id = telephonyManager.getDeviceId();
        //phoneIDWriter.log(String.valueOf(System.currentTimeMillis()), phone_id, "");
        phone_id = phoneIDWriter.get_last_value();
        group_id = groupIDWriter.get_last_value();


        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);

        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        location_rec = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1);
        check_google_play();


        loc_runnable.run(); //get new location
        get_email();
        setup_ui();
        setup_build_str();
        setup_settings();
        setup_root();
        setup_connection_check();
        setup_watchdog();
        BluetoothAdapter.getDefaultAdapter().enable();


    }


    private void setup_root() {

        /*
        if (!isRooted) { //ensure that app is rooted... will be called lots of times...
            RootChecker a = new RootChecker();
            isRooted = a.isRoot();
        }
        */

    }

    private int num_realms() {
        List<File> files_to_upload = getListFiles(openRealm(), "realm");
        return files_to_upload.size();
    }

    private List<File> getListFiles(File parentDir, String filename) {
        ArrayList<File> inFiles = new ArrayList<File>();
        File[] files = parentDir.listFiles();
        for (File file : files) {
            if (file.getName().contains(filename) || filename.equals("all")) {
                if (!file.getName().contains(".lock")) {
                    //Log.e("file", file.getName());
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


    private void check_google_play() {
        int state = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
        if (state == ConnectionResult.SUCCESS) {
            is_google_play = true;
        } else {
            is_google_play = false;

        }
    }

    private void setup_connection_check() {
        ctx = getApplicationContext();
        am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar cal = Calendar.getInstance();
        long interval = SensorConfig.CONNECTION_INTERVAL; // 30 seconds in milliseconds
        Intent serviceIntent = new Intent(ctx, ConnectionCheckService.class);
        servicePendingIntent =
                PendingIntent.getService(ctx,
                        123512345, //integer constant used to identify the service
                        serviceIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
        am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                interval,
                servicePendingIntent
        );
    }

    private void setup_watchdog() {
        ctx = getApplicationContext();
        am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar cal = Calendar.getInstance();
        long interval = SensorConfig.WATCHDOG_INTERVAL; // 24 hrs in milliseconds
        Intent serviceIntent = new Intent(ctx, WatchdogService.class);
        servicePendingIntent =
                PendingIntent.getService(ctx,
                        321321321, //integer constant used to identify the service
                        serviceIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
        am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                interval,
                servicePendingIntent
        );
    }

    private void setup_random_gw() {
        ctx = getApplicationContext();
        am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar cal = Calendar.getInstance();
        long interval = SensorConfig.GRIDWATCH_INTERVAL;
        Intent serviceIntent = new Intent(ctx, GridWatchStarterService.class);
        servicePendingIntent =
                PendingIntent.getService(ctx,
                        321321321, //integer constant used to identify the service
                        serviceIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
        am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                interval,
                servicePendingIntent
        );
    }


    /*
    private void setup_plugwatch_service() {
        Intent plug_watch_intent = new Intent(this, PlugWatchService.class);
        plug_watch_intent.putExtra(IntentConfig.PLUGWATCHSERVICE_REQ, IntentConfig.START_SCANNING);
        startService(plug_watch_intent);
    }
    */

    private void setup_settings() {
        sp.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0).commit();
    }


    private void setup_build_str() {
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        buildStr = String.valueOf(pInfo.versionCode);
    }


    //////////////////////
    // Lifecycle and Interface
    /////////////////////
    @Override
    protected void onResume() {
        super.onResume();
        App.activityResumed();

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBoundPlugWatchService) {
            unbindService(plugWatchConnection);
            mBoundPlugWatchService = false;
        }

        if (mBoundFailsafeTimerService) {
            unbindService(failSafeConnection);
            mBoundFailsafeTimerService = false;
        }
        App.activityStopped();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent service1Intent = new Intent(this, PlugWatchService.class);
        service1Intent.setAction(IntentConfig.START_SCANNING);
        bindService(service1Intent, plugWatchConnection, Context.BIND_AUTO_CREATE);

        /*
        Intent service1Intent = new Intent(this, PlugWatchService.class);
        service1Intent.setAction(IntentConfig.START_SCANNING);
        bindService(service1Intent, plugWatchConnection, Context.BIND_AUTO_CREATE);
        */

        //Intent failsafeService = new Intent(this, FailsafeTimerService.class);
        //bindService(failsafeService, failSafeConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.activityPaused();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBoundPlugWatchService) {
            unbindService(plugWatchConnection);
            mBoundPlugWatchService = false;
        }
        App.activityStopped();
    }

    private ServiceConnection plugWatchConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mIPlugWatchService = IPlugWatchService.Stub.asInterface(service);
            mBoundPlugWatchService = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e("onServiceDisconnected", "Service1 has unexpectedly disconnected");
            mIPlugWatchService = null;
            mBoundPlugWatchService = false;
            Restart r = new Restart();
            r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, getClass().getName(), new Throwable("service disconnected"), -1);
        }
    };

    private ServiceConnection failSafeConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mIFailsafeTimerService = IFailsafeTimerService.Stub.asInterface(service);
            mBoundFailsafeTimerService = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e("onServiceDisconnected", "Service1 has unexpectedly disconnected");
            mIFailsafeTimerService = null;
            mBoundFailsafeTimerService = false;
        }
    };


    ///////////////////////
    // LOC
    ///////////////////////
    private Runnable loc_runnable = new Runnable() {
        public void run() {
            update_loc();
            loc_handler.postDelayed(this, 1000 * 60 * 60 * 8);
        }
    };


    //////////////////////
    // UI
    /////////////////////
    private Runnable runnable = new Runnable() {
        public void run() {
            updateVariables();
            updateUI();
            handler.postDelayed(this, 1000);
        }
    };

    private void updateVariables() {
        if (mBoundPlugWatchService) {
            try {
                last_time = mIPlugWatchService.get_last_time();
                sp.edit().putLong(SettingsConfig.LAST_WIT, last_time).commit();
                num_gw = mIPlugWatchService.get_num_gw();
                num_wit = mIPlugWatchService.get_num_wit();
                mIPlugWatchService.set_build_str(buildStr);
                if (!is_connected) {
                    Intent a = new Intent(this, PlugWatchService.class);
                    a.putExtra(IntentConfig.PLUGWATCHSERVICE_REQ, IntentConfig.START_SCANNING);
                    startService(a);
                }
                is_connected = mIPlugWatchService.get_is_connected();
                total_network_data = sp.getLong(SettingsConfig.TOTAL_DATA, -1);
                String cur_mac = mIPlugWatchService.get_mac();

                getWifi(); //TODO talk with jay
                mIPlugWatchService.set_wifi(wifi_res);

                num_realms = num_realms();


                try {
                    if (mac_address_str != null && cur_mac != null) {
                        if (!cur_mac.equals(mac_address_str)) {
                            MacWriter r = new MacWriter(getApplicationContext());
                            r.log(String.valueOf(System.currentTimeMillis()), cur_mac, "n");
                            String sticky = r.get_last_sticky_value();
                            sticky_mac_display.setText(sticky);
                            if (!cur_mac.equals(sticky)) {
                                is_cross_pair = false;
                            } else {
                                is_cross_pair = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mac_address_str = mIPlugWatchService.get_mac();
                plugwatchservice_pid = mIPlugWatchService.get_pid();
                sp.edit().putInt(SettingsConfig.PID, plugwatchservice_pid).commit();
                realm_filename = mIPlugWatchService.get_realm_filename();
                sp.edit().putString(SettingsConfig.REALM_FILENAME, realm_filename).commit();
                if (amPinging) {
                    Ping.onAddress("8.8.8.8").doPing(new Ping.PingListener() {
                        @Override
                        public void onResult(PingResult pingResult) {
                            sp.edit().putFloat(SettingsConfig.PING_RES, pingResult.getTimeTaken()).commit();
                        }

                        @Override
                        public void onFinished() {

                        }
                    });
                }
                sp.edit().putString(SettingsConfig.VERSION_NUM, buildStr).commit();
                sp.edit().putString(SettingsConfig.FREESPACE_INTERNAL, getAvailableInternalMemorySize()).commit();
                sp.edit().putString(SettingsConfig.FREESPACE_EXTERNAL, getAvailableExternalMemorySize()).commit();
                sp.edit().putInt(SettingsConfig.WIT_SIZE, num_wit).commit();
                sp.edit().putInt(SettingsConfig.GW_SIZE, num_gw).commit();
                sp.edit().putInt(SettingsConfig.NUM_REALMS, num_realms).commit();
                sp.edit().clear();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (java.lang.NullPointerException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        if (mBoundFailsafeTimerService) {
            try {
                mIFailsafeTimerService.send_is_connected(is_connected);
                mIFailsafeTimerService.send_last(last_time);
                mIFailsafeTimerService.send_pid_of_plugwatch_service(plugwatchservice_pid);
                failsafe_pid = mIFailsafeTimerService.get_pid();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateUI() {
        try {
            if (mBoundPlugWatchService) {
                num_gw_text.setText(String.valueOf(num_gw));
                num_packets.setText(String.valueOf(num_wit));
                total_data_text.setText(String.valueOf(total_network_data));
                status.setText(is_connected ? "Connected" : "Disconnected");
                status.setTextColor(is_connected ? Color.GREEN : Color.RED);
                seconds_since_last.setText(sdf.format(new Date(last_time)));
                group_id_cur.setText(group_id);
                phone_id_cur.setText(phone_id);
                root_status.setText(isRooted ? "Rooted" : "Not Rooted");
                root_status.setTextColor(isRooted ? Color.GREEN : Color.RED);
                is_SMS_good_text.setText(is_sms_good ? "SMS Good" : "SMS Not Good");
                is_SMS_good_text.setTextColor(is_sms_good ? Color.GREEN : Color.RED);
                mac_address_display.setText(mac_address_str);
                is_cross_paired_text.setText(is_cross_pair ? "Cross-paired" : "Not Cross-paired");
                is_cross_paired_text.setTextColor(is_cross_pair ? Color.RED : Color.GREEN);
                is_lpm_text.setText(is_lpm ? "LPM Set" : "LPM Not Set");
                is_lpm_text.setTextColor(is_lpm ? Color.GREEN : Color.RED);
                isOnlineText.setText(String.valueOf(sp.getFloat(SettingsConfig.PING_RES, -1)));
                if (sticky_mac_display.getText().equals("start")) {
                    MacWriter r = new MacWriter(getApplicationContext());
                    if (r.get_last_value() != null) {
                        sticky_mac_display.setText(r.get_last_value());
                    }
                }
                num_realm_display.setText(String.valueOf(num_realms));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void setup_ui() {
        runnable.run(); //start UI loop
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                version_number.setText("v." + buildStr);
                //num_packets.setText(String.valueOf(wit_db.size()));
                //num_gw.setText(String.valueOf(gw_db.size()));
                //total_data.setText(String.valueOf(PlugWatchApp.getInstance().get_network_data()));
                //status.setText(PlugWatchApp.getInstance().get_is_connected() ? "Connected" : "Disconnected");
                //status.setTextColor(PlugWatchApp.getInstance().get_is_connected() ? Color.GREEN : Color.RED);


                MacWriter r = new MacWriter(getApplicationContext());
                if (r.get_last_value() != null) {
                    sticky_mac_display.setText(r.get_last_value());
                }

                if (email != null) {
                    google_account_text.setText(email);
                }

                group_id_cur.setText(group_id);
                phone_id_cur.setText(phone_id);
                root_status.setText(isRooted ? "Rooted" : "Not Rooted");
                root_status.setTextColor(isRooted ? Color.GREEN : Color.RED);
                google_play_status_text.setText(is_google_play ? "GP Good" : "GP Need Update");
                google_play_status_text.setTextColor(is_google_play ? Color.GREEN : Color.RED);

                is_lpm = check_lpm();
                is_lpm_text.setText(is_lpm ? "LPM Set" : "LPM Not Set");
                is_lpm_text.setTextColor(is_lpm ? Color.GREEN : Color.RED);
            }
        });
    }


    private void update_loc() {
        ReactiveLocationProvider locationProvider = new ReactiveLocationProvider(this);
        Observable<JSONObject> loc = locationProvider
                .getUpdatedLocation(location_rec)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Func1<Location, JSONObject>() {
                    @Override
                    public JSONObject call(Location loc) {
                        return loc_transform(loc);
                    }
                });
        loc.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .single()
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends JSONObject>>() {
                    @Override
                    public Observable<? extends JSONObject> call(Throwable throwable) {
                        FirebaseCrash.log("rx error" + throwable.getMessage());
                        return null;
                    }
                })
                .subscribe(new Action1<JSONObject>() {
                    @Override
                    public void call(JSONObject o) {
                    }
                });
    }

    private JSONObject loc_transform(Location location) {
        try {
            LatLngWriter r = new LatLngWriter(this);
            r.log(String.valueOf(System.currentTimeMillis()), String.valueOf(location.getLatitude()) + "," + String.valueOf(location.getLongitude()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            return new JSONObject()
                    .put("lat", String.valueOf(location.getLatitude()))
                    .put("lng", String.valueOf(location.getLongitude()))
                    .put("acc", String.valueOf(location.getAccuracy()))
                    .put("alt", String.valueOf(location.getAltitude()))
                    .put("time", String.valueOf(location.getElapsedRealtimeNanos()))
                    .put("speed", String.valueOf(location.getSpeed()))
                    .put("provider", String.valueOf(location.getProvider()));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    /////////////////////////////////
    // UI Bindings and OnClicks
    ////////////////////////////////
    @Bind(R.id.version_number)
    TextView version_number;

    @Bind(R.id.status)
    TextView status;
    @Bind(R.id.root_status)
    TextView root_status;

    @Bind(R.id.is_lpm_text)
    TextView is_lpm_text;

    @Bind(R.id.mac_address_display)
    TextView mac_address_display;

    @Bind(R.id.num_packets_text)
    TextView num_packets;
    @Bind(R.id.seconds_since_last_text)
    TextView seconds_since_last;
    @Bind(R.id.total_data_text)
    TextView total_data_text;
    @Bind(R.id.num_gw_text)
    TextView num_gw_text;
    @Bind(R.id.isOnlineText)
    TextView isOnlineText;

    @Bind(R.id.sticky_btn)
    Button sticky_btn;
    @Bind(R.id.sticky_mac_display)
    TextView sticky_mac_display;
    @Bind(R.id.sticky_mac_pw)
    EditText sticky_mac_pwd;
    @Bind(R.id.is_cross_paired_text)
    TextView is_cross_paired_text;

    @Bind(R.id.ping_btn)
    Button ping_btn;

    @Bind(R.id.google_play_state)
    TextView google_play_status_text;

    @Bind(R.id.deployment_audit)
    Button deployment_audit_btn;
    @Bind(R.id.deploy_mode_btn)
    Button deploy_mode_btn;


    @Bind(R.id.phone_id_cur)
    TextView phone_id_cur;
    @Bind(R.id.group_id_cur)
    TextView group_id_cur;

    @Bind(R.id.is_SMS_good)
    TextView is_SMS_good_text;


    @Bind(R.id.phone_id_text)
    EditText phone_id_text;
    @Bind(R.id.group_id_text)
    EditText group_id_text;

    @Bind(R.id.scan_btn)
    Button scan_btn;

    @Bind(R.id.sms_btn)
    Button sms_btn;

    @Bind(R.id.test)
    Button test_btn;
    @Bind(R.id.play_store_btn)
    Button play_store_btn;
    @Bind(R.id.cmd_line_btn)
    Button cmd_line_btn;
    @Bind(R.id.write_lpm)
    Button write_lpm;

    @Bind(R.id.update_pw)
    Button update_pw;

    @Bind(R.id.google_account_text)
    TextView google_account_text;

    @Bind(R.id.num_realm_display)
    TextView num_realm_display;

    @OnClick(R.id.sms_btn)
    public void onSMSClick() {
        String to_send = "test";
        SmsManager smsManager = SmsManager.getDefault();
        Log.e("texting msg", to_send);
        smsManager.sendTextMessage("12012317237", null, to_send, null, null);
        smsManager.sendTextMessage("20880", null, "GridWatch: " + phone_id + "," + to_send, null, null);
        is_sms_good = true;
    }

    @OnClick(R.id.deployment_audit)
    public void onDeploymentAuditClick() {
        //get has checked google?
        //does have root?
        //get imei
        //does have lpm?
        //has set group_id?
        //has set phone_id?
        //get current data
        //get phone number
        //get current airtime
        //get has set a sticky mac?
        //is play setup for auto update?
        //has current app?
        //get current location


        //is play running
        //has settings set correctly
        //check sms

        LatLngWriter l = new LatLngWriter(this);
        String loc = l.get_last_value();
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String iemi = telephonyManager.getDeviceId();


        phone_num = sp.getString(SettingsConfig.PHONE_NUM, "");
        String airtime = sp.getString(SettingsConfig.AIRTIME, "");
        String internet = sp.getString(SettingsConfig.INTERNET, "");
        Pop.on(this).with().title("Useful information").body("loc: " + loc + " \niemi: " + iemi + " \nphone num: " + phone_num + " airtime: " + airtime + "\ninternet: " + internet).show();


        RootChecker rootChecker = new RootChecker(getBaseContext());
        if (!rootChecker.isRoot()) {
            Pop.on(this).with().title("Critical").body("You are not root.").show();
        }

        String p_id = phoneIDWriter.get_last_value();
        if (p_id.equals(iemi)) {
            Pop.on(this).with().title("Warning").body("Phone ID is IEMI. Did you forget to set?").show();
        }
        String g_id = groupIDWriter.get_last_value();
        if (g_id.equals("-1")) {
            Pop.on(this).with().title("Warning").body("Group ID is -1. Did you forget to set?").show();
        }
        if (!check_lpm()) {
            Pop.on(this).with().title("Critical").body("LPM is bad.").show();
        }
        MacWriter r = new MacWriter(getApplicationContext());
        String sticky = r.get_last_sticky_value();
        Log.e("sticky", sticky);
        if (!sticky.contains(":")) {
            Pop.on(this).with().title("Critical").body("Did you forget to set a sticky MAC?").show();
        }
        Pop.on(this).with().title("Critical").body("Reminder... set google play for auto update").when(new Pop.Yah() {
            @Override
            public void clicked(DialogInterface dialog, View view) {
                Toast.makeText(getApplicationContext(), "I promise I set google play for auto update.", Toast.LENGTH_SHORT);
            }
        }).when(new Pop.Nah() {
            @Override
            public void clicked(DialogInterface dialog, View view) {
                final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        }).show();
        Pop.on(this).with().title("Warning").body("Reminder... update app from play").when(new Pop.Yah() {
            @Override
            public void clicked(DialogInterface dialog, View view) {
                Toast.makeText(getApplicationContext(), "I promise I checked for an update.", Toast.LENGTH_SHORT);
            }
        }).when(new Pop.Nah() {
            @Override
            public void clicked(DialogInterface dialog, View view) {
                final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        }).show();
        Pop.on(this).with().title("Check Airtime Balance").body("Do it.").when(new Pop.Yah() {
            @Override
            public void clicked(DialogInterface dialog, View view) {
                sp.edit().putString(SettingsConfig.USSD_ACTION, SettingsConfig.USSD_CHECK_AIRTIME).commit();
                do_call(AIRTIME);
            }
        }).show();

        Pop.on(this).with().title("Check Data Balance").body("Do it.").when(new Pop.Yah() {
            @Override
            public void clicked(DialogInterface dialog, View view) {
                sp.edit().putString(SettingsConfig.USSD_ACTION, SettingsConfig.USSD_CHECK_INTERNET).commit();
                do_call(INTERNET);
            }
        }).show();

        Pop.on(this).with().title("Get Phone Number").body("Do it.").when(new Pop.Yah() {
            @Override
            public void clicked(DialogInterface dialog, View view) {
                sp.edit().putString(SettingsConfig.USSD_ACTION, SettingsConfig.USSD_CHECK_PHONENUM).commit();
                do_call(AIRTIME);
            }
        }).show();

        Pop.on(this).with().title("Check Settings").body("remember to check settings").show();






    }

    public void set_settings() {

    }

    @OnClick(R.id.update_pw)
    public void onUpdatePWClick() {
        final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }


    @OnClick(R.id.play_store_btn)
    public void onPlayStoreUpdateClick() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + "com.google.android.gms")));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")));
        }
    }

    @OnClick(R.id.activity_home)
    public void onBackgroundClick() {
        View r = findViewById(R.id.activity_home);
        InputMethodManager imm = (InputMethodManager) r.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(r.getWindowToken(), 0);
    }

    @OnClick(R.id.test)
    public void onTestClick() {


        /*
        Intent a = new Intent(this, PlugWatchService.class);
        a.putExtra(IntentConfig.FALSE_GW, IntentConfig.FALSE_GW);
        sendBroadcast(a);
        */


        Intent a = new Intent(this, PlugWatchService.class);
        a.putExtra(IntentConfig.FALSE_GW, IntentConfig.FALSE_GW);
        startService(a);



        //Intent a = new Intent(this, Reboot.class);
        //startService(a);

        //Rebooter r = new Rebooter(getApplicationContext(), new Throwable("test"));

        //Restart r = new Restart();
        //r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, new Throwable("restarting due to timeout"), plugwatchservice_pid);
    }

    @OnClick(R.id.scan_btn)
    public void onScanClick() {
        Intent a = new Intent(this, PlugWatchService.class);
        a.putExtra(IntentConfig.PLUGWATCHSERVICE_REQ, IntentConfig.START_SCANNING);
        startService(a);
        Log.e("Scanning", "Scanning");
    }

    @OnClick(R.id.sticky_btn)
    public void onStickyMacClick() {
        Log.e("sticky mac", sticky_mac_pwd.getText().toString());
        if (sticky_mac_pwd.getText().toString().startsWith("000")) { //TODO: add in manual sticky
            Log.e("sticky mac", "pwd correct");
            sticky_mac_display.setText(mac_address_str);
            MacWriter r = new MacWriter(getApplicationContext());
            r.log(String.valueOf(System.currentTimeMillis()), mac_address_str, "sticky");
            sp.edit().putString(SettingsConfig.STICKY_MAC, mac_address_str).commit();
            Log.e("sticky mac", "set to: " + mac_address_str);
        } else {
            Log.e("sticky mac", "pwd incorrect");
        }
    }

    @OnClick(R.id.cmd_line_btn)
    public void onCmdLineClick() {
        Intent a = new Intent(this, CommandLineActivity.class);
        startActivity(a);

    }

    @OnClick(R.id.deploy_mode_btn)
    public void onDeployModeClick() {
        if (sticky_mac_pwd.getText().toString().startsWith("000")) {
            if (deploy_mode) {
                deploy_mode = false;
            } else {
                deploy_mode = true;
            }
        }
    }

    @OnFocusChange(R.id.group_id_text)
    public void onGroupFocusChange() {
        if (group_id_text.getText().toString().startsWith("000")) {
            save_group_id(group_id_text.getText().toString());
        }
        group_id_text.setText("");
    }


    @OnFocusChange(R.id.sticky_mac_pw)
    public void onStickyMacFocusChange() {
        sticky_mac_pwd.setText("");
    }

    @OnFocusChange(R.id.phone_id_text)
    public void onPhoneFocusChange() {
        if (phone_id_text.getText().toString().startsWith("000")) {
            save_phone_id(phone_id_text.getText().toString());
        }
        phone_id_text.setText("");
    }

    @OnClick(R.id.ping_btn)
    public void onOnlineTextClick() {

        if (amPinging) {
            isOnlineText.setText("ping is off");
            amPinging = false;
        } else {
            amPinging = true;
        }

    }

    @OnClick(R.id.write_lpm)
    public void onWriteLPMClick() {
        try {

            java.lang.Process proc = Runtime.getRuntime()
                    .exec(new String[]{"su", "-c", "mount -o remount,rw /system /system"});
            proc.waitFor();

            String data = "#!/system/bin/sh\n" + "/system/bin/reboot\n";
            String filename = "lpm";
            FileOutputStream outputStream;
            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(data.getBytes());
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            java.lang.Process proc2 = Runtime.getRuntime()
                    .exec(new String[]{"su", "-c", "cp " + this.getFilesDir() + "/lpm ../../../../system/bin/."});
            proc2.waitFor();


        } catch (Exception ex) {
            ex.printStackTrace();
            if (ex.getCause().getMessage().toString().equals("Permission denied")) {

            }
        }
        is_lpm = check_lpm();
    }

    public boolean check_lpm() {
        BufferedReader buffered_reader = null;
        try {
            buffered_reader = new BufferedReader(new FileReader("/system/bin/lpm"));
            String line;
            while ((line = buffered_reader.readLine()) != null) {
                Log.e("check lpm", line);
                if (line.contains("/system/bin/sh")) {
                    if (buffered_reader != null) {
                        buffered_reader.close();
                    }
                    isRooted = true;
                    updateUI();
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (buffered_reader != null)
                    buffered_reader.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) { //TODO test
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            // Close every kind of system dialog
            //Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            //sendBroadcast(closeDialog);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public void onCmdLine() {
        Intent e = new Intent(this, CommandLineActivity.class);
        startActivity(e);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.cmdline:
                onCmdLine();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void save_group_id(String group_id_to_save) {
        groupIDWriter.log(String.valueOf(System.currentTimeMillis()), group_id_to_save.substring(3), "");
        group_id = group_id_to_save.substring(3);
        Log.e("group id", "writing");
        Log.e("new group id", group_id);
        try {
            mIPlugWatchService.set_group_id(group_id);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void save_phone_id(String phone_id_to_save) { //TODO: persist to file
        phoneIDWriter.log(String.valueOf(System.currentTimeMillis()), phone_id_to_save.substring(3), "");
        phone_id = phone_id_to_save.substring(3);
        Log.e("phone id", "writing");
        Log.e("new phone id", phone_id);
        try {
            mIPlugWatchService.set_phone_id(phone_id);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

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


    private void getWifi() {
        try {
            WifiManager mWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
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
                Log.d("wifi err", "wifi not enabled");
                FirebaseCrash.log("wifi not enabled");
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

    private void get_email() {
        Pattern emailPattern = Patterns.EMAIL_ADDRESS; // API level 8+
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Account[] accounts = AccountManager.get(this).getAccounts();
        for (Account account : accounts) {
            if (emailPattern.matcher(account.name).matches()) {
                 email = account.name;
            }
        }
    }

    private void do_call(String phonenum) {
        Intent callIntent = new Intent(Intent.ACTION_CALL, ussdToCallableUri(phonenum));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        startActivity(callIntent);
    }

    private Uri ussdToCallableUri(String ussd) {
        String uriString = "";
        if (!ussd.startsWith("tel:"))
            uriString += "tel:";

        for (char c : ussd.toCharArray()) {

            if (c == '#')
                uriString += Uri.encode("#");
            else
                uriString += c;
        }
        return Uri.parse(uriString);
    }




}


