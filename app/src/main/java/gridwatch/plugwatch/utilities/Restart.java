package gridwatch.plugwatch.utilities;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by nklugman on 11/22/16.
 */

public class Restart {

    public Restart() {

    }

    public void do_restart(Context context, Class<?> c, Throwable exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        System.err.println(stackTrace);// You can use LogCat too
        if (context != null && c != null) {
            Intent intent = new Intent(context, c);
            //FirebaseCrash.log(s);
            context.startActivity(intent);
            BluetoothAdapter.getDefaultAdapter().enable();
            Process.killProcess(Process.myPid());
            System.exit(0);
        } else {
//            FirebaseCrash.log("bad restart on exception handler " + exception.getMessage());
            Reboot r = new Reboot();
            r.reboot();
        }
    }

}
