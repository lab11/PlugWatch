package gridwatch.plugwatch.wit;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.MeasurementRealm;
import gridwatch.plugwatch.logs.AvgWitWriter;
import io.realm.Realm;

import static gridwatch.plugwatch.wit.App.getContext;


public class AverageService extends IntentService {

    static final AppPreferences appPreferences = new AppPreferences(getContext());


    public AverageService() {
        super("AverageMinuteService");
    }



    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            try {

                Realm.init(getApplicationContext());
                Realm realm = Realm.getDefaultInstance();



                long num_wit = realm.where(MeasurementRealm.class).count();
                appPreferences.put(SettingsConfig.WIT_SIZE, num_wit);
                Log.e("good data: REALM", "new size is " + String.valueOf(num_wit));
                long total_wit = 0;
                try {
                    total_wit = appPreferences.getLong(SettingsConfig.TOTAL_WIT_SIZE);
                } catch (ItemNotFoundException e) {
                    e.printStackTrace();
                }
                if (total_wit < num_wit) { //this happens in testing...
                    total_wit = num_wit;
                } else {
                    total_wit += 1;
                }
                appPreferences.put(SettingsConfig.TOTAL_WIT_SIZE, total_wit);

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
                double avg_hour = packets_in_last_hour/(60.0*60.0);
                Log.e("Average hour", String.valueOf(avg_hour));

                AvgWitWriter avgWitWriter = new AvgWitWriter(this.getClass().getName());
                avgWitWriter.log(String.valueOf(System.currentTimeMillis()), String.format("%.2f", avg_30_seconds) + "," + String.format("%.2f",avg_10_minutes) + "," + String.format("%.2f",avg_hour));

                realm.close();

            }
            catch (NullPointerException | IllegalStateException e) {
                Log.e("Average service err", e.getMessage());
            }

        }
    }


}
