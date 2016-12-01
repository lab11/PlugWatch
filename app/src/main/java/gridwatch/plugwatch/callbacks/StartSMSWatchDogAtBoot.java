package gridwatch.plugwatch.callbacks;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

import gridwatch.plugwatch.wit.SMSWatchdogService;

/**
 * Created by nklugman on 11/22/16.
 */

public class StartSMSWatchDogAtBoot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.e("alarm", "starting");
            Calendar cal = Calendar.getInstance();
            Intent a = new Intent(context, SMSWatchdogService.class);
            PendingIntent pintent = PendingIntent.getService(context, 0, a, 0);
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 60*1000*60*12, pintent);
        }
    }
}