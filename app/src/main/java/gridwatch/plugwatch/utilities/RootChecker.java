package gridwatch.plugwatch.utilities;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by nklugman on 11/18/16.
 */

public class RootChecker {

    Context mContext;

    public RootChecker(Context context) {
        mContext = context;
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
            if (ex.getCause().getMessage().toString().equals("Permission denied")) {
                Toast.makeText(mContext, ex.getCause().getMessage().toString(), Toast.LENGTH_SHORT);
            }
            return false;

        }
    }

}
