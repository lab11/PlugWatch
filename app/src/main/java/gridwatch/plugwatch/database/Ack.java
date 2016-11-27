package gridwatch.plugwatch.database;

import com.loopj.android.http.RequestParams;

/**
 * Created by nklugman on 11/27/16.
 */

public class Ack {


    private long mTime;
    private String mText;
    private String mGWID;
    private String mGroupID;

    public Ack() {

    }

    public Ack(long time, String text, String gwid, String groupid) {
        mTime = time;
        mText = text;
        mGWID = gwid;
        mGroupID = groupid;
    }

    public RequestParams toRequestParams() {
        RequestParams values = new RequestParams();
        values.put("type", "crash");
        values.put("time", mTime);
        values.put("text", mText);
        values.put("id", mGWID);
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

    public String getText() {
        return mText;
    }

    public void setText(String mText) {
        this.mText = mText;
    }

    public String getGWID() {
        return mGWID;
    }

    public void setGWID(String mGWID) {
        this.mGWID = mGWID;
    }

    public String getGroupID() {
        return mGroupID;
    }

    public void setGroupID(String mGroupID) {
        this.mGroupID = mGroupID;
    }





}
