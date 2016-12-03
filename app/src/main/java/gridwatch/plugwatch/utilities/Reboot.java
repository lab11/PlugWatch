package gridwatch.plugwatch.utilities;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import gridwatch.plugwatch.configs.AppConfig;

/**
 * Created by nklugman on 11/17/16.
 */

public class Reboot extends IntentService {

    public Reboot(String name) {
        super(name);
    }

    public Reboot() {
        super("Reboot");
    }


    public void do_reboot() {
        Log.e("restart", "doing reboot");

        if (!AppConfig.TURN_REBOOT_OFF) {
            try {
                Process proc = Runtime.getRuntime()
                        .exec(new String[]{"su", "-c", "reboot"});
                proc.waitFor();
                Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", "reboot"});
            } catch (Exception ex) {
                ex.printStackTrace();
                if (ex.getCause().getMessage().toString().equals("Permission denied")) {

                }

            }
        }
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        Log.e("reboot", "time is: " + String.valueOf(System.currentTimeMillis()));
        do_reboot();

    }
}


