package gridwatch.plugwatch.callbacks;

import android.content.Context;
import android.util.Log;

import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.firebase.FirebaseCrashLogger;
import gridwatch.plugwatch.logs.RebootBackOffNumWriter;
import gridwatch.plugwatch.logs.RestartNumWriter;
import gridwatch.plugwatch.utilities.Rebooter;
import gridwatch.plugwatch.utilities.Restart;

/**
 * Created by nklugman on 6/24/16.
 */
public class RestartOnExceptionHandler extends Throwable implements
        Thread.UncaughtExceptionHandler {
    private final Context myContext;
    private final Class<?> myActivityClass;

    public RestartOnExceptionHandler(Context context, Class<?> c) {
        myContext = context;
        myActivityClass = c;
    }

    public void uncaughtException(Thread thread, Throwable exception) {
        FirebaseCrashLogger a = new FirebaseCrashLogger(myContext, exception.getMessage());

        RebootBackOffNumWriter reboot_backoff = new RebootBackOffNumWriter(myContext);
        int reboot_backoff_num = Integer.valueOf(reboot_backoff.get_last_value());
        int new_reboot_backoff_num = reboot_backoff_num + 1;
        int reboot_thresh = new_reboot_backoff_num * SensorConfig.NUM_ANY_CRASH_BEFORE_REBOOT;
        reboot_backoff.log(String.valueOf(System.currentTimeMillis()), String.valueOf(new_reboot_backoff_num));

        RestartNumWriter r = new RestartNumWriter(myContext);
        int restart_num = Integer.valueOf(r.get_last_value());
        Log.e("restart", "restart num " + String.valueOf(restart_num) + " threshold: " + String.valueOf(reboot_thresh));

        if (restart_num + 1 > reboot_thresh) {
            r.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));

            //CHECK FOR UPDATES... TODO
            Rebooter reboot = new Rebooter(myContext, this.getClass().getName(), new Throwable(this.getClass().getName() + ": REBOOTING DUE TO RANDOM CRASHES"));
        } else {
            r.log(String.valueOf(System.currentTimeMillis()), String.valueOf(restart_num + 1));
            Restart restarter = new Restart();
            restarter.do_restart(myContext,  myActivityClass, this.getClass().getName(), exception, -1);
        }


    }
}