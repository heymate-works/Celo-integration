package works.heymate.celoexploration;

public interface AttestationRequestCallback {

    void progressUpdate(String update);

    void onAttestationRequestResult(boolean success, boolean requested, String message);

}
