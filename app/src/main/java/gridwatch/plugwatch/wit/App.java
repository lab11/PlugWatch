package gridwatch.plugwatch.wit;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

import gridwatch.plugwatch.configs.AppConfig;

/**
 * Created by nklugman on 12/3/16.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseApp.initializeApp(getApplicationContext());
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
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


}