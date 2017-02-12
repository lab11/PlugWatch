package gridwatch.plugwatch.wit;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import gridwatch.plugwatch.configs.IntentConfig;

public class GridWatchStarterService extends Service {


    public GridWatchStarterService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("gw", "doing false gw");
        Context ctx = App.getContext();
        Intent a = new Intent(ctx, PlugWatchService.class);
        a.putExtra(IntentConfig.FALSE_GW, IntentConfig.FALSE_GW);
        startService(a);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
