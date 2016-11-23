package gridwatch.plugwatch.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class PhoneIDWriter {

	private final static String LOG_NAME = "witenergy_ID.log";
	private SharedPreferences prefs;

	private File mLogFile;
	private Context mContext;

	public PhoneIDWriter(Context context) {
		File root = Environment.getExternalStorageDirectory();
		mLogFile = new File(root, LOG_NAME);
		mContext = context;
		if (context != null) {
			prefs = PreferenceManager.getDefaultSharedPreferences(context);
		} else {
			Log.e("context gw_id", "null");
		}
	}

	public void log (String time, String event_type, String info) {
		String l = time + "|" + event_type;
		if (info != null) {
			l += "|" + info;
		}
		prefs.edit().putString("id", l).apply();

		/*
		try {
			FileWriter logFW = null;
			logFW = new FileWriter(mLogFile.getAbsolutePath(), true);
			logFW.write(l + "\n");
			logFW.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
	}

	/*
	public ArrayList<String> read () {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ret;

	}

	public String get_last_value () {
		//TODO make async
		ArrayList<String> log = read();
		if (!log.isEmpty()) {
			String last = log.get(log.size() - 1);
			if (last != null) {
				String[] last_fields = last.split("\\|");
				if (last_fields.length > 1) {
					return last_fields[1];
				}
			}
		}
		return "-1"; 
	}
	*/
	/*
	public ArrayList<String> read() {
		return new ArrayList<String>(200);

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
	*/

	public String get_last_value () {

		/*
		ArrayList<String> log = read();
		if (!log.isEmpty()) {
			String last = log.get(log.size() - 1);
			if (last != null) {
				String[] last_fields = last.split("\\|");
				if (last_fields.length > 1) {
					return last_fields[1];
				}
			}
		}
		return "-1";
		*/
		String[] last_fields = prefs.getString("id", "-1").split("\\|");
		if (last_fields.length > 1) {
			return last_fields[1];
		} else return "-1";
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return ret;
		}
	}

	
}
