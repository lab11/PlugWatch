package gridwatch.plugwatch.callbacks;

import android.content.Context;

import gridwatch.plugwatch.utilities.Restart;

/**
 * Created by nklugman on 6/24/16.
 */
public class RestartOnExceptionHandler extends Throwable implements
        Thread.UncaughtExceptionHandler {
    private final Context myContext;
    private final Class<?> myActivityClass;

    public RestartOnExceptionHandler(Context context, Class<?> c) {
        myContext = context;
        myActivityClass = c;
    }

    public void uncaughtException(Thread thread, Throwable exception) {
        Restart r = new Restart();
        r.do_restart(myContext, myActivityClass, exception);
    }
}