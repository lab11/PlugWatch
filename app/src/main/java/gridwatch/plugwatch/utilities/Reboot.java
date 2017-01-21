package gridwatch.plugwatch.utilities;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import net.grandcentrix.tray.AppPreferences;

import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.logs.RunningTimeWriter;

import static gridwatch.plugwatch.wit.App.getContext;

/**
 * Created by nklugman on 11/17/16.
 */

public class Reboot extends IntentService {

    private RunningTimeWriter runningTimeWriter;
    final AppPreferences appPreferences = new AppPreferences(getContext());


    public Reboot(String name) {
        super(name);
    }

    public Reboot() {
        super("Reboot");
    }


    public void do_reboot() {
        Log.e("reboot", "doing reboot");

            try {
                runningTimeWriter = new RunningTimeWriter(getClass().getName());
                runningTimeWriter.log(String.valueOf(System.currentTimeMillis()), appPreferences.getString(SettingsConfig.TIME_RUNNING));

                Process proc = Runtime.getRuntime()
                        .exec(new String[]{"su", "-c", "reboot"});
                proc.waitFor();
                Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", "reboot"});
            } catch (Exception ex) {
                ex.printStackTrace();
                if (ex.getCause().getMessage().toString().equals("Permission denied")) {
                    Log.e("reboot", "permission denied");
                }

            }

    }


    @Override
    protected void onHandleIntent(Intent intent) {
        Log.e("reboot", "hit");
        Log.e("reboot", "time is: " + String.valueOf(System.currentTimeMillis()));
        do_reboot();

    }
}


