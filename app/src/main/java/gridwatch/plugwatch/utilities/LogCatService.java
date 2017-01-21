package gridwatch.plugwatch.utilities;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class LogcatService extends Service {

    private String startLogcatTag = "logcatService:startLogcat";
    private static Context mContext;



    public LogcatService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getBaseContext();
        run();
    }

    public String run() {
        Log.e("GridWatch LogCat", "Starting");
        Log.d(startLogcatTag, "hit");
        StringBuilder log = null;
        try {
            String[] command = { "logcat", "-t", "1000" };
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            log=new StringBuilder();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line);
            }
            Log.e("Logcat dump", log.toString());
            return log.toString();
        } catch (IOException e) {
        }
        return "-1";
    }


}