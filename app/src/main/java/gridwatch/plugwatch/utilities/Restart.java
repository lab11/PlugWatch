package gridwatch.plugwatch.utilities;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import com.jakewharton.processphoenix.ProcessPhoenix;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.logs.RebootCauseWriter;
import gridwatch.plugwatch.logs.RunningTimeWriter;
import gridwatch.plugwatch.wit.App;
import gridwatch.plugwatch.wit.PlugWatchService;

import static android.content.Context.ACTIVITY_SERVICE;
import static gridwatch.plugwatch.wit.App.getContext;

/**
 * Created by nklugman on 11/22/16.
 */

public class Restart {

    private RunningTimeWriter runningTimeWriter;
    AppPreferences appPreferences;

    int cnt = 0;

    private Context mContext;
    private String calling_class_name;

    public Restart() {
        appPreferences = new AppPreferences(getContext());

    }

    //TODO something is wrong with this class... stress test before shipping
    //it doesnt seem to reboot the service 100% of the time...

    public void do_restart(Context context, Class<?> c, String guilty_class, Throwable exception, int pid) {

        runningTimeWriter = new RunningTimeWriter(getClass().getName());
        try {
            runningTimeWriter.log(String.valueOf(System.currentTimeMillis()), appPreferences.getString(SettingsConfig.TIME_RUNNING));
        } catch (ItemNotFoundException e) {
            runningTimeWriter.log(String.valueOf(System.currentTimeMillis()), "0");
            e.printStackTrace();
        }

        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        System.err.println(stackTrace);// You can use LogCat too
        if (context == null) {
            Log.e("Restart" , "context is null in do restart called by " + guilty_class);
        }
        if (c == null) {
            Log.e("Restart" , "c is null in do restart called by " + guilty_class);
        }
        if (context != null && c != null) {
            mContext = context;
            appPreferences = new AppPreferences(context);
            appPreferences.put(SettingsConfig.LAST_WIT, String.valueOf(System.currentTimeMillis()+5000));



            send_dead_packet();

            try {
                RebootCauseWriter r = new RebootCauseWriter(mContext, getClass().getName());
                r.log(String.valueOf(System.currentTimeMillis()), exception.getMessage(), "restart");
            } catch (Exception e) {
                e.printStackTrace();
            }

            ActivityManager manager = (ActivityManager) App.getInstance().getBaseContext().getSystemService(ACTIVITY_SERVICE);
            final List<ActivityManager.RunningAppProcessInfo> runningProcesses = manager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo process: runningProcesses) {
                if (process.processName.contains(".PlugWatch") ||
                        process.processName.contains(".Watchdog"))  {
                    Log.e("killing", process.processName);
                    Log.e("killing", String.valueOf(process.pid));
                    Process.killProcess(process.pid);
                }
            }






            ProcessPhoenix.triggerRebirth(App.getInstance());


            /*
            Intent intent = new Intent(context, PlugWatchUIActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    App.getInstance().getBaseContext(), 0,
                    intent, -1);
            AlarmManager mgr = (AlarmManager) App.getInstance().getBaseContext()
                    .getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 2000, pendingIntent);
            System.exit(2);
            */


            /*
            Intent intent = new Intent(context, c);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            if (pid != -1 ) {
                Process.killProcess(pid);
            }
            Process.killProcess(Process.myPid());
            System.exit(0);
            */

        } else { //something went wrong
            Rebooter r = new Rebooter(context, calling_class_name, false, new Throwable(this.getClass().getName() + ": something went wrong... rebooting from restart"));
        }
    }

    private Runnable runnable = new Runnable() {
        public void run() {


        }
    };

    private void send_dead_packet() {
        Intent a = new Intent(mContext, PlugWatchService.class);
        a.putExtra(IntentConfig.FAIL_PACKET, IntentConfig.FAIL_PACKET);
        mContext.startService(a);
    }


}
