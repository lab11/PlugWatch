package gridwatch.plugwatch.network;

import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.io.Serializable;

/**
 * Created by nklugman on 11/18/16.
 */

public class MetaRetrofit implements Serializable {

    private String i; //phone_id
    private String c; //network state
    private String t; //time
    private String r; //group_id
    private String l; //lat
    private String n; //lng
    private String w; //num wit
    private String g; //num gridwatch
    private String m; //time last wit
    private String q; //total data

    public MetaRetrofit() {

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


