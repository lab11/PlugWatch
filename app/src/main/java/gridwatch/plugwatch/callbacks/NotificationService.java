package gridwatch.plugwatch.callbacks;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

import gridwatch.plugwatch.configs.DatabaseConfig;
import gridwatch.plugwatch.configs.SettingsConfig;
import gridwatch.plugwatch.database.Ack;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;

/**
 * Created by nklugman on 11/30/16.
 */

public class NotificationService extends AccessibilityService {

    public static String TAG = "XXXX";

    private static boolean check_number = false;
    private static boolean check_airtime = false;
    private static boolean check_data = false;
    private static boolean topup_airtime = false;
    private static boolean topup_internet = false;

    private DatabaseReference mDatabase;


    public static String pin;

    public String cur_phone_id = "-1";
    public String cur_group_id = "-1";


    private static boolean acted = false;

    private void reset_statemachine() {
        check_number = false;
        check_airtime = false;
        check_data = false;
        topup_airtime = false;
        topup_internet = false;
        mDatabase = FirebaseDatabase.getInstance().getReference();
        PhoneIDWriter r = new PhoneIDWriter(getApplicationContext(), getClass().getName());
        cur_phone_id = r.get_last_value();
        GroupIDWriter w = new GroupIDWriter(getApplicationContext(), getClass().getName());
        cur_group_id = w.get_last_value();

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        reset_statemachine();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String action = sp.getString(SettingsConfig.USSD_ACTION, "");
        String extra;
        if (action.equals(SettingsConfig.USSD_CHECK_PHONENUM)) {
            Log.e("notification_service: phonenum", action);
            check_number = true;
        } else if (action.equals(SettingsConfig.USSD_CHECK_INTERNET)) {
            Log.e("notification_service: checkinternet", action);
            check_data = true;
        } else if (action.equals(SettingsConfig.USSD_CHECK_AIRTIME)) {
            Log.e("notification_service: checkairtime", action);
            check_airtime = true;
        } else if (action.equals(SettingsConfig.USSD_TOPUP_INTERNET)) {
            Log.e("notification_service: topupinternet", action);
            pin = sp.getString(SettingsConfig.USSD_PIN,"");
            topup_internet = true;
        } else if (action.equals(SettingsConfig.USSD_TOPUP_AIRTIME)) {
            Log.e("notification_service: topupairtime", action);
            pin = sp.getString(SettingsConfig.USSD_PIN,"");
            topup_airtime = true;
        }


        if (check_number) {
            Log.e(TAG, "check number");
        }
        if (check_data) {
            Log.e(TAG, "check data");
        }
        if (check_airtime) {
            Log.e(TAG, "check airtime");
        }
        if (topup_internet) {
            Log.e(TAG, "topup data");
        }
        if (topup_airtime) {
            Log.e(TAG, "topup internet");
        }

        String text = event.getText().toString();
        Log.d(TAG, text);
        if (text.contains("would like to send a message")) {
            Log.e(TAG, "returning");
            return;
        }

        if (event.getClassName().equals("android.app.AlertDialog")) {
            AccessibilityNodeInfo nodeInfo = event.getSource();



            if (nodeInfo == null) {
                Log.e("final result", result_parser(text));
                return;
            }
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            performGlobalAction(GLOBAL_ACTION_BACK);


            if (text.substring(1,5).equals("Self")) {
                if (check_data || check_number || check_airtime || topup_airtime || topup_internet)
                Log.e(TAG, "a");
                act(rootNode, "1", event);

            }
            if (text.substring(1,3).equals("My")) {
                Log.e(TAG, "b");
                if (check_number) {
                    Log.e(TAG, "b.1");
                    act(rootNode, "5", event);
                }
                if (check_airtime) {
                    Log.e(TAG, "b.2");
                    act(rootNode, "1", event);
                }
                if (topup_airtime) {
                    Log.e(TAG, "b.3");
                    act(rootNode, "2", event);
                }
            }
            if (text.substring(1,7).equals("Please")) {
                Log.e(TAG, "c");
                act(rootNode, pin, event);
            }
            if (text.substring(3,9).equals("Safari")) {
                if (check_data) {
                    Log.e(TAG, "d");
                    act(rootNode, "6", event);
                }
            }
        } else {
            Log.e("final result", result_parser(text));
        }
    }

