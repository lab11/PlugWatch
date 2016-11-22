package gridwatch.plugwatch.wit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnFocusChange;
import gridwatch.plugwatch.R;
import gridwatch.plugwatch.WitEnergyVersionTwo;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.DatabaseConfig;
import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.GWDump;
import gridwatch.plugwatch.database.ID;
import gridwatch.plugwatch.database.MeasurementRealm;
import gridwatch.plugwatch.gridWatch.GridWatch;
import gridwatch.plugwatch.utilities.RootChecker;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;

public class WitEnergyBluetoothActivity extends Activity {

    private RxBleClient rxBleClient;
    private Subscription scanSubscription;
    private RxBleDevice bleDevice;
    ArrayList<byte[]> to_write_slowly = new ArrayList<>();
    private String mCurrent;
    private String mFrequency;
    private String mPower;
    private String mPowerFactor;
    private String mVoltage;
    private PublishSubject<Void> disconnectTriggerSubject = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservable;
    Realm realm = Realm.getDefaultInstance();
    final RealmResults<MeasurementRealm> wit_db = realm.where(MeasurementRealm.class).findAll();
    final RealmResults<GWDump> gw_db = realm.where(GWDump.class).findAll();
    final RealmResults<ID> group_id_db = realm.where(ID.class).equalTo(DatabaseConfig.TYPE, DatabaseConfig.GROUP).findAll().sort(DatabaseConfig.TIME);
    final RealmResults<ID> phone_id_db = realm.where(ID.class).equalTo(DatabaseConfig.TYPE, DatabaseConfig.PHONE).findAll().sort(DatabaseConfig.TIME);

    private Date last_time;
    SimpleDateFormat date_format = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");

    private String phone_id;
    private String group_id;

    private boolean isRooted;

    SharedPreferences settings;

    @Bind(R.id.version_number)
    TextView version_number;

    @Bind(R.id.status)
    TextView status;
    @Bind(R.id.root_status)
    TextView root_status;

    @Bind(R.id.num_packets)
    TextView num_packets;
    @Bind(R.id.seconds_since_last)
    TextView seconds_since_last;
    @Bind(R.id.total_data)
    TextView total_data;
    @Bind(R.id.num_gw)
    TextView num_gw;



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
    @Bind(R.id.graph)
    Button graph_btn;

    BroadcastReceiver receiver;

    private Handler mHandler;

    private int cur_wit_size;
    private int cur_gw_size;
    private boolean isConnected;
    private String macAddress;

    @OnClick(R.id.activity_wit_energy_bluetooth)
    public void onBackgroundClick() {
        View r = findViewById(R.id.activity_wit_energy_bluetooth);
        InputMethodManager imm = (InputMethodManager) r.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(r.getWindowToken(), 0);
    }


