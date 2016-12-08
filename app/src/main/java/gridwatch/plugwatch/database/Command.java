package gridwatch.plugwatch.database;

import com.loopj.android.http.RequestParams;

import io.realm.RealmObject;

/**
 * Created by nklugman on 6/30/16.
 */
public class Command extends RealmObject {


    private String mType;
    private long mTime;
    private boolean misText;
    private String mGWID;
    private String mGroupID;

    public Command() {

    }

    public Command(long time, String type, boolean isText, String gwid, String groupid) {
        mType = type;
        mTime = time;
        misText = isText;
        mGWID = gwid;
        mGroupID = groupid;
    }

    public RequestParams toRequestParams() {
        RequestParams values = new RequestParams();
        values.put("time", mTime);
        values.put("type", "command");
        values.put("cmd", mType);
        values.put("isText", misText);
        values.put("id", mGWID);
        values.put("group", mGroupID);
        values.put("wifi", "yes");
        return values;
    }

    public String toString() {
        return "t:" + String.valueOf(mTime) + ",m:" + mType + ",i:" + String.valueOf(misText);
    }

    public String getType() {
        return mType;
    }

    public void setType(String mType) {
        this.mType = mType;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long time) {
        this.mTime = time;
    }

    public boolean getIsText() {
        return misText;
    }

    public void setIsText(boolean state) {
        this.misText = state;
    }

    public String getPhoneID() {
        return mGWID;
    }

    public void setPhoneID(String phone_id) {
        this.mGWID = phone_id;
    }

    public String getGroupID() {
        return mGroupID;
    }

    public void setGroupID(String mGroupID) {
        this.mGroupID = mGroupID;
    }



}