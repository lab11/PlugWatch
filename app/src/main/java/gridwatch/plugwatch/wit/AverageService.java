package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import gridwatch.plugwatch.database.MeasurementRealm;
import gridwatch.plugwatch.logs.AvgWitWriter;
import io.realm.Realm;


public class AverageService extends IntentService {


    public AverageService() {
        super("AverageMinuteService");
    }



    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            try {

                Realm realm = Realm.getDefaultInstance();

                long packets_in_last_30_seconds = realm.where(MeasurementRealm.class).between("mTime", System.currentTimeMillis() - 30000, System.currentTimeMillis()).count();
                Log.e("non-Average Num in 30 seconds", String.valueOf(packets_in_last_30_seconds));
                double avg_30_seconds = packets_in_last_30_seconds/30.0;
                Log.e("Average 30 seconds", String.valueOf(avg_30_seconds));

                long packets_in_last_10_minutes = realm.where(MeasurementRealm.class).between("mTime", System.currentTimeMillis() - (1000 * 60 * 10), System.currentTimeMillis()).count();
                Log.e("non-Average Num in 10 minutes", String.valueOf(packets_in_last_10_minutes));
                double avg_10_minutes = packets_in_last_10_minutes/(60.0*10.0);
                Log.e("Average 10 minutes", String.valueOf(avg_10_minutes));

                long packets_in_last_hour = realm.where(MeasurementRealm.class).between("mTime", System.currentTimeMillis() - (1000 * 60 * 60), System.currentTimeMillis()).count();
                Log.e("non-Average Num in hour", String.valueOf(packets_in_last_hour));
                double avg_hour = packets_in_last_hour/(60.0*10.0*60.0);
                Log.e("Average hour", String.valueOf(avg_hour));

                AvgWitWriter avgWitWriter = new AvgWitWriter(this.getClass().getName());
                avgWitWriter.log(String.valueOf(System.currentTimeMillis()), String.format("%.2f", avg_30_seconds) + "," + String.format("%.2f",avg_10_minutes) + "," + String.format("%.2f",avg_hour));
            }
            catch (java.lang.NullPointerException e) {
                Log.e("Average service err", e.getMessage());
            }

        }
    }


}