    private void act(AccessibilityNodeInfo rootNode, String text, AccessibilityEvent event){
        Log.e("acting with", text);
            acted = true;
            int childCount = rootNode.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo tmpNode = rootNode.getChild(i);
                int subChildCount = tmpNode.getChildCount();
                if (subChildCount == 0) {
                    if (tmpNode.getClassName().toString().contentEquals("android.widget.EditText")) {
                        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clipData = ClipData.newPlainText("NotificationService", text);
                        clipboardManager.setPrimaryClip(clipData);
                            tmpNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);

                        AccessibilityNodeInfo nodeInfo = event.getSource();
                        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId("com.android.settings:id/left_button");
                        list = nodeInfo.findAccessibilityNodeInfosByViewId("android:id/button1");
                        for (AccessibilityNodeInfo node : list) {
                            Log.i(TAG, "ACC::onAccessibilityEvent: button1 " + node);
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                    }
                    return;
                }
                if (subChildCount > 0) {
                    act(tmpNode, text, event);
                }
            }

    }

    private String result_parser(String r) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());


        if (check_number) {
            try {
                r = r.split(" ")[3].replace(".","").replace(",","");
                sp.edit().putString(SettingsConfig.PHONE_NUM, r).commit();
                Ack a = new Ack(System.currentTimeMillis(), r, cur_phone_id, cur_group_id);
                int new_check_number_num = sp.getInt(SettingsConfig.NUM_PHONENUM_CHECK, 0) + 1;
                sp.edit().putInt(SettingsConfig.NUM_PHONENUM_CHECK, new_check_number_num).commit();
                mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.PHONENUM).child(String.valueOf(new_check_number_num)).setValue(a);

            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.e(TAG, r);
            return r;
        }
        if (check_airtime) {
            try {
                r = r.split(" ")[3] + " " + r.split(" ")[4].replace(",", "");
                sp.edit().putString(SettingsConfig.AIRTIME, r).commit();
                Ack a = new Ack(System.currentTimeMillis(), r, cur_phone_id, cur_group_id);
                int new_airtime_check_num = sp.getInt(SettingsConfig.NUM_AIRTIME_CHECK, 0) + 1;
                sp.edit().putInt(SettingsConfig.NUM_AIRTIME_CHECK, new_airtime_check_num).commit();
                mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.AIRTIME_BALANCE).child(String.valueOf(new_airtime_check_num)).setValue(a);

            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.e(TAG, r);
            return r;
        }
        if (check_data) {
            Ack a = new Ack(System.currentTimeMillis(), r, cur_phone_id, cur_group_id);
            int new_internet_check_num = sp.getInt(SettingsConfig.NUM_INTERNET_CHECK, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_INTERNET_CHECK, new_internet_check_num).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.INTERNET_BALANCE).child(String.valueOf(new_internet_check_num)).setValue(a);

            return r;
        }
        if (topup_airtime) {
            Ack a = new Ack(System.currentTimeMillis(), r, cur_phone_id, cur_group_id);
            int new_num_topup_airtime = sp.getInt(SettingsConfig.NUM_TOPUP_AIRTIME, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_TOPUP_AIRTIME, new_num_topup_airtime).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.TOPUP_AIRTIME).child(String.valueOf(new_num_topup_airtime)).setValue(a);
            return r;
        }
        if (topup_internet) {
            Ack a = new Ack(System.currentTimeMillis(), r, cur_phone_id, cur_group_id);
            int new_num_topup_internet = sp.getInt(SettingsConfig.NUM_TOPUP_INTERNET, 0) + 1;
            sp.edit().putInt(SettingsConfig.NUM_TOPUP_INTERNET, new_num_topup_internet).commit();
            mDatabase.child(cur_phone_id).child(DatabaseConfig.ACK).child(DatabaseConfig.TOPUP_INTERNET).child(String.valueOf(new_num_topup_internet)).setValue(a);
            return r;
        }
        return "";
    }


    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.packageNames = new String[]{"com.android.phone"};
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
    }
}
