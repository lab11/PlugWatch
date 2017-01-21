package gridwatch.plugwatch.utilities;

import android.util.Log;

import java.util.Random;

import gridwatch.plugwatch.configs.AppConfig;

/**
 * Created by nklugman on 1/8/17.
 */

public class Crasher {

    public Crasher(String function_name) {
        if (AppConfig.RANDOM_DEATH) {
            Random a = new Random();
            int randomNum = a.nextInt((10 - 0) + 1);
            if (randomNum == 0) {
                Log.e("RANDOM CRASHER", function_name);
                String to_crash = null;
                to_crash.length();
            }
        }
    }

}
