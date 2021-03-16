package works.heymate.celo;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;

class InternalUtils {

    private static Handler mMainThreadHandler = null;

    public static void runOnMainThread(Runnable r) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            r.run();
        }
        else {
            synchronized (InternalUtils.class) {
                if (mMainThreadHandler == null) {
                    mMainThreadHandler = new Handler(Looper.getMainLooper());
                }
            }

            mMainThreadHandler.post(r);
        }
    }

    public static String streamToString(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();

        byte[] buffer = new byte[512];
        int offset = 0;
        int size = 0;

        while (size != -1) {
            size = inputStream.read(buffer, offset, buffer.length - offset);

            if (offset + size > 1) {
                int read = offset + size;

                if (read % 2 != 0) {
                    read--;
                    offset = 1;
                }
                else {
                    offset = 0;
                }

                sb.append(new String(buffer, 0, read, "UTF-8"));

                if (offset == 1) {
                    buffer[0] = buffer[read];
                }
            }
            else if (size != -1) {
                offset += size;
            }
        }

        if (offset > 0) {
            sb.append((char) buffer[0]);
        }

        return sb.toString();
    }

}
