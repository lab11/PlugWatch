package gridwatch.plugwatch.firebase;

import android.content.Context;

import com.google.firebase.crash.FirebaseCrash;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import gridwatch.plugwatch.configs.SettingsConfig;

import static gridwatch.plugwatch.wit.App.getContext;

/**
 * Created by nklugman on 11/27/16.
 */

public class FirebaseCrashLogger {

    private String phone_id;
    private String group_id;
    final AppPreferences appPreferences = new AppPreferences(getContext());

    public FirebaseCrashLogger(Context context, String msg) {
        try {
            phone_id = appPreferences.getString(SettingsConfig.PHONE_ID);
            group_id = appPreferences.getString(SettingsConfig.GROUP_ID);
        } catch (ItemNotFoundException e) {
            e.printStackTrace();
        }

            FirebaseCrash.log(phone_id + "," + group_id + "," + msg);

    }
}
