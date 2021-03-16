package works.heymate.celo;

import org.celo.contractkit.ContractKit;

public interface ContractKitCallback {

    void onContractKitResult(boolean success, ContractKit contractKit, CeloException errorCause);

}
