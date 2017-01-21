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
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
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

import com.github.javiersantos.appupdater.BuildConfig;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.LocationRequest;
import com.google.firebase.crash.FirebaseCrash;
import com.stealthcopter.networktools.Ping;
import com.stealthcopter.networktools.ping.PingResult;
import com.vistrav.pop.Pop;

import net.grandcentrix.tray.AppPreferences;

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
import gridwatch.plugwatch.database.WD;
import gridwatch.plugwatch.logs.AvgWitWriter;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.LatLngWriter;
import gridwatch.plugwatch.logs.MacWriter;
import gridwatch.plugwatch.logs.NumWitTotalWriter;
import gridwatch.plugwatch.logs.NumWitWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;
import gridwatch.plugwatch.logs.RunningTimeWriter;
import gridwatch.plugwatch.utilities.LogcatService;
import gridwatch.plugwatch.utilities.Private;
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
import static gridwatch.plugwatch.wit.APIService.formatSize;
import static gridwatch.plugwatch.wit.App.getContext;

public class PlugWatchUIActivity extends Activity {

    public String buildStr;

    AppPreferences appPreferences;

    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy HH:mm:ss");

    private String phone_num;

    int cnt = 0;

    private boolean deploy_mode;

    private boolean is_sms_good;

    private static String free_size = "";

    private boolean amPinging;

    private static int num_realms = -1;

    private static long last_time = -1;
    private static long num_wit = -1;
    private static long total_wit = -1;
    private static long num_gw = -1;
    private static boolean is_connected = false;
    private static long total_network_data = -1;
    private static int plugwatchservice_pid = -1;
    private static String realm_filename = "";

    private NumWitTotalWriter num_wit_total_writer;
    private NumWitWriter num_wit_writer;

    private static int failsafe_pid = -1;

    private static boolean is_cross_pair = false;

    private String stickyMac;

    private String email;

    private String avg_running_time;

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
    private boolean is_sdcard;

    private long ms_time_running = -1;

    private String mac_address_str;

    private String wifi_res = "";

    private String avg_string = "";

    private String phone_id;
    private String group_id;
    private PhoneIDWriter phoneIDWriter;
    private GroupIDWriter groupIDWriter;

    private RunningTimeWriter runningTimeWriter;


    private AvgWitWriter avgWitWriter;

    private boolean isRooted;

    private LocationRequest location_rec;

