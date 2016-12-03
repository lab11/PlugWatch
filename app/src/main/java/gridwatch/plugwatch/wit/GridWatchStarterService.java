package gridwatch.plugwatch.wit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.Random;

import gridwatch.plugwatch.configs.IntentConfig;

public class GridWatchStarterService extends Service {


    public GridWatchStarterService() {
        Random r = new Random();
        int gw_select = r.nextInt(10);
        if (gw_select == 1) {
            Log.e("GridWatch", "doing random sample");
            Intent a = new Intent(this, PlugWatchService.class);
            a.putExtra(IntentConfig.FALSE_GW, IntentConfig.FALSE_GW);
            sendBroadcast(a);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
