package gridwatch.plugwatch;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.internal.RxBleLog;

import java.io.File;

import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.ID;
import gridwatch.plugwatch.database.Migration;
import gridwatch.plugwatch.network.WitJobCreator;
import gridwatch.plugwatch.network.WitRetrofitService;
import gridwatch.plugwatch.wit.PlugWatchUIActivity;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by nklugman on 11/1/16.
 */

public class PlugWatchApp extends Application {

    public String buildStr;
    private RxBleClient rxBleClient;
    private static PlugWatchApp instance;
    private JobManager jobManager;

    private final static int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private final static int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private final static int SAMPLE_FREQUENCY = SensorConfig.MICROPHONE_SAMPLE_FREQUENCY;
    private final static byte BIT_RATE = SensorConfig.MICROPHONE_BIT_RATE;
    private static int recBufferSize;
    public AudioRecord mRecorder;

    private static int network_data = -1;
    private static boolean isConnected = false;
    private static int num_wit = -1;
    private static int num_gw = -1;
    private static long last_time = System.currentTimeMillis();
    private static Realm realm;
    private static ID group_id;
    private static ID phone_id;
    private WitRetrofitService retrofitService;
    private OkHttpClient.Builder httpClientBuilder;
    private String phone_id_cur;
    private String group_id_cur;

    public PlugWatchApp() throws PackageManager.NameNotFoundException {
        instance = this;
    }

    public static RxBleClient getRxBleClient(Context context) {
        PlugWatchApp application = (PlugWatchApp) context.getApplicationContext();
        return application.rxBleClient;
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

    private void setup_realm() {
        File f = openRealm();
        Log.e("FILENAME", f.getAbsolutePath().toString() + "/realm_"+Settings.Secure.getString(getBaseContext().getContentResolver(),
                Settings.Secure.ANDROID_ID));
        RealmConfiguration realmConfig = new RealmConfiguration.Builder(f)
                .name("realm_"+Settings.Secure.getString(getBaseContext().getContentResolver(),
                        Settings.Secure.ANDROID_ID))
                .migration(new Migration())
                .schemaVersion(5)
                .build();
        Realm.setDefaultConfiguration(realmConfig);
        Log.e("realm file", f.getAbsolutePath());
        realm = Realm.getDefaultInstance();
        realm.setAutoRefresh(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    PlugWatchUIActivity.class));
        }

        setup_settings();
        setup_build_str();
        setup_bluetooth();
        setup_network();
        setup_audio_recorder();
        setup_realm(); //database
        setup_retrofit();
        setup_job_manager(); //for all jobs
    }

    private void setup_settings() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putInt(SettingsConfig.NUM_CONNECTION_REBOOTS, 0).commit();
    }

    private void setup_bluetooth() {
        BluetoothAdapter.getDefaultAdapter().enable();
        rxBleClient = RxBleClient.create(this);
        RxBleClient.setLogLevel(RxBleLog.DEBUG);
    }

    private void setup_network() {
        Interceptor b = chain -> {
            long request_size = chain.request().body().contentLength();
            Request request = chain.request();
            Response response = chain.proceed(request);
            return response;
        };
        httpClientBuilder = new OkHttpClient.Builder().addInterceptor(b);
    }

    private void setup_retrofit() {
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        retrofitService = new Retrofit.Builder()
                .baseUrl(getResources().getString(R.string.BASE_URL))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .callFactory(httpClientBuilder.build())
                .build().create(WitRetrofitService.class);
    }


    public static PlugWatchApp getInstance() {
        return instance;
    }

    public void configureJobManager() {
        JobManager.create(this).addJobCreator(new WitJobCreator());
    }

    public synchronized JobManager setup_job_manager() {
        if (jobManager == null) {
            configureJobManager();
        }
        return jobManager;
    }

    private void setup_audio_recorder(){
        recBufferSize = AudioRecord.getMinBufferSize(SAMPLE_FREQUENCY,
                RECORDER_CHANNELS,
                RECORDER_ENCODING);

        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_FREQUENCY,
                RECORDER_CHANNELS,
                RECORDER_ENCODING,
                recBufferSize*2);
    }

    private File openRealm() {
        File file = this.getExternalFilesDir("/db/");
        if (!file.exists()) {
            boolean result = file.mkdir();
            Log.e("TTT", "Results: " + result);
        }
        return file;
    }

    //#####################################
    //# Getters and Setters
    //#####################################
    public void set_network_data(int nd) {
        network_data = nd;
    }

    public int get_network_data() {
        return network_data;
    }

    public void set_num_gw(int nd) {
        num_gw = nd;
    }

    public int get_num_gw() {
        return num_gw;
    }

    public void set_num_wit(int nd) {
        num_wit = nd;
    }

    public int get_num_wit() {
        return num_wit;
    }

    public void set_phone_id(ID id) {
        phone_id = id;
    }

    public ID get_phone_id() {
        return phone_id;
    }

    public void set_group_id(ID id) {
        group_id = id;
    }

    public ID get_group_id() {
        return group_id;
    }

    public void set_is_connected(boolean a) {
        isConnected = a;
    }

    public boolean get_is_connected() {
        return isConnected;
    }

    public void set_date(long e) {
        last_time = e;
    }

    public long get_last_time() {
        return last_time;
    }

    public WitRetrofitService get_retrofit_service() {
        return retrofitService;
    }

}