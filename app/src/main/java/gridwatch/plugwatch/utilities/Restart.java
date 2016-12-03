package gridwatch.plugwatch.utilities;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Process;

import java.io.PrintWriter;
import java.io.StringWriter;

import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.logs.RebootCauseWriter;
import gridwatch.plugwatch.wit.PlugWatchService;

/**
 * Created by nklugman on 11/22/16.
 */

public class Restart {

    private Handler handler = new Handler(); //this is fine for UI

    int cnt = 0;

    private Context mContext;
    private String calling_class_name;

    public Restart() {

    }

    public void do_restart(Context context, Class<?> c, String guilty_class, Throwable exception, int pid) {
        //send_dead_packet();
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        System.err.println(stackTrace);// You can use LogCat too
        if (context != null && c != null) {
            mContext = context;


            send_dead_packet();

            try {
                RebootCauseWriter r = new RebootCauseWriter(mContext);
                r.log(String.valueOf(System.currentTimeMillis()), exception.getMessage(), "restart");
            } catch (Exception e) {
                e.printStackTrace();
            }

            Intent intent = new Intent(context, c);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            if (pid != -1 ) {
                Process.killProcess(pid);
            }
            Process.killProcess(Process.myPid());
            System.exit(0);

        } else { //something went wrong
            Rebooter r = new Rebooter(context, calling_class_name, new Throwable(this.getClass().getName() + ": something went wrong... rebooting from restart"));
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
