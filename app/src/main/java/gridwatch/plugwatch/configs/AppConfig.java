package gridwatch.plugwatch.configs;

/**
 * Created by nklugman on 11/18/16.
 */

public class AppConfig {

    public final static boolean DEBUG = true;
    public final static boolean RESTART_ON_EXCEPTION = true;
    public final static boolean TURN_REBOOT_OFF = false;

    public final static int UI_UPDATE_INTERVAL = 5000;
    public final static int ACTIVITY_GOOD = 1;
    public static final int ACTIVITY_PAUSED = 2;
    public static final int ACTIVITY_STOPPED = 3;
}
