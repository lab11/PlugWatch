package gridwatch.plugwatch.network;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.evernote.android.job.Job;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.io.IOException;

import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.wit.App;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by nklugman on 11/25/16.
 */

public class NetworkJob extends Job {


    public static final String TAG = "job_network_tag";
    SharedPreferences sp;


    @Override
    @NonNull
    protected Result onRunJob(Params params) {
        sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        PersistableBundleCompat extras = params.getExtras();
        App.getInstance().getRetrofitService().createMeasurement(extras).enqueue(new retrofit2.Callback<WitRetrofit>() {

            @Override
            public void onResponse(Call<WitRetrofit> call, Response<WitRetrofit> response) {
                Log.e("network response GOOD", response.body().toString());
                try {
                    long cur_network = sp.getLong(SettingsConfig.TOTAL_DATA, -1);
                    sp.edit().putLong(SettingsConfig.TOTAL_DATA, cur_network += call.request().body().contentLength()).commit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<WitRetrofit> call, Throwable t) {
                Log.e("network response FAIL", t.toString());
                try {
                    long cur_network = sp.getLong(SettingsConfig.TOTAL_DATA, -1);
                    sp.edit().putLong(SettingsConfig.TOTAL_DATA, cur_network += call.request().body().contentLength()).commit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        return Result.SUCCESS;
    }




}