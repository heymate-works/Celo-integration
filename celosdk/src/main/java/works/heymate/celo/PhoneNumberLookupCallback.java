package works.heymate.celo;

import java.util.List;

public interface PhoneNumberLookupCallback {

    void onPhoneNumberLookupResult(boolean success, List<String> assignedAccounts, CeloException errorCause);

}
