package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Intent;
import android.os.IBinder;

public class WatchdogService extends IntentService {


    public WatchdogService() {
        super("WatchdogService");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }
}
