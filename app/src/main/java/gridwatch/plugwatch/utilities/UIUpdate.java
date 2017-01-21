package gridwatch.plugwatch.utilities;

import java.io.Serializable;

/**
 * Created by nklugman on 12/28/16.
 */

public class UIUpdate implements Serializable {

    private int m_num_gw;
    private int m_num_wit;
    private int m_total_data;
    private int m_seconds_since_last;
    private int m_free_size;
    private int m_group_id;
    private int m_phone_id;
    private boolean m_is_sms_good;
    private String m_mac;
    private boolean m_is_cross_paired;
    private boolean m_is_no_reboot;
    private boolean m_is_lpm;
    private boolean m_is_online;
    private int m_num_realm;

    public UIUpdate(int num_gw, int num_wit, int total_data, int second_since_last,
                    int free_size, int group_id, int phone_id, boolean is_sms_good,
                    String mac, boolean is_cross_paired, boolean is_no_reboot,
                    boolean is_lpm, boolean is_online, int num_realm) {
        m_num_gw = num_gw;
        m_num_wit = num_wit;
        m_total_data = total_data;
        m_seconds_since_last = second_since_last;
        m_free_size = free_size;
        m_group_id = group_id;
        m_phone_id = phone_id;
        m_is_sms_good = is_sms_good;
        m_mac = mac;
        m_is_cross_paired = is_cross_paired;
        m_is_no_reboot = is_no_reboot;
        m_is_lpm = is_lpm;
        m_is_online = is_online;
        m_num_realm = num_realm;
    }




}
