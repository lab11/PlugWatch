package gridwatch.plugwatch.utilities;

/**
 * Created by nklugman on 11/18/16.
 */

public class RootChecker {

    public void RootChecker() {

    }

    public boolean isRoot() {
        try {
            Process proc = Runtime.getRuntime()
                    .exec(new String[]{"su", "-c", "ls"});
            proc.waitFor();
          //  Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", "ls"});
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

}
