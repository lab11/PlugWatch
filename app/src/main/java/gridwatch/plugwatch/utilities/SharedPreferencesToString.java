package gridwatch.plugwatch.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;

/**
 * Created by nklugman on 12/5/16.
 */

public class SharedPreferencesToString {

    private Context mContext;

    public SharedPreferencesToString(Context context) {
        mContext = context;


    }

    public String getString() {
        String all_pref = "";
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        Map<String, ?> allEntries = sp.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            Log.d("map values", entry.getKey() + ": " + entry.getValue().toString());
            all_pref += entry.getKey() + ": " + entry.getValue().toString() + " ";
        }
        return all_pref;
    }

}
