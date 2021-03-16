package works.heymate.celo;

public class CeloException extends Exception {

    private CeloError mError;

    public CeloException(CeloError error, Throwable cause) {
        super(error.getMessage(), cause);

        mError = error;
    }

    public CeloException getMainCause() {
        if (getCause() instanceof CeloException) {
            return ((CeloException) getCause()).getMainCause();
        }

        return this;
    }

}
