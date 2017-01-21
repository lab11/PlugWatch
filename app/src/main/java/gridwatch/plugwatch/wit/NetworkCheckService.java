package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.stealthcopter.networktools.Ping;
import com.stealthcopter.networktools.ping.PingResult;

import java.net.UnknownHostException;

import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.utilities.Rebooter;

/**
 * Created by nklugman on 11/27/16.
 */

public class NetworkCheckService extends IntentService {


    public NetworkCheckService() {
        super("NetworkCheckService");
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(), getClass().getName(),
                    PlugWatchUIActivity.class));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            Ping.onAddress("8.8.8.8").setTimeOutMillis(1000).setTimes(5).doPing(new Ping.PingListener() {
                @Override
                public void onResult(PingResult pingResult) {
                    if (!pingResult.isReachable) {
                        Rebooter r = new Rebooter(getApplicationContext(),getClass().getName(), false, new Throwable("rebooting due to network not reachable"));
                    }
                }

                @Override
                public void onFinished() {

                }
            });
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }





}
