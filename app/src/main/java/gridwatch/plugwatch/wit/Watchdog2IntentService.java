package gridwatch.plugwatch.wit;

import android.app.ActivityManager;
import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import java.util.List;

import gridwatch.plugwatch.utilities.Restart;

/**
 * Created by nklugman on 12/6/16.
 */

public class Watchdog2IntentService extends IntentService {

    public Watchdog2IntentService() {
        super("Watchdog2IntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            int cnt = 0;
            final List<ActivityManager.RunningAppProcessInfo> runningProcesses = manager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo process: runningProcesses) {
                //Log.e(getClass().getName(), process.processName);
                if (process.processName.contains(".PlugWatch") ||
                        process.processName.contains("gridwatch.wit")) {
                    cnt++;
                }
            }
            Log.e("wd 2 reboot cnt:", String.valueOf(cnt));
            if (cnt == 1) {
                Restart r = new Restart();
                r.do_restart(getApplicationContext(), PlugWatchUIActivity.class, getClass().getName(), new Throwable("watchdog2 rebooting due to dead process"), -1);
            } else {
                Log.e("watchdog2", "all is well!");
            }
        } catch (java.lang.NullPointerException e) {
            //FirebaseCrashLogger a = new FirebaseCrashLogger(getApplicationContext(), e.getMessage());
            e.printStackTrace();
        } catch (Exception g) {
            g.printStackTrace();
        }
    }
}
