package gridwatch.plugwatch.network;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;

/**
 * Created by nklugman on 11/25/16.
 */

public class NetworkJobCreator implements JobCreator {

    @Override
    public Job create(String tag) {
        switch (tag) {
            case NetworkJob.TAG:
                return new NetworkJob();
            case GWJob.TAG:
                return new GWJob();
            default:
                return null;
        }
    }


}