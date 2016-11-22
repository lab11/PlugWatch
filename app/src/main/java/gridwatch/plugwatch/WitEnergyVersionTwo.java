package gridwatch.plugwatch;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.evernote.android.job.JobManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.internal.RxBleLog;

import java.io.File;
import java.util.Date;

import gridwatch.plugwatch.callbacks.Rebooter;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.callbacks.WatchDog;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.database.ID;
import gridwatch.plugwatch.database.Migration;
import gridwatch.plugwatch.network.WitJobCreator;
import gridwatch.plugwatch.network.WitRetrofitService;
import gridwatch.plugwatch.wit.WitEnergyBluetoothActivity;
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

public class WitEnergyVersionTwo extends Application {



    public String buildStr;

    private RxBleClient rxBleClient;
    private static WitEnergyVersionTwo instance;
    private JobManager jobManager;

    private final static int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private final static int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private final static int SAMPLE_FREQUENCY = SensorConfig.MICROPHONE_SAMPLE_FREQUENCY;
    private final static byte BIT_RATE = SensorConfig.MICROPHONE_BIT_RATE;
    private static int recBufferSize;
    public AudioRecord mRecorder;

    public int network_data = -1;
    public boolean isConnected = false;
    public int num_wit = -1;
    public int num_gw = -1;
    public Date last_time = new Date();

    public WitRetrofitService retrofitService;

    private Context baseContext;
    AlarmManager alarmMgr;

    private OkHttpClient.Builder httpClientBuilder;

    public Realm realm;
    private ID group_id;
    private ID phone_id;
    public String phone_id_cur;
    public String group_id_cur;

    public WitEnergyVersionTwo() throws PackageManager.NameNotFoundException {
        instance = this;
    }

    public static RxBleClient getRxBleClient(Context context) {
        WitEnergyVersionTwo application = (WitEnergyVersionTwo) context.getApplicationContext();
        return application.rxBleClient;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        buildStr = String.valueOf(pInfo.versionCode);

        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
                    WitEnergyBluetoothActivity.class));
        }

        rxBleClient = RxBleClient.create(this);
        RxBleClient.setLogLevel(RxBleLog.DEBUG);

        baseContext = getBaseContext();
        alarmMgr = (AlarmManager)baseContext.getSystemService(Context.ALARM_SERVICE);
        //setup_reboot_alarm();
        //setup_watchdog_alarm();

        BluetoothAdapter.getDefaultAdapter().enable();

        Interceptor a = chain -> {
            long request_size = chain.request().body().contentLength();
            Request request = chain.request();
            Response response = chain.proceed(request);
            return response;
        };
        httpClientBuilder = new OkHttpClient.Builder().addInterceptor(a);


        initAudioRecorder();

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

        setup_retrofit();

        getJobManager();
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


    public static WitEnergyVersionTwo getInstance() {
        return instance;
    }

    public void configureJobManager() {
        JobManager.create(this).addJobCreator(new WitJobCreator());
    }

    public synchronized JobManager getJobManager() {
        if (jobManager == null) {
            configureJobManager();
        }
        return jobManager;
    }



    private void setup_reboot_alarm() {
        Intent intent = new Intent(baseContext, Rebooter.class);
        intent.putExtra(IntentConfig.TYPE, IntentConfig.TYPE_ALARM);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(getBaseContext(), 283728912, intent, 0);
        alarmMgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_HOUR / 60 * 5,
                AlarmManager.INTERVAL_HOUR / 60 * 5,
                alarmIntent);
    }


    private void initAudioRecorder(){
        recBufferSize = AudioRecord.getMinBufferSize(SAMPLE_FREQUENCY,
                RECORDER_CHANNELS,
                RECORDER_ENCODING);

        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_FREQUENCY,
                RECORDER_CHANNELS,
                RECORDER_ENCODING,
                recBufferSize*2);
    }

    private void setup_watchdog_alarm() {
        Intent intent = new Intent(baseContext, WatchDog.class);
        intent.putExtra(IntentConfig.TYPE, IntentConfig.TYPE_ALARM);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(getBaseContext(), 111822923, intent, 0);
        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_DAY,
                AlarmManager.INTERVAL_DAY,
                alarmIntent);
    }


    private File openRealm() {
        File file = this.getExternalFilesDir("/db/");
        if (!file.exists()) {
            boolean result = file.mkdir();
            Log.e("TTT", "Results: " + result);
        }
        return file;

    }




    private File checkLogName() {
        File folder = new File(this.getExternalFilesDir(""),"");
        if(!folder.exists()){
            if(folder.mkdirs())
                Toast.makeText(this, "New Folder Created", Toast.LENGTH_SHORT).show();
        }
        return folder;
    }



}