package gridwatch.plugwatch.database;

import com.loopj.android.http.RequestParams;

import gridwatch.plugwatch.configs.DatabaseConfig;
import io.realm.RealmObject;

/**
 * Created by nklugman on 6/30/16.
 */
public class ID extends RealmObject {


    private String mID;
    private String mTYPE;
    private long mTIME;


    public ID() {

    }

    public ID(String type, String id) {
        mID = id;
        mTYPE = type;
        mTIME = System.currentTimeMillis();
    }

    public RequestParams toRequestParams() {
        RequestParams values = new RequestParams();
        values.put(DatabaseConfig.TYPE, mTYPE);
        values.put(DatabaseConfig.ID, mID);
        values.put("wifi", "no");
        return values;
    }

    public String getID() {
        return mID;
    }

    public String getType() {
        return mTYPE;
    }

}


