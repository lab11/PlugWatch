package gridwatch.plugwatch.database;

import io.realm.RealmObject;

/**
 * Created by nklugman on 11/7/16.
 */

public class MeasurementRealm extends RealmObject {

    private String mCurrent;
    private String mFrequency;
    private String mPower;
    private String mPowerFactor;
    private String mVoltage;
    private long mTime;
    private String mMac;

    public MeasurementRealm() {}

    public MeasurementRealm(String current, String frequency,
                       String power, String powerFactor,
                       String voltage, String mac) {
        this.mVoltage = voltage;
        this.mCurrent = current;
        this.mPower = power;
        this.mPowerFactor = powerFactor;
        this.mFrequency = frequency;
        this.mTime = System.currentTimeMillis();
        this.mMac = mac;
    }

    public String getCurrent() {
        return mCurrent;
    }

    public String getFrequency() {
        return mFrequency;
    }

    public String getPower() {
        return mPower;
    }

    public String getPowerFactor() {
        return mPowerFactor;
    }

    public String getVoltage() {
        return mVoltage;
    }

    public long getTime() {
        return mTime;
    }

    public String getMac() {
        return mMac;
    }

    @Override
    public String toString() {
        return "Measurement {"
                + "voltage="
                + mVoltage
                + ", current="
                + mCurrent
                + ", power="
                + mPower
                + ", powerFactor="
                + mPowerFactor
                + ", frequency="
                + mFrequency
                + ", time="
                + String.valueOf(mTime)
                + ", mac="
                + mMac
                + '}';
    }


}
