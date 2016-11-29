package gridwatch.plugwatch.network;

import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.io.Serializable;

import ch.hsr.geohash.GeoHash;

/**
 * Created by nklugman on 11/18/16.
 */

public class GWRetrofit implements Serializable {

    private long t; //time
    private String a; //event type
    private double q; //lat
    private double w; //lng
    private String i; //phone_id
    private String e; //group_id
    private String b; //version number
    private String z; //cur size
    private String h; //geohash
    private long o; //last packet
    private double c; //battery life
    private String d; //cross paired


    public GWRetrofit(String type, long now, double lat,
                      double lng, String phone_id, String experiment_id,
                      String version_num, String cur_size, long last, double battery, String cross) {
        this.a = type;
        this.t = now;
        this.q = lat;
        this.w = lng;
        this.i = phone_id;
        this.e = experiment_id;
        this.b = version_num;
        this.z = cur_size;
        this.h = GeoHash.geoHashStringWithCharacterPrecision(lat,lng,10);
        this.o = last;
        this.c = battery;
        this.d = cross;

        //this.b = PlugWatchApp.getInstance().buildStr;
    }

    public PersistableBundleCompat toBundle() {
        PersistableBundleCompat measurement = new PersistableBundleCompat();
        measurement.putLong("t", t);
        measurement.putString("a", a);
        measurement.putDouble("q", q);
        measurement.putDouble("w", w);
        measurement.putString("i", i);
        measurement.putString("e", e);
        measurement.putString("b", b);
        measurement.putString("z", z);
        measurement.putString("h", h);
        measurement.putLong("o", o);
        measurement.putDouble("c", c);
        measurement.putString("d", d);
        return measurement;
    }

    public String toString() {
        return "time:" + String.valueOf(t) + ",type:" + a +
                ",lat:" + String.valueOf(q) + ",lng:" + String.valueOf(w) + "\n" +
                ",phone_id:" + i + ",group_id:" + String.valueOf(e) + ",version:" + b +
                ",size:" + z + ",geohash:" + h + ",last:" + String.valueOf(o) + ",life:" +
                String.valueOf(c) + "\n" + ",cross:" + String.valueOf(d);
    }

    public long getT() {
        return t;
    }

    public void setT(long t) {
        this.t = t;
    }

    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }

    public double getQ() {
        return q;
    }

    public void setQ(double q) {
        this.q = q;
    }

    public double getW() {
        return w;
    }

    public void setW(double w) {
        this.w = w;
    }

    public String getI() {
        return i;
    }

    public void setI(String i) {
        this.i = i;
    }

    public String getE() {
        return e;
    }

    public void setE(String e) {
        this.e = e;
    }

    public String getB() {
        return b;
    }

    public void setB(String b) {
        this.b = b;
    }

    public String getZ() {
        return z;
    }

    public void setZ(String z) {
        this.z = z;
    }

    public String getH() {
        return h;
    }

    public void setH(String h) {
        this.h = h;
    }

    public long getO() {
        return o;
    }

    public void setO(long o) {
        this.o = o;
    }

    public double getC() {
        return c;
    }

    public void setC(double c) {
        this.c = c;
    }

    public String getD() {
        return d;
    }

    public void setD(String d) {
        this.d = d;
    }


}


