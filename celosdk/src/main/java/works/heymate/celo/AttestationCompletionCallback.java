package works.heymate.celo;

public interface AttestationCompletionCallback {

    /**
     * If errorCause is not null, integer values are unreliable.
     * @param verified
     * @param completed
     * @param total
     * @param remaining
     * @param errorCause
     */
    void onAttestationCompletionResult(boolean verified, int completed, int total, int remaining, CeloException errorCause);

}
