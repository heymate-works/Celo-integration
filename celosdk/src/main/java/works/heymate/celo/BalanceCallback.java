package works.heymate.celo;

import java.math.BigInteger;

public interface BalanceCallback {

    void onBalanceResult(boolean success, BigInteger rawCUSD, BigInteger rawGold, long cUSDCents, double gold, CeloException errorCause);

}
