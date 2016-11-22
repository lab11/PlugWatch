package gridwatch.plugwatch.network;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;

/**
 * Created by nklugman on 11/18/16.
 */

public class WitJobCreator implements JobCreator {

    @Override
    public Job create(String tag) {
        switch (tag) {
            case WitJob.TAG:
                return new WitJob();
            default:
                return null;
        }
    }
}