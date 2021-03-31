package works.heymate.celo;

import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;

public class CurrencyUtil {

    private static final BigInteger WEI = Convert.toWei(BigDecimal.ONE, Convert.Unit.ETHER).toBigInteger();;
    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100L);

    public static BigInteger centsToBlockChainValue(long cents) {
        return BigInteger.valueOf(cents).multiply(WEI).divide(ONE_HUNDRED);
    }

    public static final long blockChainValueToCents(BigInteger value) {
        return value.multiply(ONE_HUNDRED).divide(WEI).longValue();
    }

}
