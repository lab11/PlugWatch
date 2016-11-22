package gridwatch.plugwatch.network;

import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.io.Serializable;

/**
 * Created by nklugman on 11/20/16.
 */

public class SettingsRetrofit implements Serializable {

    private String i; //phone_id
    private String c; //settings dmp
    private String t; //time
    //private String z; //

    public SettingsRetrofit(String phone_id, String dump,
                       String time) {
        this.c = dump;
        this.i = phone_id;
        this.t = time;
        //z = NetworkConfig.ACK;
    }

    public PersistableBundleCompat toBundle() {
        PersistableBundleCompat ack = new PersistableBundleCompat();
        ack.putString("c", c);
        ack.putString("i", i);
        ack.putString("t", t);
        //ack.putString("z", z);
        return ack;
    }

    public String getC() {
        return c;
    }

    public void setC(String c) {
        this.c = c;
    }

    public String getI() {
        return i;
    }

    public void setI(String i) {
        this.i = i;
    }

    public String getT() {
        return t;
    }

    public void setT(String t) {
        this.t = t;
    }

}
