package works.heymate.celo;

public class SelectiveCall {

    private static final double FACTOR = 1.5d;
    private static final double DELAY = 100;

    public interface Function<T> {

        T call() throws Exception;

    }

    public static <T> T selectiveRetryAsyncWithBackOff(Function<T> function, int tries, String... dontRetry) throws Exception {
        Exception saveError = null;

        for (int i = 0; i < tries; i++) {
            try {
                return function.call();
            } catch (Exception error) {
                String errorMessage = error.getMessage();

                for (String msg: dontRetry) {
                    if (errorMessage.contains(msg)) {
                        throw error;
                    }
                }

                saveError = error;
            }

            if (i < tries - 1) {
                Thread.sleep((long) (Math.pow(FACTOR, i) * DELAY));
            }
        }

        throw saveError;
    }

}
