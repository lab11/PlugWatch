package gridwatch.plugwatch.database;

import com.loopj.android.http.RequestParams;

/**
 * Created by nklugman on 11/27/16.
 */

public class WD {


    private long mTime;
    private long mTime_size_last_wit_ms;

    private String mPhoneId;
    private String mGroupID;



    private String mMeasurementSize;
    private String mGWSize;
    private String mVersionNum;
    private String mExternalFreespace;
    private String mInternalFreespace;




    public WD() {

    }

    public WD(long time, long time_size_last_wit_ms, String measurementSize, String GWSize, String versionNum,
              String external_freespace, String internal_freespace, String gwid, String groupid) {
        mTime = time;
        mTime_size_last_wit_ms = time_size_last_wit_ms;
        mPhoneId = gwid;
        mGroupID = groupid;
        mMeasurementSize = measurementSize;
        mGWSize = GWSize;
        mVersionNum = versionNum;
        mExternalFreespace = external_freespace;
        mInternalFreespace = internal_freespace;
    }

    public RequestParams toRequestParams() {
        RequestParams values = new RequestParams();
        values.put("type", "crash");
        values.put("time", mTime);
        values.put("id", mPhoneId);
        values.put("group", mGroupID);
        values.put("wifi", "no");
        return values;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long mTime) {
        this.mTime = mTime;
    }


    public String getGWID() {
        return mPhoneId;
    }

    public void setGWID(String mGWID) {
        this.mPhoneId = mGWID;
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


}