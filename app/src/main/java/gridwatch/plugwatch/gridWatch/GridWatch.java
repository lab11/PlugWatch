package gridwatch.plugwatch.gridWatch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
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

import com.github.pwittchen.reactivenetwork.library.Connectivity;
import com.github.pwittchen.reactivenetwork.library.ReactiveNetwork;
import com.github.pwittchen.reactivesensors.library.ReactiveSensorEvent;
import com.github.pwittchen.reactivesensors.library.ReactiveSensors;
import com.google.android.gms.location.LocationRequest;
import com.orhanobut.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.GWDump;
import gridwatch.plugwatch.wit.PlugWatchUIActivity;
import io.realm.Realm;
import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func6;
import rx.schedulers.Schedulers;



public class GridWatch {
    private ReactiveSensors reactiveSensors;
    private ReactiveLocationProvider reactiveLocationProvider;
    private ReactiveNetwork reactiveNetworks;
    Observable<JSONObject> gridwatch_stream;

    private String filePath;
    SharedPreferences settings;

    private LocationRequest location_rec;

    private MediaRecorder mRecorder;


    private Context mContext;
    private String mType;
    private String mPhone_id = "-1";

    public GridWatch(Context context, String type, String phone_id) {
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(mContext,
                    PlugWatchUIActivity.class));
        }

        mContext = context;
        mType = type;
        mPhone_id = phone_id;
    }

    public void run() {
        reactiveSensors = new ReactiveSensors(mContext);
        filePath = Environment.getExternalStorageDirectory() + "/"+String.valueOf(System.currentTimeMillis())+".wav";

        location_rec = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1);

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
        Observable<JSONObject> loc = locationProvider
                .getUpdatedLocation(location_rec)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Func1<Location, JSONObject>() {
                    @Override
                    public JSONObject call(Location loc) {
                        return loc_transform(loc);
                    }
                });

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
         gridwatch_stream = Observable
                .zip(
                        loc,
                        meta,
                        settings,
                        accel,
                        cell,
                        network,
                        new Func6<JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject, JSONObject>() {
                            @Override
                            public JSONObject call(JSONObject o, JSONObject o2, JSONObject o3, JSONObject o4, JSONObject o5, JSONObject o6) {
                                take_audio_recording(); //I HATE THIS
                                return merge(o, o2, o3, o4, o5, o6);
                            }
                        });

        gridwatch_stream.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .single()
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends JSONObject>>() {
                    @Override
                    public Observable<? extends JSONObject> call(Throwable throwable) {
//                        FirebaseCrash.log("rx error" + throwable.getMessage());
                        return null;
                    }
                })
                .subscribe(new Action1<JSONObject>() {
                    @Override
                    public void call(JSONObject o) {
                        Realm realm = Realm.getDefaultInstance();
                        realm.executeTransactionAsync(new Realm.Transaction() {
                            @Override
                            public void execute(Realm bgRealm) {
                                GWDump cur = new GWDump(mPhone_id, o.toString());
                                bgRealm.copyToRealm(cur);
                            }
                        }, new Realm.Transaction.OnSuccess() {
                            @Override
                            public void onSuccess() {
                                Log.e("GridWatch", "event saved in realm");

                                //PlugWatchApp.getInstance().set_num_gw(realm.where(GWDump.class).findAll().size());

                            }
                        });
                        Logger.e(o.toString());
                    }
                });
    }

    //****************************************************************************************
    //* TRANSFORMER FUNCTION
    //****************************************************************************************
    private JSONObject network_transform(Connectivity con) {
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
            return new JSONObject()
                    .put("boot cnt", settings.getInt(SettingsConfig.BOOT_CNT, 1))
                    .put("crash cnt", settings.getInt(SettingsConfig.CRASH_CNT, 1));
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


    public static JSONObject cell_tower_transform(Context ctx){
        Log.e("cell", "hit");
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
