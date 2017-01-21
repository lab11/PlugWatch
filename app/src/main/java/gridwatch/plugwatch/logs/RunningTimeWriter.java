package gridwatch.plugwatch.logs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

public class RunningTimeWriter {

	private final static String LOG_NAME = "pw_running_time.log";
	private static SharedPreferences prefs;

	private static File mLogFile;
	private static Context mContext;

	public RunningTimeWriter(String calling_class) {
		String secStore = System.getenv("SECONDARY_STORAGE");
		File root = new File(secStore);
		if (!root.exists()) {
			boolean result = root.mkdir();
			Log.i("TTT", "Results: " + result);
		}
		mLogFile = new File(root, LOG_NAME);
	}

	public static void log(String time, String run_time) {

		String l = time + "|" + run_time;

		try {
			FileWriter logFW = null;
			logFW = new FileWriter(mLogFile.getAbsolutePath(), true);
			logFW.write(l + "\n");
			logFW.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


	public String get_avg() {

		ArrayList<String> log = read();
		double all_time_ms = 0;
		if (log != null) {
			if (!log.isEmpty()) {
				for (int i = 0; i < log.size(); i++) {
					try {
						StringTokenizer st = new StringTokenizer(log.get(i), "|");
						String time = st.nextToken();
						String time_str = st.nextToken();
						double time_ms = Double.valueOf(time_str);
						//Log.e("GET AVG", String.valueOf(time_str));
						all_time_ms += time_ms;
					} catch (java.lang.ArrayIndexOutOfBoundsException e) {
						Log.e("RunningTimeWriter", "index out of bound with " + log.get(i));
					}
				}
			}
		}
		double avg = all_time_ms/Double.valueOf(log.size());
		Log.e("avg running time", String.valueOf(avg));

		return String.valueOf(avg);
	}

	public ArrayList<String> read() {

		FileWorkerAsyncTask task = new FileWorkerAsyncTask(mLogFile);
		try {
			return task.execute().get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return null;

	}




	private static class FileWorkerAsyncTask extends AsyncTask<Void, Void, ArrayList<String> > {

		private File mLogFile;

		public FileWorkerAsyncTask(File mLogFile) {
			this.mLogFile = mLogFile;
		}

		@Override
		protected ArrayList<String> doInBackground(Void... params) {
			ArrayList<String> ret = new ArrayList<String>(200);
			try {
				BufferedReader logBR = new BufferedReader(new InputStreamReader(new FileInputStream(mLogFile.getAbsolutePath())));

				int line_num = 0;
				String line;

				while ((line = logBR.readLine()) != null) {
					ret.add(line);
					if (line_num >= 100) {
						break;
					}
				}
				logBR.close();
			} catch (IOException e) {
				if (e.getCause().toString().contains("No such file")) {
					log(String.valueOf(System.currentTimeMillis()), "-1");
					Log.e("LOG CREATED", LOG_NAME);
					return ret;
				} else {
					// TODO Auto-generated catch block
					Log.e("file writer", e.getCause().toString());
					e.printStackTrace();
				}
			}
			return ret;
		}
	}



	
}
