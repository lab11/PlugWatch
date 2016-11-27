package gridwatch.plugwatch.firebase;

import android.content.Context;

import com.google.firebase.crash.FirebaseCrash;

import gridwatch.plugwatch.utilities.GroupIDWriter;
import gridwatch.plugwatch.utilities.PhoneIDWriter;

/**
 * Created by nklugman on 11/27/16.
 */

public class FirebaseCrashLogger {

    private String phone_id;
    private String group_id;

    public FirebaseCrashLogger(Context context, String msg) {
        PhoneIDWriter a = new PhoneIDWriter(context);
        phone_id = a.get_last_value();
        GroupIDWriter b = new GroupIDWriter(context);
        group_id = b.get_last_value();
        FirebaseCrash.log(phone_id+","+group_id+","+msg);
    }
}
