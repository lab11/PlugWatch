package gridwatch.plugwatch.utilities;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by nklugman on 11/17/16.
 */

public class Reboot {


    public void Reboot() {

    }

    public void do_reboot(Throwable exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        System.err.println(stackTrace);// You can use LogCat too
        try {
            Process proc = Runtime.getRuntime()
                    .exec(new String[]{"su", "-c", "reboot"});
            proc.waitFor();
            Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", "reboot now"});
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}


