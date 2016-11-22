package gridwatch.plugwatch.callbacks;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;

/**
 * Created by nklugman on 11/21/16.
 */

public class ConnectivityJobCreator implements JobCreator {

    @Override
    public Job create(String tag) {
        switch (tag) {
            case ConnectivityJob.TAG:
                return new ConnectivityJob();
            default:
                return null;
        }
    }
}