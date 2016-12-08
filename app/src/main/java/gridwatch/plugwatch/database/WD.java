package gridwatch.plugwatch.database;

/**
 * Created by nklugman on 11/27/16.
 */

public class WD {


    private long mTime;
    private long mTime_size_last_wit_ms;

    private String mPhoneId;
    private String mGroupID;


    private String mIemi;


    private String mLoc;

    private String mMeasurementSize;
    private String mGWSize;
    private String mVersionNum;
    private String mExternalFreespace;
    private String mInternalFreespace;
    private String mNumRealms;


    private String mIsOnline;


    private String mInfo;

    private long mTotalNetwork;

    private double mBattery;


    public String toString() {
        return "t:" + String.valueOf(mTime) + ",q:" +
                String.valueOf(mTime_size_last_wit_ms) + ",w:" +
                mMeasurementSize + ",g:" + mGWSize + ",v:" + mVersionNum +
                ",e:" + mExternalFreespace + ",i:" + mInternalFreespace +
                ",b:" + mBattery + ",r" + mNumRealms + ",y:" + mTotalNetwork +
                ",d:" + mLoc + ",h" + mIemi + "k:," + mIsOnline + "l:," + mInfo;
    }

    public WD() {

    }

    public WD(long time, long time_size_last_wit_ms, String measurementSize, String GWSize, String numRealms, String versionNum,
              String external_freespace, String internal_freespace, String gwid, String groupid, double battery, long total_network,
              String loc, String iemi, boolean isOnline, String info) {
        mTime = time;
        mTime_size_last_wit_ms = time_size_last_wit_ms;
        mPhoneId = gwid;
        mGroupID = groupid;
        mMeasurementSize = measurementSize;
        mGWSize = GWSize;
        mVersionNum = versionNum;
        mExternalFreespace = external_freespace;
        mInternalFreespace = internal_freespace;
        mBattery = battery;
        mNumRealms = numRealms;
        mTotalNetwork = total_network;
        mLoc = loc;
        mIemi = iemi;
        if (isOnline) {
            mIsOnline = "t";
        } else {
            mIsOnline = "f";
        }
        mInfo = info;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long mTime) {
        this.mTime = mTime;
    }

    public double getBattery() {
        return mBattery;
    }

    public void setBattery(double battery) {
        this.mBattery = battery;
    }

    public String getGWID() {
        return mPhoneId;
    }

    public void setGWID(String mGWID) {
        this.mPhoneId = mGWID;
    }

    public String getNumRealms() {
        return mNumRealms;
    }

    public void setNumRealms(String num) {
        this.mNumRealms = num;
    }

    public String getGroupID() {
        return mGroupID;
    }

    public void setGroupID(String mGroupID) {
        this.mGroupID = mGroupID;
    }


    public String getMeasurementSize() {
        return mMeasurementSize;
    }

    public void setMeasurementSize(String mMeasurementSize) {
        this.mMeasurementSize = mMeasurementSize;
    }

    public String getGWSize() {
        return mGWSize;
    }

    public void setGWSize(String mGWSize) {
        this.mGWSize = mGWSize;
    }

    public String getVersionNum() {
        return mVersionNum;
    }

    public void setVersionNum(String mVersionNum) {
        this.mVersionNum = mVersionNum;
    }

    public String getExternalFreespace() {
        return mExternalFreespace;
    }

    public void setExternalFreespace(String mFreespace) {
        this.mExternalFreespace = mFreespace;
    }

    public String getInternalFreespace() {
        return mInternalFreespace;
    }

    public void setInternalFreespace(String mFreespace) {
        this.mInternalFreespace = mFreespace;
    }


    public long getTime_size_last_wit_ms() {
        return mTime_size_last_wit_ms;
    }

    public void setTime_size_last_wit_ms(long time_size_last_wit_ms) {
        this.mTime_size_last_wit_ms = time_size_last_wit_ms;
    }

    public long getTotalNetwork() {
        return mTotalNetwork;
    }

    public void setTotalNetwork(long totalNetwork) {
        this.mTotalNetwork = totalNetwork;
    }

    public String getLoc() {
        return mLoc;
    }

    public void setLoc(String loc) {
        this.mLoc = loc;
    }

    public String getIemi() {
        return mIemi;
    }

    public void setIemi(String Iemi) {
        this.mIemi = Iemi;
    }


    public String getInfo() {
        return mInfo;
    }

    public void setInfo(String info) {
        this.mInfo = info;
    }

    public String getIsOnline() {
        return mIsOnline;
    }

    public void setmIsOnline(String isOnline) {
        this.mIsOnline = isOnline;
    }

}