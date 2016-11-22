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
}