package gridwatch.plugwatch.wit;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gridwatch.plugwatch.R;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.network.WitRetrofitService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by nklugman on 12/3/16.
 */

public class App extends Application {
    private static App instance;

    //Network Variables
    private static JobManager jobManager;
    private static OkHttpClient.Builder httpClientBuilder;
    private static WitRetrofitService retrofitService;
    SharedPreferences sp;


    public WitRetrofitService getRetrofitService() {
        return retrofitService;
    }



    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseApp.initializeApp(getApplicationContext());
            FirebaseDatabase.getInstance();
            mContext = getApplicationContext();
            instance = this;
            setup_retrofit();
        } catch (Exception e) {
            Log.e("critical", "need to fix google play");
        }
    }

    public static int isActivityVisible() {
        return activityVisible;
    }

    public static void activityResumed() {
        activityVisible = AppConfig.ACTIVITY_GOOD;
        Log.e("app", "ACTIVITY GOOD");
    }

    public static void activityPaused() {
        activityVisible = AppConfig.ACTIVITY_PAUSED;
        Log.e("app", "ACTIVITY PAUSED");

    }

    public static void activityStopped() {
        activityVisible = AppConfig.ACTIVITY_STOPPED;
        Log.e("app", "ACTIVITY STOPPED");
    }

    private static int activityVisible = AppConfig.ACTIVITY_GOOD;


    private static Context mContext;

    public static App getInstance() {
        return instance;
    }

    public static Context getContext() {
        //  return instance.getApplicationContext();
        return mContext;
    }



    private void setup_retrofit() {
        sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        httpClientBuilder = new OkHttpClient.Builder();

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        try {
            retrofitService = new Retrofit.Builder()
                    .baseUrl(getContext().getResources().getString(R.string.BASE_URL))
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .callFactory(httpClientBuilder.build())
                    .build().create(WitRetrofitService.class);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e("NetworkJob", "out of memory"); //TODO this is really stupid... shouldn't be creating new retrofit service each time

        }
    }


}