package gridwatch.plugwatch.gridWatch;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.location.Location;
import android.media.MediaRecorder;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.github.pwittchen.reactivenetwork.library.Connectivity;
import com.github.pwittchen.reactivenetwork.library.ReactiveNetwork;
import com.github.pwittchen.reactivesensors.library.ReactiveSensorEvent;
import com.github.pwittchen.reactivesensors.library.ReactiveSensors;
import com.google.android.gms.location.LocationRequest;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.orhanobut.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.DatabaseConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.Ack;
import gridwatch.plugwatch.database.GWDump;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.LatLngWriter;
import gridwatch.plugwatch.logs.MacWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;
import gridwatch.plugwatch.network.GWJob;
import gridwatch.plugwatch.network.GWRetrofit;
import gridwatch.plugwatch.wit.PlugWatchUIActivity;
import io.realm.Realm;
import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func7;
import rx.schedulers.Schedulers;

import static gridwatch.plugwatch.configs.SensorConfig.LOCATION_TIMEOUT_IN_SECONDS;


public class GridWatch {
    private ReactiveSensors reactiveSensors;
    private ReactiveLocationProvider reactiveLocationProvider;
    private ReactiveNetwork reactiveNetworks;
    Observable<JSONObject> gridwatch_stream;

    SharedPreferences settings;

    SharedPreferences sp;


    private ReactiveLocationProvider location_rec;

    private MediaRecorder mRecorder;

    private DatabaseReference mDatabase;


    private Context mContext;
    private String mType;
    private String mPhone_id = "-1";

    private long mLast;
    private String mSize;
    private String mCP;
    private String mVersionNum;
    private String mMAC;