    Date time_created;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        time_created = new Date();



        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(), getClass().getName(),
                    PlugWatchUIActivity.class));
        }

        if (AppConfig.DEBUG) {
            Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
            // Vibrate for 500 milliseconds
            //v.vibrate(500);
        }

        //AppUpdater appUpdater = new AppUpdater(this);
        //appUpdater.setUpdateFrom(UpdateFrom.GOOGLE_PLAY);
        //appUpdater.start();


        Log.e("PlugWatchUIActivity", "onCreate");

        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);

        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        appPreferences     = new AppPreferences(getContext());

        num_wit_total_writer = new NumWitTotalWriter(getClass().getName());
        num_wit_writer = new NumWitWriter(getClass().getName());
        avgWitWriter = new AvgWitWriter(getClass().getName());
        runningTimeWriter = new RunningTimeWriter(getClass().getName());

        location_rec = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1);
        loc_runnable.run(); //get new location

        check_google_play();
        get_email();
        setup_ui();
        setup_build_str();
        setup_watchdog2();
        setup_settings();
        setup_random_gw();
        setup_connection_check();
        setup_watchdog();
        setup_smswatchdog();

        setup_wifi_logger();
        setup_writers();

        BluetoothAdapter.getDefaultAdapter().enable();
        runnable.run(); //start UI loop

    }

    private void setup_writers() {
        if (getBaseContext() == null) {
            Log.e("setup writers", "null");
        }
        phoneIDWriter = new PhoneIDWriter(getBaseContext(), getClass().getName());
        groupIDWriter = new GroupIDWriter(getBaseContext(), getClass().getName());
        //TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //phone_id = telephonyManager.getDeviceId();
        //phoneIDWriter.log(String.valueOf(System.currentTimeMillis()), phone_id, "");
        phone_id = phoneIDWriter.get_last_value();
        group_id = groupIDWriter.get_last_value();
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
                        12112345, //integer constant used to identify the service
                        serviceIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
        am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                interval,
                servicePendingIntent
        );
    }

    private void setup_wifi_logger() {
        ctx = getApplicationContext();
        am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar cal = Calendar.getInstance();
        long interval = SensorConfig.WIFI_LOGGER_CONNECTION_INTERVAL; // 30 seconds in milliseconds
        Intent serviceIntent = new Intent(ctx, WifiLoggerService.class);
        servicePendingIntent =
                PendingIntent.getService(ctx,
                        12323345, //integer constant used to identify the service
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

    private void setup_smswatchdog() {
        ctx = getApplicationContext();
        am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar cal = Calendar.getInstance();
        long interval = SensorConfig.SMS_WATCHDOG_INTERVAL; // 24 hrs in milliseconds
        Intent serviceIntent = new Intent(ctx, SMSWatchdogService.class);
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



    private void setup_watchdog2() {
        Intent watchdog_service2_intent = new Intent(this, WatchdogService2.class);
        startService(watchdog_service2_intent);
    }


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
        Log.e("PlugWatchUIActivity", "onStart");


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
            updateVariables(false);
            updateUI(false);
            handler.postDelayed(this, 1000);
        }
    };

    private class UpdateVariablesTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (mBoundPlugWatchService) {
                try {
                    last_time = mIPlugWatchService.get_last_time();
                    num_gw = mIPlugWatchService.get_num_gw();
//                  num_wit = mIPlugWatchService.get_num_wit();

                    num_wit = Long.valueOf(num_wit_writer.get_last_value());
                    total_wit = Long.valueOf(num_wit_total_writer.get_last_value());


                    ms_time_running =  (new Date()).getTime() - time_created.getTime();

                    appPreferences.put(SettingsConfig.TIME_RUNNING, String.valueOf(ms_time_running));


                    avg_running_time  = runningTimeWriter.get_avg();


                    mIPlugWatchService.set_build_str(buildStr);
                    if (!is_connected) {
                        Intent a = new Intent(getBaseContext(), PlugWatchService.class);
                        a.putExtra(IntentConfig.PLUGWATCHSERVICE_REQ, IntentConfig.START_SCANNING);
                        startService(a);
                    }
                    is_connected = mIPlugWatchService.get_is_connected();
                    total_network_data = sp.getLong(SettingsConfig.TOTAL_DATA, -1);
                    String cur_mac = mIPlugWatchService.get_mac();

                    is_sdcard = externalMemoryAvailable();

                    mIPlugWatchService.set_wifi(wifi_res);

                    num_realms = num_realms() + 1;

                    free_size = getAvailableExternalMemorySize();

                    try {
                        if (mac_address_str != null && cur_mac != null) {
                            if (!cur_mac.equals(mac_address_str)) {
                                MacWriter r = new MacWriter(getClass().getName());
                                r.log(String.valueOf(System.currentTimeMillis()), cur_mac, "n");
                                stickyMac = r.get_last_sticky_value();
                                if (!cur_mac.equals(stickyMac)) {
                                    is_cross_pair = false;
                                } else {
                                    is_cross_pair = true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    avg_string = avgWitWriter.get_last_value();

                    mac_address_str = mIPlugWatchService.get_mac();
                    plugwatchservice_pid = mIPlugWatchService.get_pid();
                    realm_filename = mIPlugWatchService.get_realm_filename();
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
                    sp.edit().putString(SettingsConfig.REALM_FILENAME, realm_filename).commit();
                    sp.edit().putInt(SettingsConfig.PID, plugwatchservice_pid).commit();
                    sp.edit().putString(SettingsConfig.VERSION_NUM, buildStr).commit();
                    sp.edit().putString(SettingsConfig.FREESPACE_INTERNAL, getAvailableInternalMemorySize()).commit();
                    sp.edit().putString(SettingsConfig.FREESPACE_EXTERNAL, free_size).commit();
                    sp.edit().putLong(SettingsConfig.WIT_SIZE, num_wit).commit();
                    sp.edit().putLong(SettingsConfig.TOTAL_WIT_SIZE, total_wit).commit();
                    sp.edit().putLong(SettingsConfig.GW_SIZE, num_gw).commit();
                    sp.edit().putInt(SettingsConfig.NUM_REALMS, num_realms).commit();
                    sp.edit().putLong(SettingsConfig.LAST_WIT, last_time).commit();
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
            return null;
        }
    }


    private void updateVariables(boolean step_override) {
        if (!deploy_mode || step_override) {
            UpdateVariablesTask e = new UpdateVariablesTask();
            e.doInBackground(null);
        }
    }

    private void updateUI(boolean step_override) {

        if (!deploy_mode || step_override) {
            try {
                if (mBoundPlugWatchService) {
                    deploymode_text.setText("Debug Mode");
                    deploymode_text.setTextColor(Color.YELLOW);

                    //RESET COLORS... should mix this in later but...
                    is_lpm_text.setTextColor(App.DEBUG_MODE_COLOR);
                    mac_address_display.setTextColor(App.DEBUG_MODE_COLOR);
                    total_data_text.setTextColor(App.DEBUG_MODE_COLOR);
                    is_cross_paired_text.setTextColor(App.DEBUG_MODE_COLOR);
                    google_play_status_text.setTextColor(App.DEBUG_MODE_COLOR);
                    is_SMS_good_text.setTextColor(App.DEBUG_MODE_COLOR);
                    is_sdcard_text.setTextColor(App.DEBUG_MODE_COLOR);
                    free_size_text.setTextColor(App.DEBUG_MODE_COLOR);
                    num_realm_display.setTextColor(App.DEBUG_MODE_COLOR);
                    num_packets.setTextColor(App.DEBUG_MODE_COLOR);
                    total_packets.setTextColor(App.DEBUG_MODE_COLOR);
                    seconds_since_last.setTextColor(App.DEBUG_MODE_COLOR);
                    num_gw_text.setTextColor(App.DEBUG_MODE_COLOR);
                    sticky_mac_display.setTextColor(App.DEBUG_MODE_COLOR);
                    google_account_text.setTextColor(App.DEBUG_MODE_COLOR);
                    phone_id_cur.setTextColor(App.DEBUG_MODE_COLOR);
                    group_id_cur.setTextColor(App.DEBUG_MODE_COLOR);
                    avg_field.setTextColor(App.DEPLOY_MODE_COLOR);
                    time_since_restart_text.setTextColor(App.DEPLOY_MODE_COLOR);
                    avg_time_running_text.setTextColor(App.DEPLOY_MODE_COLOR);

                    avg_field.setText(avg_string);
                    avg_time_running_text.setText(avg_running_time);

                    num_gw_text.setText(String.valueOf(num_gw));
                    num_packets.setText(String.valueOf(num_wit));
                    total_packets.setText(String.valueOf(total_wit));
                    total_data_text.setText(String.valueOf(total_network_data));
                    status.setText(is_connected ? "Connected" : "Disconnected");
                    status.setTextColor(is_connected ? Color.GREEN : Color.RED);
                    seconds_since_last.setText(sdf.format(new Date(last_time)));
                    free_size_text.setText(free_size);
                    group_id_cur.setText(group_id);
                    phone_id_cur.setText(phone_id);
                    root_status.setText(isRooted ? "Rooted" : "Not Rooted");
                    root_status.setTextColor(isRooted ? Color.GREEN : Color.RED);
                    is_SMS_good_text.setText(is_sms_good ? "SMS Good" : "SMS Not Good");
                    is_SMS_good_text.setTextColor(is_sms_good ? Color.GREEN : Color.RED);
                    mac_address_display.setText(mac_address_str);
                    is_cross_paired_text.setText(is_cross_pair ? "Cross-paired" : "Not Cross-paired");
                    is_cross_paired_text.setTextColor(is_cross_pair ? Color.RED : Color.GREEN);
                    google_play_status_text.setText(is_google_play ? "GP Good" : "GP Need Update");
                    google_play_status_text.setTextColor(is_google_play ? Color.GREEN : Color.RED);
                    is_sdcard_text.setText(is_sdcard ? "SD Present" : "No SD");
                    is_sdcard_text.setTextColor(is_sdcard ? Color.GREEN : Color.RED);
                    is_lpm_text.setText(is_lpm ? "LPM Set" : "LPM Not Set");
                    is_lpm_text.setTextColor(is_lpm ? Color.GREEN : Color.RED);
                    isOnlineText.setText(String.valueOf(sp.getFloat(SettingsConfig.PING_RES, -1)));
                    sticky_mac_display.setText(stickyMac);
                    num_realm_display.setText(String.valueOf(num_realms));

                    if (ms_time_running != -1) {
                        int seconds = (int) (ms_time_running / 1000) % 60;
                        int minutes = (int) ((ms_time_running / (1000 * 60)) % 60);
                        int hours = (int) ((ms_time_running / (1000 * 60 * 60)) % 24);
                        String time = "H: " + String.valueOf(hours) + " M: " + String.valueOf(minutes) + " S: " + String.valueOf(seconds);
                        time_since_restart_text.setText(String.valueOf(time));
                    }
                } else {
                    deploymode_text.setText("Service Disconneced");
                    deploymode_text.setTextColor(Color.RED);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            deploymode_text.setText("Deploy Mode");
            deploymode_text.setTextColor(Color.GREEN);

            is_lpm_text.setText(App.DEPLOY_MODE_TEXT);
            is_lpm_text.setTextColor(App.DEPLOY_MODE_COLOR);

            mac_address_display.setText(App.DEPLOY_MODE_TEXT);
            mac_address_display.setTextColor(App.DEPLOY_MODE_COLOR);

            total_data_text.setText(App.DEPLOY_MODE_TEXT);
            total_data_text.setTextColor(App.DEPLOY_MODE_COLOR);

            is_cross_paired_text.setText(App.DEPLOY_MODE_TEXT);
            is_cross_paired_text.setTextColor(App.DEPLOY_MODE_COLOR);

            google_play_status_text.setText(App.DEPLOY_MODE_TEXT);
            google_play_status_text.setTextColor(App.DEPLOY_MODE_COLOR);

            is_SMS_good_text.setText(App.DEPLOY_MODE_TEXT);
            is_SMS_good_text.setTextColor(App.DEPLOY_MODE_COLOR);

            is_sdcard_text.setText(App.DEPLOY_MODE_TEXT);
            is_sdcard_text.setTextColor(App.DEPLOY_MODE_COLOR);

            free_size_text.setText(App.DEPLOY_MODE_TEXT);
            free_size_text.setTextColor(App.DEPLOY_MODE_COLOR);

            num_realm_display.setText(App.DEPLOY_MODE_TEXT);
            num_realm_display.setTextColor(App.DEPLOY_MODE_COLOR);

            num_packets.setText(App.DEPLOY_MODE_TEXT);
            num_packets.setTextColor(App.DEPLOY_MODE_COLOR);

            total_packets.setText(App.DEPLOY_MODE_TEXT);
            total_packets.setTextColor(App.DEPLOY_MODE_COLOR);

            seconds_since_last.setText(App.DEPLOY_MODE_TEXT);
            seconds_since_last.setTextColor(App.DEPLOY_MODE_COLOR);

            num_gw_text.setText(App.DEPLOY_MODE_TEXT);
            num_gw_text.setTextColor(App.DEPLOY_MODE_COLOR);

            sticky_mac_display.setText(App.DEPLOY_MODE_TEXT);
            sticky_mac_display.setTextColor(App.DEPLOY_MODE_COLOR);

            google_account_text.setText(App.DEPLOY_MODE_TEXT);
            google_account_text.setTextColor(App.DEPLOY_MODE_COLOR);

            phone_id_cur.setText(phone_id + " " + App.DEPLOY_MODE_TEXT);
            phone_id_cur.setTextColor(App.DEPLOY_MODE_COLOR);

            group_id_cur.setText(group_id + " " + App.DEPLOY_MODE_TEXT);
            group_id_cur.setTextColor(App.DEPLOY_MODE_COLOR);

            avg_field.setText(App.DEPLOY_MODE_TEXT);
            avg_field.setTextColor(App.DEPLOY_MODE_COLOR);

            time_since_restart_text.setText(App.DEPLOY_MODE_TEXT);
            time_since_restart_text.setTextColor(App.DEPLOY_MODE_COLOR);

            avg_time_running_text.setText(App.DEPLOY_MODE_TEXT);
            avg_time_running_text.setTextColor(App.DEPLOY_MODE_COLOR);
        }

    }



    private void setup_ui() {

        new Handler(Looper.myLooper()).post(new Runnable() {
            @Override
            public void run() {
                version_number.setText("v." + buildStr);
                //num_packets.setText(String.valueOf(wit_db.size()));
                //num_gw.setText(String.valueOf(gw_db.size()));
                //total_data.setText(String.valueOf(PlugWatchApp.getInstance().get_network_data()));
                //status.setText(PlugWatchApp.getInstance().get_is_connected() ? "Connected" : "Disconnected");
                //status.setTextColor(PlugWatchApp.getInstance().get_is_connected() ? Color.GREEN : Color.RED);


                MacWriter r = new MacWriter(getClass().getName());
                if (r.get_last_value() != null) {
                    sticky_mac_display.setText(r.get_last_value());
                }

                if (email != null) {
                    google_account_text.setText(email);
                    google_account_text.setTextColor(Color.GREEN);
                } else {
                    google_account_text.setText("Problem getting email");
                    google_account_text.setTextColor(Color.RED);
                }

                is_sdcard = externalMemoryAvailable();
                is_sdcard_text.setText(is_sdcard ? "SD Present" : "No SD");
                is_sdcard_text.setTextColor(is_sdcard ? Color.GREEN : Color.RED);

                build_name_text.setText(BuildConfig.VERSION_NAME);

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
            LatLngWriter r = new LatLngWriter(getClass().getName());
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

    @Bind(R.id.deploy_mode_text)
    TextView deploymode_text;

    @Bind(R.id.is_lpm_text)
    TextView is_lpm_text;

    @Bind(R.id.mac_address_display)
    TextView mac_address_display;

    @Bind(R.id.num_packets_text)
    TextView num_packets;

    @Bind(R.id.avg_field)
    TextView avg_field;

    @Bind(R.id.total_wit)
    TextView total_packets;

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

    @Bind(R.id.step_btn)
    Button step_btn;

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

    @Bind(R.id.is_sd_card_text)
    TextView is_sdcard_text;

    @Bind(R.id.phone_id_text)
    EditText phone_id_text;
    @Bind(R.id.group_id_text)
    EditText group_id_text;

    @Bind(R.id.scan_btn)
    Button scan_btn;

    @Bind(R.id.sms_btn)
    Button sms_btn;

    @Bind(R.id.free_size_text)
    TextView free_size_text;

    @Bind(R.id.avg_time_running)
    TextView avg_time_running_text;

    @Bind(R.id.build_name)
    TextView build_name_text;

    @Bind(R.id.time_since_restart)
    TextView time_since_restart_text;

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
        smsManager.sendTextMessage("20880", null, "GridWatch " + phone_id + "," + to_send, null, null);
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


        LatLngWriter l = new LatLngWriter(getClass().getName());
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
        MacWriter r = new MacWriter(getClass().getName());
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

        do_wd();





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

        Intent a = new Intent(this, LogcatService.class);
        startService(a);

        //SharedPreferencesToString a = new SharedPreferencesToString(getApplicationContext());


        //Rebooter reboot = new Rebooter(getApplicationContext(), this.getClass().getName(), new Throwable(this.getClass().getName() + ": REBOOTING DUE TO TEST"));


        //Log.e("crash", null);

        //String a = "0";
        //int b = Integer.parseInt(a);

        /*
        RebootBackOffNumWriter r = new RebootBackOffNumWriter(getClass().getName());
        Log.e("reading", r.get_last_value());
        int a = Integer.getInteger(r.get_last_value());
        r.log(String.valueOf(System.currentTimeMillis()), "1");
        */


        /*
        Intent a = new Intent(this, PlugWatchService.class);
        a.putExtra(IntentConfig.CRASH_PLUGWATCH_SERVICE, IntentConfig.CRASH_PLUGWATCH_SERVICE);
        startService(a);
        */



        /*
        Intent a = new Intent(this, PlugWatchService.class);
        a.putExtra(IntentConfig.FALSE_GW, IntentConfig.FALSE_GW);
        startService(a);
        */


        //Intent a = new Intent(this, Reboot.class);
        //startService(a);

        //Reboot r = new Reboot();
        //r.do_reboot();

        //Rebooter r = new Rebooter(getApplicationContext(), getClass().getName(), new Throwable("test"));

        //Restart r = new Restart();
        //r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, getClass().getName(), new Throwable("restarting due to error"), plugwatchservice_pid);

        //RestartTest rt = new RestartTest();
        //rt.do_restart(getApplicationContext(), this, getClass().getName(), new Throwable("error"), -1);



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
        if (sticky_mac_pwd.getText().toString().startsWith(Private.STICKY_PW)) { //TODO: add in manual sticky
            Log.e("sticky mac", "pwd correct");
            sticky_mac_display.setText(mac_address_str);
            MacWriter r = new MacWriter(getClass().getName());
            r.log(String.valueOf(System.currentTimeMillis()), mac_address_str, "sticky");
            stickyMac = mac_address_str;
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
        if (sticky_mac_pwd.getText().toString().startsWith(Private.DEPLOYMODE_PW)) {
            if (!deploy_mode) {
                deploy_mode = true;
                step_btn.setVisibility(View.VISIBLE);
            } else {

                deploy_mode = false;
                step_btn.setVisibility(View.INVISIBLE);
            }
        }
    }

    @OnClick(R.id.step_btn)
    public void onStepBtnClick() {
        updateVariables(true);
        updateUI(true);
    }

    @OnFocusChange(R.id.group_id_text)
    public void onGroupFocusChange() {
        if (group_id_text.getText().toString().startsWith(Private.GROUP_PW)) {
            save_group_id(group_id_text.getText().toString());
        }
        group_id_text.setText("");
    }


    @OnFocusChange(R.id.sticky_mac_pw)
    public void onStickyMacFocusChange() {
        //sticky_mac_pwd.setText("");
    }


    @OnFocusChange(R.id.phone_id_text)
    public void onPhoneFocusChange() {
        if (phone_id_text.getText().toString().startsWith(Private.ID_PW)) {
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
                    updateUI(false);
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
            String secStore = System.getenv("SECONDARY_STORAGE");
            File f_secs = new File(secStore);

            //File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(f_secs.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return formatSize(availableBlocks * blockSize);
        } else {
            return "FAILED TO GET AVAILABLE EXTERNAL MEM";
        }
    }


    public static boolean externalMemoryAvailable() {
        return Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
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
            //wifi_writer.log(String.valueOf(System.currentTimeMillis()), wifi_res);
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


    private void do_wd() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        double battery = level / (double) scale;
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
        ConnectivityManager cm = (ConnectivityManager) getBaseContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        boolean isOnline = netInfo != null && netInfo.isConnectedOrConnecting();

        WD cur = new WD(time, time_since_last_wit_ms, measurementSize, gwSize, num_realms, versionNum, externalFreespace, internalFreespace,
                phone_id, group_id, battery, network_size, loc, iemi, isOnline, SensorConfig.WD_TYPE_DEPLOY_AUDIT);
        int new_wd_num = sp.getInt(SettingsConfig.NUM_WD, 0) + 1;
        sp.edit().putInt(SettingsConfig.NUM_WD, new_wd_num).commit();
            SmsManager smsManager = SmsManager.getDefault();
            Log.e("texting msg", cur.toString());
            smsManager.sendMultipartTextMessage("12012317237", null, smsManager.divideMessage(cur.toString()), null, null);
            smsManager.sendTextMessage("20880", null, "GridWatch :" + phone_id + "," + cur.toString(), null, null);

    }


}


