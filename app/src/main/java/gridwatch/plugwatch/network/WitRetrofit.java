package gridwatch.plugwatch.network;

import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.io.Serializable;

import ch.hsr.geohash.GeoHash;

/**
 * Created by nklugman on 11/18/16.
 */

public class WitRetrofit implements Serializable {

    private String a; //time runing
    private String b; //version number
    private String c; //current
    private String e; //group_id
    private String f; //frequency
    private String g; //total size
    private String h; //geohash
    private String i; //phone_id
    private String m; //mac
    private String p; //power
    private double q; //lat
    private String r; //powerfactor
    private long t;   //time
    private String u; //wifi
    private String v; //voltage
    private double w; //lng
    private String y; //cross posted
    private String z; //cur size


    public WitRetrofit(String current, String frequency,
                          String power, String powerFactor,
                          String voltage, long now, double lat,
                       double lng, String phone_id, String experiment_id,
                       String version_num, String cur_size,
                       String mac, String cp, String wifi, String total_size,
                       String time_running) {
        this.c = current;
        this.f = frequency;
        this.p = power;
        this.r = powerFactor;
        this.v = voltage;
        this.t = now;
        this.q = lat;
        this.w = lng;
        this.i = phone_id;
        this.e = experiment_id;
        this.b = version_num;
        this.z = cur_size;
        this.h = GeoHash.geoHashStringWithCharacterPrecision(lat,lng,10);
        this.m = mac;
        this.y = cp;
        this.u = wifi;
        this.g = total_size;
        this.a = time_running;
    }

    public PersistableBundleCompat toBundle() {
        PersistableBundleCompat measurement = new PersistableBundleCompat();
        measurement.putString("c", c);
        measurement.putString("f", f);
        measurement.putString("p", p);
        measurement.putString("r", r);
        measurement.putString("v", v);
        measurement.putLong("t", t);
        measurement.putDouble("q", q);
        measurement.putDouble("w", w);
        measurement.putString("i", i);
        measurement.putString("e", e);
        measurement.putString("b", b);
        measurement.putString("z", z);
        measurement.putString("h", h);
        measurement.putString("m", m);
        measurement.putString("y", y);
        measurement.putString("u", u);
        measurement.putString("g", g);
        measurement.putString("a", a);

        return measurement;
    }

    public String toString() {
        return "current:" + c + ",frequency:" + f +
                ",power:" + p + ",powerFactor:" + r + "\n" +
                ",voltage:" + v + ",now:" + String.valueOf(t) +
                ",lat:" + String.valueOf(q) + ",lng:" + String.valueOf(w) + "\n" +
                ",phone_id:" + i + ",group_id:" + e +
                ",build_num:" + b + ",cur_size:" + z + ",geohash:" + h +
                ",mac:" + m + ",cp:" + y + ",wifi:" + u + ",total_size:" + g + ",time_running:" + a;
    }


    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }

    public String getG() {
        return g;
    }

    public void setG(String g) {
        this.g = g;
    }

    public String getC() {
        return c;
    }

    public void setC(String c) {
        this.c = c;
    }

    public String getF() {
        return f;
    }

    public void setF(String f) {
        this.f = f;
    }

    public String getP() {
        return p;
    }

    public void setP(String p) {
        this.p = p;
    }

    public String getR() {
        return r;
    }

    public void setR(String r) {
        this.r = r;
    }

    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }

    public long getT() {
        return t;
    }

    public void setT(long t) {
        this.t = t;
    }

    public double getQ() {
        return q;
    }

    public void setQ(int q) {
        this.q = q;
    }

    public double getW() {
        return w;
    }

    public void setW(int w) {
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

    public void setZ(String z) {
        this.z = z;
    }

    public String getZ() {
        return z;
    }

    public String getH() {
        return h;
    }

    public void setH(String h) {
        this.h = h;
    }

    public String getM() {
        return m;
    }

    public void setM(String m) {
        this.m = m;
    }

    public String getY() {
        return y;
    }

    public void setY(String y) {
        this.y = y;
    }

    public String getU() {
        return u;
    }

    public void setU(String y) {
        this.u = u;
    }
}


