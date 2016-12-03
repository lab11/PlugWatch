package gridwatch.plugwatch.network;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;

import gridwatch.plugwatch.R;
import gridwatch.plugwatch.configs.SettingsConfig;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by nklugman on 11/25/16.
 */

public class NetworkJob extends Job {

    //Network Variables
    private JobManager jobManager;
    private OkHttpClient.Builder httpClientBuilder;
    private WitRetrofitService retrofitService;
    public static final String TAG = "job_network_tag";

    SharedPreferences sp;

    @Override
    @NonNull
    protected Result onRunJob(Params params) {
        setup_retrofit();

        PersistableBundleCompat extras = params.getExtras();
        retrofitService.createMeasurement(extras).enqueue(new retrofit2.Callback<WitRetrofit>() {

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


    private void setup_retrofit() {
        sp = PreferenceManager.getDefaultSharedPreferences(getContext());


        httpClientBuilder = new OkHttpClient.Builder();

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        retrofitService = new Retrofit.Builder()
                .baseUrl(getContext().getResources().getString(R.string.BASE_URL))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .callFactory(httpClientBuilder.build())
                .build().create(WitRetrofitService.class);
    }

}