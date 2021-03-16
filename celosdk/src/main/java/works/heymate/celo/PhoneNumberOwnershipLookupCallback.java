package works.heymate.celo;

public interface PhoneNumberOwnershipLookupCallback {

    void onPhoneNumberOwnershipLookupResult(boolean success, boolean owned, CeloException errorCause);

}
