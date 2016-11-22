package gridwatch.plugwatch.gridWatch;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.github.pwittchen.reactivesensors.library.ReactiveSensors;

import rx.Subscription;

public abstract class SensorActivity extends AppCompatActivity {
    protected int sensorType;
    protected String sensorName;

    private ReactiveSensors reactiveSensors;
    private Subscription subscription;
    private SensorHelper sensorHelper;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reactiveSensors = new ReactiveSensors(this);
        sensorHelper = new SensorHelper(reactiveSensors, sensorType, sensorName);
    }

    @Override protected void onResume() {
        super.onResume();

        if (!sensorHelper.deviceHasSensor()) {
            return;
        }

        subscription = sensorHelper.createSubscription();
    }

    @Override protected void onPause() {
        super.onPause();
        sensorHelper.safelyUnsubscribe(subscription);
    }
}