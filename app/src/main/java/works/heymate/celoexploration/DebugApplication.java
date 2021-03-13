package works.heymate.celoexploration;

import android.app.Application;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;

public class DebugApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        final File logFile = new File(getExternalCacheDir(), "celo.txt");

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            PrintWriter writer;
            try {
                writer = new PrintWriter(new FileOutputStream(logFile));
                e.printStackTrace(writer);
                writer.close();
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(t, e);
            }
        });
    }

}
