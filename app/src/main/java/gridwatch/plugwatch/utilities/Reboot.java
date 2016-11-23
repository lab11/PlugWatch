package gridwatch.plugwatch.utilities;

/**
 * Created by nklugman on 11/17/16.
 */

public class Reboot {


    public void Reboot() {

    }

    public void reboot() {
        do_reboot();
    }

    private void do_reboot() {
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


