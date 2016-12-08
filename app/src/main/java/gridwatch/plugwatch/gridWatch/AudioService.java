package gridwatch.plugwatch.gridWatch;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import gridwatch.plugwatch.configs.SensorConfig;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class AudioService extends IntentService {

    private String onHandleIntentTag = "microphoneService:onHandleIntent";
    private String runTag = "microphoneService:run";
    private String initAudioRecorderTag = "microphoneService:initAudioRecorder";
    private String startRecordingTag = "microphoneService:startRecordingTag";
    private String stopRecordingTag = "microphoneService:stopRecordingTag";
    private String constructWAVFileTag = "microphoneService:constructWAVFile";
    private String setupFilePathsTag = "microphoneService:setupFilePaths";


    private static int RECORDER_TIME = SensorConfig.MICROPHONE_SAMPLE_TIME_MS; //ms
    private final static String recordingFileTmpName = SensorConfig.recordingFileTmpName;
    private final static String recordingFolder = SensorConfig.recordingFolder;
    private final static String recordingExtension = SensorConfig.recordingExtension;
    private static int recBufferSize;
    private final static int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private final static int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private final static int SAMPLE_FREQUENCY = SensorConfig.MICROPHONE_SAMPLE_FREQUENCY;
    private final static byte BIT_RATE = SensorConfig.MICROPHONE_BIT_RATE;

    private static String time;

    private static AudioRecord mRecorder = null;
    private static ResultReceiver mResultReceiver;
    private static Context mContext;
    private static byte tmpData[];
    private static String tmpFileName;
    private static File tmpFile;
    private static File fileFolder;
    private static String recordingFileName = null;
    private static FileOutputStream os = null;

    public AudioService(String name) {
        super(name);
    }

    public AudioService() {
        super("AudioService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            mContext = getBaseContext();
            Log.d(onHandleIntentTag, "hit");
            setupFilePaths();
            recBufferSize = AudioRecord.getMinBufferSize(SAMPLE_FREQUENCY,
                    RECORDER_CHANNELS,
                    RECORDER_ENCODING);
            mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_FREQUENCY,
                    RECORDER_CHANNELS,
                    RECORDER_ENCODING,
                    recBufferSize*2);
            run();
        }
    }


    public String run() {
        Log.e("GridWatch SENSOR AUDIO", "Starting");

        // TODO should never be false... cut this off earlier. Hack
        // TODO, check for available memory before trying this
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            Log.d(runTag, "hit");

            startRecording();

            // Take RECORDER_TIME worth of data
            int read = 0;
            if (os != null) {
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    long t = System.currentTimeMillis();
                    while (System.currentTimeMillis() - t <= RECORDER_TIME) {
                        read = mRecorder.read(tmpData, 0, recBufferSize);
                        try {
                            os.write(tmpData);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            stopRecording();
            constructWAVFile();
            Log.e("GridWatch SENSOR AUDIO", "DONE");
            return time;
        }
        else {
            Log.w(runTag, "Android SDK Version Incorrect");
            return "";
        }
    }

    private void setupFilePaths() {
        String tmpFilePath = "";


        String secStore = System.getenv("SECONDARY_STORAGE");
        File f_secs = new File(secStore);
        if (!f_secs.exists()) {
            boolean result = f_secs.mkdir();
            Log.i("TTT", "Results: " + result);
        }
        tmpFilePath = f_secs.getPath();
        fileFolder = new File(tmpFilePath, recordingFolder);
        if (!fileFolder.exists()) fileFolder.mkdirs();
        tmpFile = new File(tmpFilePath, recordingFileTmpName);
        if (!tmpFile.exists()) tmpFile.delete();
        tmpFileName = fileFolder.getAbsolutePath() + "/" + recordingFileTmpName;
        tmpData = new byte[recBufferSize];
        try {
            os = new FileOutputStream(tmpFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {
        // Get that recording going
        if (mRecorder != null) {
            mRecorder.startRecording();
            Log.d(startRecordingTag, "Starting Recording");
        }
    }

    private void stopRecording(){
        // Stop recording
        if (mRecorder != null) {
            mRecorder.stop();
            Log.d(startRecordingTag, "Stopping Recording");

        }
    }

    private void constructWAVFile() {
        // Make a WAV file
        time = String.valueOf(System.currentTimeMillis());
        recordingFileName = fileFolder.getAbsolutePath() + "/" + System.currentTimeMillis() + recordingExtension;
        Log.d(constructWAVFileTag + ":recordingFileName", recordingFileName);
        Log.d(constructWAVFileTag + ":tmpFileName", tmpFileName);

        // Convert RAW to WAV
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = SAMPLE_FREQUENCY;
        int channels = 2;
        long byteRate = BIT_RATE * SAMPLE_FREQUENCY * channels/8;
        byte[] wavData = new byte[recBufferSize];

        try {
            in = new FileInputStream(tmpFileName);
            out = new FileOutputStream(recordingFileName);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            WriteWavHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(wavData) != -1){
                out.write(wavData);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(constructWAVFileTag, "Done Transfering TMP to WAV");

        // Delete the tmp file
        if (tmpFile.exists()) tmpFile.delete();
        //mThisEvent.mSixtyHzFinished = true;
    }
    private void WriteWavHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = BIT_RATE;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }
}