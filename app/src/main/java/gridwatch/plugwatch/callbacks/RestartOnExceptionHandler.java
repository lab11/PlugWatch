package gridwatch.plugwatch.callbacks;

import android.content.Context;
import android.util.Log;

import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.firebase.FirebaseCrashLogger;
import gridwatch.plugwatch.logs.RestartNumWriter;
import gridwatch.plugwatch.utilities.Rebooter;
import gridwatch.plugwatch.utilities.Restart;
import gridwatch.plugwatch.wit.App;

/**
 * Created by nklugman on 6/24/16.
 */
public class RestartOnExceptionHandler extends Throwable implements
        Thread.UncaughtExceptionHandler {
    private final Context myContext;
    private final Class<?> myActivityClass;

    public RestartOnExceptionHandler(Context context, String calling_class, Class<?> c) {
        Log.e("restart onexceptionhandler", calling_class);
        myContext = App.getContext();
        myActivityClass = c;
    }

    public void uncaughtException(Thread thread, Throwable exception) {
        FirebaseCrashLogger a = new FirebaseCrashLogger(myContext, exception.getMessage());
        Log.e("restart uncaught_exception", exception.getMessage());
        exception.printStackTrace();

        int reboot_thresh = SensorConfig.NUM_ANY_CRASH_BEFORE_REBOOT;

        RestartNumWriter r = new RestartNumWriter(myContext, getClass().getName());
        int restart_num = Integer.parseInt(r.get_last_value());
        Log.e("restart", "restart num " + String.valueOf(restart_num) + " threshold: " + String.valueOf(reboot_thresh));

        if (restart_num + 1 > reboot_thresh) {
            r.log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));

            //CHECK FOR UPDATES... TODO
            //Rebooter reboot = new Rebooter(myContext, this.getClass().getName(), new Throwable(this.getClass().getName() + ": REBOOTING DUE TO RANDOM CRASHES"));

            Rebooter reboot = new Rebooter(App.getInstance().getApplicationContext(), this.getClass().getName(), false, new Throwable(this.getClass().getName() + ": REBOOTING DUE TO TEST"));



            //Reboot reboot = new Reboot();
            //reboot.do_reboot();
        } else {
            r.log(String.valueOf(System.currentTimeMillis()), String.valueOf(restart_num + 1));
            Restart restarter = new Restart();
            restarter.do_restart(myContext,  myActivityClass, this.getClass().getName(), exception, -1);
        }


    }
}