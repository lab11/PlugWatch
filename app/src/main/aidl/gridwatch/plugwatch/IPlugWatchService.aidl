// IPlugWatchService.aidl
package gridwatch.plugwatch;

// Declare any non-default types here with import statements

interface IPlugWatchService {

    long get_last_time();
    boolean get_is_connected();
    int get_num_wit();
    int get_num_gw();
    void set_phone_id(int phone_id);
    void set_group_id(int group_id);

}
