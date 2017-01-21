// IPlugWatchService.aidl
package gridwatch.plugwatch;

// Declare any non-default types here with import statements

interface IPlugWatchService {


    long get_last_time();
    boolean get_is_connected();
    int get_num_wit();
    int get_num_gw();
    void set_phone_id(String phone_id);
    void set_group_id(String group_id);

    String get_mac();
    void set_whitelist(boolean whitelist, String mac);

    int get_pid();

    void set_build_str(String cur_build_str);

    String get_realm_filename();

    void set_wifi(String wifi);

    String get_ui_update();


}
