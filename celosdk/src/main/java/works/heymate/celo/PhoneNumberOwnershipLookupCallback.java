package works.heymate.celo;

public interface PhoneNumberOwnershipLookupCallback {

    void onPhoneNumberOwnershipLookupResult(boolean success, boolean verified, int completedAttestations, int totalAttestations, int remainingAttestations, CeloException errorCause);

}
