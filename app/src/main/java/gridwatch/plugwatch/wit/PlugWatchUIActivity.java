package gridwatch.plugwatch.wit;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Calendar;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnFocusChange;
import gridwatch.plugwatch.IPlugWatchService;
import gridwatch.plugwatch.R;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.utilities.GroupID;
import gridwatch.plugwatch.utilities.PhoneIDWriter;
import gridwatch.plugwatch.utilities.RootChecker;

public class PlugWatchUIActivity extends Activity {

    public String buildStr;

    private static long last_time = -1;
    private static int num_wit = -1;
    private static int num_gw = -1;
    private static boolean is_connected = false;
    private static int total_network_data = -1;

    private static boolean is_mac_whitelisted = false;
    private static String mac_whitelist = "";

    SharedPreferences sp;

    IPlugWatchService mIPlugWatchService;
    boolean mBoundPlugWatchService = false;

    private PendingIntent servicePendingIntent;
    private Handler handler = new Handler(Looper.getMainLooper()); //this is fine for UI
    private Context ctx;
    private AlarmManager am;

    private String mac_address_str;

    private String phone_id;
    private String group_id;
    private PhoneIDWriter phoneIDWriter;
    private GroupID groupIDWriter;

    private boolean isRooted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    PlugWatchUIActivity.class));
        }

        setContentView(R.layout.activity_wit_energy_bluetooth);
        ButterKnife.bind(this);
        phoneIDWriter = new PhoneIDWriter(getApplicationContext());
        groupIDWriter = new GroupID(getApplicationContext());
        sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());


        setup_ui();
        setup_build_str();
        setup_settings();
        setup_root();

        setup_connection_check();
        setup_failsafe_timer_service();
        //setup_plugwatch_service();
    }


    private void setup_failsafe_timer_service() {
        Intent a = new Intent(this, FailsafeTimerService.class);
        startService(a);
    }

    private void setup_root() {

        /*
        if (!isRooted) { //ensure that app is rooted... will be called lots of times...
            RootChecker a = new RootChecker();
            isRooted = a.isRoot();
        }
        */

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
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBoundPlugWatchService) {
            unbindService(plugWatchConnection);
            mBoundPlugWatchService = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent service1Intent = new Intent(this, PlugWatchService.class);
        bindService(service1Intent, plugWatchConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onNewIntent(Intent intent) {}

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
                is_connected = mIPlugWatchService.get_is_connected();
                total_network_data = sp.getInt(SettingsConfig.TOTAL_DATA, -1);
                mac_address_str = mIPlugWatchService.get_mac();
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (java.lang.NullPointerException e) {
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
                seconds_since_last.setText(String.valueOf(last_time));
                group_id_cur.setText(group_id);
                phone_id_cur.setText(phone_id);
                root_status.setText(isRooted ? "Rooted" : "Not Rooted");
                root_status.setTextColor(isRooted ? Color.GREEN : Color.RED);
                mac_address_display.setText(mac_address_str);
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
                phone_id = phoneIDWriter.get_last_value();
                group_id = groupIDWriter.get_last_value();
                version_number.setText("v." + buildStr);
                //num_packets.setText(String.valueOf(wit_db.size()));
                //num_gw.setText(String.valueOf(gw_db.size()));
                //total_data.setText(String.valueOf(PlugWatchApp.getInstance().get_network_data()));
                //status.setText(PlugWatchApp.getInstance().get_is_connected() ? "Connected" : "Disconnected");
                //status.setTextColor(PlugWatchApp.getInstance().get_is_connected() ? Color.GREEN : Color.RED);
                group_id_cur.setText(group_id);
                phone_id_cur.setText(phone_id);
                root_status.setText(isRooted ? "Rooted" : "Not Rooted");
                root_status.setTextColor(isRooted ? Color.GREEN : Color.RED);
            }
        });
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

    @Bind(R.id.mac_toggle)
    Button mac_toggle;
    @Bind(R.id.mac_text)
    TextView mac_text;
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

    @Bind(R.id.phone_id_cur)
    TextView phone_id_cur;
    @Bind(R.id.group_id_cur)
    TextView group_id_cur;

    @Bind(R.id.phone_id_text)
    EditText phone_id_text;
    @Bind(R.id.group_id_text)
    EditText group_id_text;

    @Bind(R.id.scan_btn)
    Button scan_btn;
    @Bind(R.id.root_btn)
    Button root_btn;
    @Bind(R.id.test)
    Button graph_btn;

    @OnClick(R.id.activity_wit_energy_bluetooth)
    public void onBackgroundClick() {
        View r = findViewById(R.id.activity_wit_energy_bluetooth);
        InputMethodManager imm = (InputMethodManager) r.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(r.getWindowToken(), 0);
    }

    @OnClick(R.id.test)
    public void onTestClick() {

    }

    @OnClick(R.id.scan_btn)
    public void onScanClick() {
        Intent a = new Intent(this, PlugWatchService.class);
        a.putExtra(IntentConfig.PLUGWATCHSERVICE_REQ, IntentConfig.START_SCANNING);
        startService(a);
        Log.e("Scanning", "Scanning");
    }

    @OnClick(R.id.root_btn)
    public void onRootClick() {
        RootChecker a = new RootChecker();
        isRooted = a.isRoot();
        updateUI();
    }

    @OnClick(R.id.mac_toggle)
    public void onMacToggleClick() {
        if (mac_toggle.isEnabled()) {
            is_mac_whitelisted = true;
        } else {
            is_mac_whitelisted = false;
        }
        if (mBoundPlugWatchService) {
            try {
                mIPlugWatchService.set_whitelist(is_mac_whitelisted, mac_whitelist);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @OnFocusChange(R.id.mac_text)
    public void onMacFocusChange() {
        mac_whitelist = mac_text.getText().toString();
    }



    @OnFocusChange(R.id.group_id_text)
    public void onGroupFocusChange() {
        if (group_id_text.getText().toString().startsWith("000")) {
            save_group_id(group_id_text.getText().toString());
        }
        group_id_text.setText("");
    }



    @OnFocusChange(R.id.phone_id_text)
    public void onPhoneFocusChange() {
        if (phone_id_text.getText().toString().startsWith("000")) {
            save_phone_id(phone_id_text.getText().toString());
        }
        phone_id_text.setText("");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) { //TODO test
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus) {
            // Close every kind of system dialog
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }
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




}


