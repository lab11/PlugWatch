package gridwatch.plugwatch.inputs;

import android.content.Intent;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.wit.APIService;

/**
 * Created by nklugman on 11/21/16.
 */

public class FirebaseAPI extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // TODO(developer): Handle FCM messages here.
        // If the application is in the foreground handle both data and notification messages here.
        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
        Log.d("gcm api: FirebaseAPI", "From: " + remoteMessage.getFrom());
        Log.d("gcm api: FirebaseAPI", "Notification Message Body: " + remoteMessage.getNotification().getBody());

        try {
            Intent a = new Intent(getApplicationContext(), APIService.class);
            a.putExtra(IntentConfig.INCOMING_API_COMMAND, (String) remoteMessage.getData().get("command"));
            a.putExtra(IntentConfig.INCOMING_API_PHONE_ID, (String) remoteMessage.getData().get("phone_id"));
            a.putExtra(IntentConfig.INCOMING_API_GROUP_ID, (String) remoteMessage.getData().get("group_id"));
            startService(a);
        } catch (Exception e){
            FirebaseCrash.log(e.getMessage());

        }


    }

}
