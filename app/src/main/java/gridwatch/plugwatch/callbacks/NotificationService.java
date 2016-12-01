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

import java.util.List;

import gridwatch.plugwatch.configs.SettingsConfig;

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

    public static String pin;

    private static boolean acted = false;

    private void reset_statemachine() {
        check_number = false;
        check_airtime = false;
        check_data = false;
        topup_airtime = false;
        topup_internet = false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        reset_statemachine();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String action = sp.getString("action", "");
        String extra;
        if (action.equals("phone_num")) {
            Log.e("notification_service: phonenum", action);
            check_number = true;
        } else if (action.equals("check_internet")) {
            Log.e("notification_service: checkinternet", action);
            check_data = true;
        } else if (action.equals("check_airtime")) {
            Log.e("notification_service: checkairtime", action);
            check_airtime = true;
        } else if (action.equals("topup_internet")) {
            Log.e("notification_service: topupinternet", action);
            pin = sp.getString("extra","");
            topup_internet = true;
        } else if (action.equals("topup_airtime")) {
            Log.e("notification_service: topupairtime", action);
            pin = sp.getString("extra","");
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
        if (event.getClassName().equals("android.app.AlertDialog")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            AccessibilityNodeInfo nodeInfo = event.getSource();
            if (nodeInfo == null) {
                Log.e("final result", result_parser(text));
                return;
            }
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (text.substring(1,5).equals("Self")) {
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.e(TAG, r);
            return r;
        }
        if (check_data) {
            return r;
        }
        if (topup_airtime) {
            return r;
        }
        if (topup_internet) {
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
