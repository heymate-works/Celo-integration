package works.heymate.celo;

public interface AddressCallback {

    void onAddressResult(boolean success, String address, CeloException errorCause);

}