    public GridWatch(Context context, String type, String phone_id, String size, String mac, String version_num, long last) {
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(mContext, getClass().getName(),
                    PlugWatchUIActivity.class));
        }

        mContext = context;
        mType = type;
        mPhone_id = phone_id;
        mSize = size;
        mMAC = mac;
        mVersionNum = version_num;
        mLast = last;
        mDatabase = FirebaseDatabase.getInstance().getReference();
        sp = PreferenceManager.getDefaultSharedPreferences(mContext);

    }

    public void run() {
        Log.e("GridWatch", "running");


        reactiveSensors = new ReactiveSensors(mContext);


        //****************************************************************************************
        //* SENSORS
        //****************************************************************************************

        //***********************
        //* Returns network state and name
        //
        //* observed by:
        //*    gridwatch stream
        //***********************
        Observable<JSONObject> network = ReactiveNetwork.observeNetworkConnectivity(mContext)
                .subscribeOn(Schedulers.io())
                .timeout(SensorConfig.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS, Observable.just((Connectivity) null), AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Func1<Connectivity, JSONObject>() {
                    @Override
                    public JSONObject call(Connectivity connectivity) {
                        return network_transform(connectivity);
                    }
                });

        //***********************
        //* Returns magnitude of 10 seconds of accel
        //
        //* observed by:
        //*    gridwatch stream
        //***********************
        Observable<JSONObject> accel = reactiveSensors.observeSensor(Sensor.TYPE_ACCELEROMETER)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .buffer(SensorConfig.ACCEL_TIME, TimeUnit.SECONDS)
                .map(new Func1<List<ReactiveSensorEvent>, JSONObject>() {
                         @Override
                         public JSONObject call(List<ReactiveSensorEvent> eventList) {
                             return accel_transform(eventList);
                         }
                     }
                );


        //***********************
        //* Returns single location point
        //
        //* observed by:
        //*    gridwatch stream
        //***********************
        ReactiveLocationProvider locationProvider = new ReactiveLocationProvider(mContext);
        locationProvider.getLastKnownLocation()
                .subscribe(new Action1<Location>() {
                    @Override
                    public void call(Location location) {
                        Log.e("GridWatch : " , location.toString());
                    }
                });

        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setExpirationDuration(TimeUnit.SECONDS.toMillis(SensorConfig.LOCATION_TIMEOUT_IN_SECONDS))
                .setInterval(SensorConfig.LOCATION_UPDATE_INTERVAL);

        Observable<JSONObject> loc = locationProvider.getUpdatedLocation(req)
                .timeout(LOCATION_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS, Observable.just((Location) null), AndroidSchedulers.mainThread())
                .first()
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Func1<Location, JSONObject>() {
                         @Override
                         public JSONObject call(Location eventList) {
                             return loc_transform(eventList);
                         }
                     }
                );;

        //***********************
        //* Get cell-phone tower information
        //
        //* observed by:
        //*    gridwatch stream
        //***********************
        Observable<JSONObject> cell = Observable.fromCallable(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                return cell_tower_transform(mContext);
            }
        });


        //***********************
        //* Returns all settings information
        //
        //* observed by:
        //*    gridwatch stream
        //***********************
        Observable<JSONObject> settings = Observable.fromCallable(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                return settings_transform();
            }
        });

        //***********************
        //* Returns all meta-data
        //
        //* observed by:
        //*    gridwatch stream
        //***********************
        Observable<JSONObject> meta = Observable.fromCallable(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                return meta_transform();
            }
        });

        Observable<JSONObject> wifi = Observable.fromCallable(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                return wifi_transform(mContext);
            }
        });


        //****************************************************************************************
        //* STREAMS
        //****************************************************************************************
        Observable<JSONObject> audio = Observable.fromCallable(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                return null;
                //return take_audio_recording();

            }
        });

        //*************************
        //* GridWatch
        //*************************
        /*
         gridwatch_stream = Observable
                .zip(
                        loc,
                        meta,
                        settings,
                        accel,
                        cell,
                        network,
                        wifi,
                        new Func7<JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject>() {
                            @Override
                            public JSONObject call(JSONObject o, JSONObject o2, JSONObject o3, JSONObject o4, JSONObject o5, JSONObject o6, JSONObject o7) {
                                take_audio_recording(); //I HATE THIS
                                Log.e("GridWatch", "subscribe");
                                return merge(o, o2, o3, o4, o5, o6, o7);
                            }
                        });
                        */

        gridwatch_stream = Observable
                .zip(
                        loc,
                        wifi,
                        meta,
                        settings,
                        accel,
                        cell,
                        network,
                        new Func7<JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject>() {
                            @Override
                            public JSONObject call(JSONObject o, JSONObject o2, JSONObject o3, JSONObject o4, JSONObject o5, JSONObject o6, JSONObject o7) {
                                take_audio_recording(); //I HATE THIS
                                Log.e("GridWatch", "subscribe");
                                return merge(o, o2, o3, o4, o5, o6, o7);
                            }
                        });

        gridwatch_stream.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .single()
                .subscribe(new Action1<JSONObject>() {
                    @Override
                    public void call(JSONObject o) {
                        Realm realm = Realm.getDefaultInstance();
                        realm.executeTransactionAsync(new Realm.Transaction() {
                            @Override
                            public void execute(Realm bgRealm) {
                                GWDump cur = new GWDump(mPhone_id, o.toString());
                                Log.e("GridWatch", cur.toString());
                                Logger.e(cur.toString());
                                try {
                                    bgRealm.copyToRealm(cur);
                                } catch (io.realm.exceptions.RealmError e) {
                                    bgRealm.close();
                                }
                            }
                        }, new Realm.Transaction.OnSuccess() {
                            @Override
                            public void onSuccess() {
                                Log.e("GridWatch", "event saved in realm");
                                send_report();
                                //PlugWatchApp.getInstance().set_num_gw(realm.where(GWDump.class).findAll().size());

                            }
                        });
                    }
                });
    }

    private String checkCP() {
        try {
            MacWriter r = new MacWriter(getClass().getName());
            String sticky = r.get_last_sticky_value();
            try {
                if (mMAC != null) {
                    if (!mMAC.equals(sticky)) {
                        return "t";
                    } else {
                        return "f";
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                return "-1";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "-1";
    }

    private void send_report() {
        try {
            PhoneIDWriter b = new PhoneIDWriter(mContext, getClass().getName());
            String phone_id = b.get_last_value();
            GroupIDWriter d = new GroupIDWriter(mContext, getClass().getName());
            String experiment_id = d.get_last_value();

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = mContext.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            double lat = 0.0;
            double lng = 0.0;
            try {
                LatLngWriter c = new LatLngWriter(getClass().getName());
                String latlng = c.get_last_value();
                lat = Double.valueOf(latlng.split(",")[0]);
                lng = Double.valueOf(latlng.split(",")[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }

            String event_type = mType;
            long time = System.currentTimeMillis();

            long last = mLast;
            String cur_size = String.valueOf(mSize);
            double battery = level / (double) scale;
            String cp = checkCP();
            String version_num = mVersionNum;

            GWRetrofit a = new GWRetrofit(event_type, time, lat, lng,
                    phone_id, experiment_id, version_num,
                    cur_size, last, battery, cp);
            Log.i("gw: network scheduling", a.toString());
            Log.i("gw: number of jobs: ", java.lang.String.valueOf(JobManager.instance().getAllJobRequests().size()));
            if (JobManager.instance().getAllJobRequests().size() > SensorConfig.MAX_JOBS) {
                Log.e("gw: network", "canceling all jobs");
                JobManager.instance().cancelAll();
            }
            int jobId = new JobRequest.Builder(GWJob.TAG)
                    .setExecutionWindow(1_000L, 20_000L)
                    .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.EXPONENTIAL)
                    .setRequiresCharging(false)
                    .setExtras(a.toBundle())
                    .setRequiresDeviceIdle(false)
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                    .setPersisted(true)
                    .build()
                    .schedule();

            Ack gw = new Ack(System.currentTimeMillis(), a.toString(), phone_id, experiment_id);
            int new_gw_num = sp.getInt(SettingsConfig.GW, 0) + 1;
            sp.edit().putInt(SettingsConfig.GW, new_gw_num).commit();
            mDatabase.child(phone_id).child(DatabaseConfig.GW).child(String.valueOf(new_gw_num)).setValue(gw);


        } catch (Exception e) {
            e.printStackTrace();
            FirebaseCrash.log(e.getMessage());

        }
    }

    //****************************************************************************************
    //* TRANSFORMER FUNCTION
    //****************************************************************************************
    private JSONObject network_transform(Connectivity con) {
        if (con == null) {
            try {
                return new JSONObject().put("state", "unknown").put("name", "unknown");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            return new JSONObject()
                    .put("state", con.getState().name())
                    .put("name", con.getName());
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject loc_transform(Location location) {
        LatLngWriter r = new LatLngWriter(getClass().getName());
        if (location == null) {
            try {
                return new JSONObject().put("lat", "-1").put("lng", "-1");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.e("GridWatch ", location.toString());
        r.log(String.valueOf(System.currentTimeMillis()), String.valueOf(location.getLatitude())+","+String.valueOf(location.getLongitude()));
        try {
            return new JSONObject()
                    .put("lat", String.valueOf(location.getLatitude()))
                    .put("lng", String.valueOf(location.getLongitude()))
                    .put("acc", String.valueOf(location.getAccuracy()))
                    .put("alt", String.valueOf(location.getAltitude()))
                    .put("time", String.valueOf(location.getElapsedRealtimeNanos()))
                    .put("speed", String.valueOf(location.getSpeed()))
                    .put("provider", String.valueOf(location.getProvider()));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject accel_transform(List<ReactiveSensorEvent> eventList) {
        Double mag = 0.0;
        for (int i = 0; i < eventList.size(); i++) {
            SensorEvent event = eventList.get(i).getSensorEvent();
            if (event != null) {
                float x = Math.abs(event.values[0]);
                float y = Math.abs(event.values[1]);
                float z = Math.abs(event.values[2]);
                mag += Math.sqrt(x * x + y * y + z * z);
            }
        }
        try {
            Log.e("GridWatch accel:", String.valueOf(mag));
            return new JSONObject()
                    .put("accel_mag", String.valueOf(mag));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    };

    //home address
    //utility name
    //account num
    //meter num
    //public data
    private JSONObject settings_transform() {
        settings = mContext.getSharedPreferences(SettingsConfig.SETTINGS_META_DATA, 0);
        try {
            Log.e("GridWatch settings", "hit");
            return new JSONObject()
                    .put("boot cnt", settings.getInt(SettingsConfig.BOOT_CNT, 1));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }    }

    //phone id
    //is plugged
    //os type
    //os version
    //app version
    //event type
    //time
    //notes
    private JSONObject meta_transform() {
        try {
            return new JSONObject()
                    .put("type", mType)
                    .put("phone_id", mPhone_id)
                    .put("meta_time", System.currentTimeMillis());
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    };

    public void take_audio_recording() {
        Intent audio = new Intent(mContext, AudioService.class);
        mContext.startService(audio);
    }

    public static JSONObject wifi_transform(Context ctx) {
        try {
            WifiManager mWifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            JSONObject to_ret = new JSONObject();
            if (mWifi.isWifiEnabled() != false) {
                List<ScanResult> results = mWifi.getScanResults();
                Log.e("GridWatch ssids", results.toString());
                for (int i = 0; i < results.size(); i++) {
                    ScanResult a = results.get(i);
                    Log.e("SSID", a.SSID);
                    Log.e("BSID", a.BSSID);
                    Log.e("LEVEL", String.valueOf(a.level));
                    to_ret.put(a.SSID, a.BSSID + "," + String.valueOf(a.level) + "," + String.valueOf(a.timestamp));
                }
                return to_ret;
            } else {
               return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static JSONObject cell_tower_transform(Context ctx){
        Log.e("GridWatch cell", "hit");
        TelephonyManager tel = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        JSONObject cellList = new JSONObject();
        int phoneTypeInt = tel.getPhoneType();
        String phoneType = null;
        phoneType = phoneTypeInt == TelephonyManager.PHONE_TYPE_GSM ? "gsm" : phoneType;
        phoneType = phoneTypeInt == TelephonyManager.PHONE_TYPE_CDMA ? "cdma" : phoneType;
        Log.e("phone type", phoneType);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            List<NeighboringCellInfo> neighCells = tel.getNeighboringCellInfo();

            Log.e("neighCells Size", String.valueOf(neighCells.size()));
            for (int i = 0; i < neighCells.size(); i++) {
                try {
                    JSONObject cellObj = new JSONObject();
                    NeighboringCellInfo thisCell = neighCells.get(i);
                    cellObj.put("cellId", thisCell.getCid());
                    cellObj.put("lac", thisCell.getLac());
                    cellObj.put("rssi", thisCell.getRssi());
                    merge(cellList, cellObj);
                } catch (Exception e) {
                    Log.e("GRIDWATCH","cell tower exception " + e.getMessage());
                }
            }
        } else {
            List<CellInfo> infos = tel.getAllCellInfo();
            Log.e("cell", "M");
            for (int i = 0; i<infos.size(); ++i) {
                try {
                    JSONObject cellObj = new JSONObject();
                    CellInfo info = infos.get(i);
                    if (info instanceof CellInfoGsm){
                        CellSignalStrengthGsm gsm = ((CellInfoGsm) info).getCellSignalStrength();
                        CellIdentityGsm identityGsm = ((CellInfoGsm) info).getCellIdentity();
                        cellObj.put("cellId", identityGsm.getCid());
                        cellObj.put("lac", identityGsm.getLac());
                        cellObj.put("dbm", gsm.getDbm());
                        merge(cellList, cellObj);
                    } else if (info instanceof CellInfoLte) {
                        CellSignalStrengthLte lte = ((CellInfoLte) info).getCellSignalStrength();
                        CellIdentityLte identityLte = ((CellInfoLte) info).getCellIdentity();
                        cellObj.put("cellId", identityLte.getCi());
                        cellObj.put("tac", identityLte.getTac());
                        cellObj.put("dbm", lte.getDbm());
                        merge(cellList, cellObj);
                    }
                } catch (Exception ex) {
                    Log.e("GRIDWATCH","cell tower exception " + ex.getMessage());
                }
            }
        }
        return cellList;
    }


    //****************************************************************************************
    //* HELPER FUNCTIONS
    //****************************************************************************************
    private static JSONObject merge(JSONObject... jsonObjects) {
        JSONObject jsonObject = new JSONObject();
        for(JSONObject temp : jsonObjects){
            if (temp != null) {
                Iterator<String> keys = temp.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        jsonObject.put(key, temp.get(key));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return jsonObject;
    }

}
