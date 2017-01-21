package gridwatch.plugwatch.configs;

/**
 * Created by nklugman on 11/19/16.
 */

public class SensorConfig {

    public final static int ACCEL_TIME = 2;
    public final static int AUDIO_TIME = 2;

    //CONFIGURE THE FFT
    public final static Boolean LOCAL_FFT_BOOL = true;
    public final static int CENTER_FREQ = 50;
    public final static int NOTCH_SIZE = 5; // 5 hz +- center freq
    public final static Boolean FIRST_HARMONIC = false;
    public final static int NUM_FFT_HIT_CNT = 5;
    public final static int FFT_SAMPLE_TIME_MS = 10000;
    public final static Boolean FFT_ON = true;

    //CONFIGURE THE AUDIO RECORDING
    public final static int MICROPHONE_SAMPLE_TIME_MS = 3000;
    public final static int MICROPHONE_SAMPLE_FREQUENCY = 44100;
    public final static byte MICROPHONE_BIT_RATE = 16;
    public final static String recordingFileTmpName = "wit_tmp.raw";
    public final static String recordingFolder = "wit_audio";
    public final static String recordingExtension = ".wav";

    public final static String PLUGGED = "PLUGGED";
    public final static String UNPLUGGED = "UNPLUGGED";

    public final static int WATCHDOG_INTERVAL =  1000 * 60 * 60 * 12; //interval to check connection ms


    public final static int CONNECTION_INTERVAL =  15000; //interval to check connection ms
    public final static int CONNECTION_THRESHOLD = 50000; //after this number time restart the app ms
    public final static int REBOOT_THRESHOLD = 7; // after this threshold of restarts, reboot the phone
    public final static int NOTIFICATION_BUT_NO_DECODE_TIMEOUT = 1000 * 60 * 2; //state that exists when notifications are coming that can't be decoded

    public final static long RESTART_BUFFER = 5000; //when we restart we fake a new wit coming in to allow it to timeout naturally... plus a buffer
    public final static long REBOOT_BUFFER = 10000; //takes longer to reboot


    public final static int MAX_JOBS = 100;
    public static final String SMS_ENDPOINT = "12012317237";
    public static final long GRIDWATCH_INTERVAL = 1000 * 60 * 60 * 12; //*60*60*12; //12 hrs
    public static final String FALSE_GW = "FALSE_GW";
    public static final String NULL = "NULL_GW";

    public static final String WD_TYPE_DEPLOY_AUDIT = "DA";
    public static final String WD_TYPE_API = "API";
    public static final String WD_TYPE_SMSWD = "WSMS";
    public static final String WD_TYPE_WD = "WD";
    public static final long LOCATION_TIMEOUT_IN_SECONDS = 10;
    public static final long LOCATION_UPDATE_INTERVAL = 100;
    public static final long NETWORK_TIMEOUT_SECONDS = 5;
    public static final long WIFI_LOGGER_CONNECTION_INTERVAL = 1000 * 60 * 10; //10 minutes
    public static final long WATCHDOG2_INTERVAL = 1000 * 60; //1 minute
    public static final long SMS_WATCHDOG_INTERVAL = 1000 * 60 * 60 * 12;

    public static final long LOGCAT_SAMPLE_TIME_MS = 1000 * 60 * 5;
    public static final long AVERAGE_INTERVAL = 1000*30;

    public static int NUM_ANY_CRASH_BEFORE_REBOOT = 5;
    public static int MAX_NUM_REBOOT_BACKOFF = 30;
    public static int REBOOT_MIN_WAIT = 15000;

    public static int MAX_DELAY_BEFORE_REBOOT = 1000 * 60 * 10;
}