    @OnClick(R.id.graph)
    public void onGraphClick() {
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    WitEnergyBluetoothActivity.class));
        }

        setContentView(R.layout.activity_wit_energy_bluetooth);
        ButterKnife.bind(this);

        settings = getSharedPreferences(SettingsConfig.SETTINGS_META_DATA, 0);

        Intent a = new Intent(this, PlugWatchService.class);
        a.putExtra(IntentConfig.PLUGWATCHSERVICE_REQ, IntentConfig.START_SCANNING);
        startService(a);

        initUI();
        wit_db.addChangeListener(new RealmChangeListener<RealmResults<MeasurementRealm>>() {
            @Override
            public void onChange(RealmResults<MeasurementRealm> element) {
                cur_wit_size = wit_db.size();
                last_time = new Date();
            }
        });
        gw_db.addChangeListener(new RealmChangeListener<RealmResults<GWDump>>() {
            @Override
            public void onChange(RealmResults<GWDump> element) {
                cur_gw_size = wit_db.size();
            }
        });
        mHandler = new Handler(Looper.getMainLooper());
        startRepeatingTask();
    }

    private void test_network() {
        GridWatch gw = new GridWatch(getBaseContext(), SensorConfig.PLUGGED, phone_id);
        gw.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter(IntentConfig.UPDATE_UI)
        );
    }

    private void save_group_id(String group_id_to_save) {
        settings.edit().putString(SettingsConfig.GROUP_ID, group_id_to_save.substring(3)).apply();
        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm bgRealm) {
                ID new_id = new ID(DatabaseConfig.GROUP, group_id_to_save.substring(3));
                bgRealm.copyToRealm(new_id);
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {

            }
        });
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter(IntentConfig.UPDATE_UI)
        );
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        Log.e("intent", "new intent");
    }

    private void save_phone_id(String phone_id_to_save) { //TODO: persist to file
        settings.edit().putString(SettingsConfig.PHONE_ID, phone_id_to_save.substring(3)).apply();
        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm bgRealm) {
                ID new_id = new ID(DatabaseConfig.PHONE, phone_id_to_save.substring(3));
                bgRealm.copyToRealm(new_id);
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {

            }
        });
    }


    @Override
    protected void onPause() {
        //unregisterReceiver(receiver);
        super.onPause();
    }

    private void updateUI() {
        Log.e("updating ui", "attempting");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                     @Override
                                                     public void run() {
                                                         try {
                                                             int cur_wit_cnt = WitEnergyVersionTwo.getInstance().num_wit;
                                                             if (cur_wit_cnt != -1) {
                                                                 num_packets.setText(String.valueOf(cur_wit_cnt));
                                                             }
                                                             int cur_gw_cnt = WitEnergyVersionTwo.getInstance().num_gw;
                                                             if (cur_gw_cnt != -1) {
                                                                 num_gw.setText(String.valueOf(cur_gw_cnt));
                                                             }
                                                             total_data.setText(String.valueOf(WitEnergyVersionTwo.getInstance().network_data));
                                                             isConnected = WitEnergyVersionTwo.getInstance().isConnected;
                                                             status.setText(isConnected ? "Connected" : "Disconnected");
                                                             status.setTextColor(isConnected ? Color.GREEN : Color.RED);
                                                             seconds_since_last.setText(date_format.format(WitEnergyVersionTwo.getInstance().last_time));
                                                             group_id_cur.setText(group_id);
                                                             phone_id_cur.setText(phone_id);
                                                             root_status.setText(isRooted ? "Rooted" : "Not Rooted");
                                                             root_status.setTextColor(isRooted ? Color.GREEN : Color.RED);
                                                         } catch (java.lang.NullPointerException e) {
                                                             Log.e("error updating UI", "null pointer... continuing");
                                                         }

                                                     }
                                                 });
    }


    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                updateUI();
            } finally {
                mHandler.postDelayed(mStatusChecker, AppConfig.UI_UPDATE_INTERVAL);
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRepeatingTask();
    }

    void startRepeatingTask() {
        mStatusChecker.run();
    }

    void stopRepeatingTask() {
        mHandler.removeCallbacks(mStatusChecker);
    }

    private void initUI() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    group_id = String.valueOf(group_id_db.last().getID());
                    phone_id = String.valueOf(phone_id_db.last().getID());
                } catch (IndexOutOfBoundsException e) {

                }
                version_number.setText("v." + WitEnergyVersionTwo.getInstance().buildStr);
                cur_wit_size = wit_db.size();
                num_packets.setText(String.valueOf(cur_wit_size));
                cur_gw_size = gw_db.size();
                num_gw.setText(String.valueOf(cur_gw_size));
                total_data.setText(String.valueOf(WitEnergyVersionTwo.getInstance().network_data));
                status.setText(isConnected ? "Connected" : "Disconnected");
                status.setTextColor(isConnected ? Color.GREEN : Color.RED);
                group_id_cur.setText(group_id);
                phone_id_cur.setText(phone_id);
                root_status.setText(isRooted ? "Rooted" : "Not Rooted");
                root_status.setTextColor(isRooted ? Color.GREEN : Color.RED);
            }
        });
    }









}


