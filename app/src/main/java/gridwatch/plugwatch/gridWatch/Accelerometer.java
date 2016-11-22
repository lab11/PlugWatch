package gridwatch.plugwatch.gridWatch;

import android.hardware.Sensor;
import android.os.Bundle;

/**
 * Created by nklugman on 11/19/16.
 */

public class Accelerometer extends SensorActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        sensorType = Sensor.TYPE_ACCELEROMETER;
        sensorName = "accelerometer";
        super.onCreate(savedInstanceState);
    }
}