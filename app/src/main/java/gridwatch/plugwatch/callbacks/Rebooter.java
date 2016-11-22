package gridwatch.plugwatch.callbacks;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import gridwatch.plugwatch.utilities.Reboot;

import static android.content.Intent.ACTION_REBOOT;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class Rebooter extends IntentService {

    public Rebooter() {
        super("Rebooter");
    }

    public static void doReboot(Context context, String param1) {
        Intent intent = new Intent(context, Rebooter.class);
        intent.setAction(ACTION_REBOOT);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_REBOOT.equals(action)) {
                reboot_now();
            }
        }
    }

    private void reboot_now() {
        Reboot rebooter = new Reboot();
        rebooter.reboot();
    }


}
