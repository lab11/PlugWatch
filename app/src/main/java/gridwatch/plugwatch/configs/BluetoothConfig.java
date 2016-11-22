package gridwatch.plugwatch.configs;

/**
 * Created by nklugman on 11/1/16.
 */

import java.util.UUID;

public class BluetoothConfig {


    public static final UUID UUID_WIT_FFE1;
    public static final UUID UUID_WIT_FFE3;
    public static final UUID UUID_WIT_SERV;

    static {
        UUID_WIT_SERV = UUID.fromString("0000fee0-494c-4f47-4943-544543480000");
        UUID_WIT_FFE1 = UUID.fromString("0000fee1-494c-4f47-4943-544543480000");
        UUID_WIT_FFE3 = UUID.fromString("0000fee3-494c-4f47-4943-544543480000");
    }

    public static final String API_URL = "http://141.212.11.206:3000";


}
