package gridwatch.plugwatch.wit;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import gridwatch.plugwatch.IFailsafeTimerService;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.utilities.Rebooter;

public class FailsafeTimerService extends Service {

    private Handler handler = new Handler(Looper.getMainLooper()); //this is fine for UI
    private long last_time;
    private int pid_plugwatch_service;
    private boolean is_connected;

    public FailsafeTimerService() {
        runnable.run();
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(), getClass().getName(),
                    PlugWatchUIActivity.class));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IFailsafeTimerService.Stub mBinder = new IFailsafeTimerService.Stub() {

        @Override
        public void send_last(long last) throws RemoteException {
            last_time = last;
        }

        @Override
        public void send_pid_of_plugwatch_service(int pid) throws RemoteException {
            pid_plugwatch_service = pid;
        }

        @Override
        public void send_is_connected(boolean cur_is_connected) throws RemoteException {
            is_connected = cur_is_connected;
        }

        @Override
        public int get_pid() throws RemoteException {
            return android.os.Process.myPid();
        }
    };

    private Runnable runnable = new Runnable() {
        public void run() {
            if (!is_connected) {
                Rebooter r = new Rebooter(getApplicationContext(), getClass().getName(), false, new Throwable("rebooting due to failsafe timer"));
            }
            handler.postDelayed(this, 1000*60*30);
        }
    };




}
