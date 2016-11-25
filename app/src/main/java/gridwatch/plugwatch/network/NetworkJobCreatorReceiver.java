package gridwatch.plugwatch.network;

import android.content.Context;
import android.support.annotation.NonNull;

import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;

/**
 * Created by nklugman on 11/25/16.
 */

public class NetworkJobCreatorReceiver extends JobCreator.AddJobCreatorReceiver {

        @Override
        protected void addJobCreator(@NonNull Context context, @NonNull JobManager manager) {
            manager.addJobCreator(new NetworkJobCreator());
        }
}



