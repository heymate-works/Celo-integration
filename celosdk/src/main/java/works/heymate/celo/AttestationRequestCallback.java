package works.heymate.celo;

public interface AttestationRequestCallback {

    /**
     * Number of previously pending attestations = totalAttestations - newAttestations - completedAttestations
     * @param countsAreReliable If the provided counts are reliable. False means some error stopped the process before querying
     * @param newAttestations newly generated attestations that user should be expecting SMSs from
     * @param totalAttestations total existing attestations
     * @param completedAttestations number of attestations completed so far
     * @param errorCause
     */
    void onAttestationRequestResult(boolean countsAreReliable, int newAttestations, int totalAttestations, int completedAttestations, CeloException errorCause);

}
