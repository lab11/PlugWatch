// IFailsafeTimerService.aidl
package gridwatch.plugwatch;

// Declare any non-default types here with import statements

interface IFailsafeTimerService {

    void send_last(long last);
    void send_pid_of_plugwatch_service(int pid);
    void send_is_connected(boolean is_connected);
    int get_pid();

}
