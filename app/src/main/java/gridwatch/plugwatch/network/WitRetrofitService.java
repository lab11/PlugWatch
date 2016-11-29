package gridwatch.plugwatch.network;

import com.evernote.android.job.util.support.PersistableBundleCompat;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Created by nklugman on 11/18/16.
 */


public interface WitRetrofitService {
    @POST("./")
    Call<String> createMeasurement(@Body WitRetrofit witRetrofit);

    @POST("./")
    Call<WitRetrofit> createMeasurement(@Body PersistableBundleCompat witRetrofit);

    @POST("/ack/")
    Call<String> sendAck(@Body AckRetrofit ackRetrofit);

    @POST("/settings/")
    Call<String> sendSetting(@Body SettingsRetrofit settingsRetrofit);

    @POST("/crash/")
    Call<String> sendCrash(@Body CrashRetrofit crashRetrofit);

    @POST("/meta/")
    Call<String> sendMeta(@Body MetaRetrofit metaRetrofit);

    @POST("/gw/")
    Call<GWRetrofit> sendGW(@Body PersistableBundleCompat gwRetrofit);

}


