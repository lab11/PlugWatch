package gridwatch.plugwatch.logs;

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
import java.util.concurrent.ExecutionException;

public class MacWriter {

	private final static String LOG_NAME = "pw_mac.log";

	private static File mLogFile;
	static SharedPreferences prefs = null;

	public MacWriter(String calling_class) {
		create_file(calling_class);
	}

	private static void create_file(String calling_class) {
		String secStore = System.getenv("SECONDARY_STORAGE");
		File root = new File(secStore);
		if (!root.exists()) {
			boolean result = root.mkdir();
			Log.i("TTT", "Results: " + result);
		}
		mLogFile = new File(root, LOG_NAME);
		//log(String.valueOf(System.currentTimeMillis()), String.valueOf(0));
	}

	public static void log(String time, String mac , String info) {
		String l = time + "|" + mac;
		if (!info.equals("")) {
			l += "|" + info;
		}
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

	public static void init() {
		try {
			FileWriter logFW = null;
			logFW = new FileWriter(mLogFile.getAbsolutePath(), true);
			logFW.write("\n");
			logFW.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public ArrayList<String> read () {
		FileWorkerAsyncTask task = new FileWorkerAsyncTask(mLogFile);
		try {
			task.execute();
			return task.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return null;
	}

	public String get_last_sticky_value() {
		//Log.e("get_last_sticky", "getting");
		ArrayList<String> log = read();
		if (!log.isEmpty()) {
			for (int i = log.size()-1; i >= 0; i--) {
				String cur = log.get(i);
				//Log.e("testing get_last_sticky", cur);
				String[] fields = cur.split("\\|");
				if (fields.length >= 2) {
					if (fields.length >= 3) {
						if (fields[2].equals("sticky")) {
							return fields[1];
						}
					}
				}
			}
		}
		return "0";
	}


	public String get_last_value () {
		ArrayList<String> log = read();
		if (log != null) {
			if (!log.isEmpty()) {
				String last = log.get(log.size() - 1);
				if (last != null) {
					String[] last_fields = last.split("\\|");
					if (last_fields.length > 1) {
						return last_fields[1];
					}
				}
			}
		}
		return "0";
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
			}  catch (IOException e) {
				if (e.getCause().toString().contains("No such file")) {
					log(String.valueOf(System.currentTimeMillis()), "start", "");
					Log.e("LOG CREATED", LOG_NAME);
					ret.add("start");
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
