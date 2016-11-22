package gridwatch.plugwatch.network;

import android.support.annotation.NonNull;
import android.util.Log;

import com.evernote.android.job.Job;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.io.IOException;

import gridwatch.plugwatch.WitEnergyVersionTwo;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by nklugman on 11/18/16.
 */

public class WitJob extends Job {

    public static final String TAG = "wit_job";

    @Override
    @NonNull
    protected Result onRunJob(Params params) {
        Log.e("job", "running!");
        final WitRetrofitService retrofitService = WitEnergyVersionTwo.getInstance().retrofitService;

        //WitEnergyVersionTwo.getInstance().getBaseContext()

        PersistableBundleCompat extras = params.getExtras();
        retrofitService.createMeasurement(extras).enqueue(new retrofit2.Callback<WitRetrofit>() {

            @Override
            public void onResponse(Call<WitRetrofit> call, Response<WitRetrofit> response) {
                Log.e("network response GOOD", response.body().toString());
                try {
                    WitEnergyVersionTwo.getInstance().network_data += call.request().body().contentLength();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<WitRetrofit> call, Throwable t) {
                Log.e("network response FAIL", t.toString());
                try {
                    WitEnergyVersionTwo.getInstance().network_data += call.request().body().contentLength();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        return Result.SUCCESS;
    }


}