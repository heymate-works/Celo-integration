package works.heymate.celo;

import org.celo.contractkit.ContractKit;

public class CeloContext {

    public static CeloContext MAIN_NET = new CeloContext(
            ContractKit.MAINNET,
            42220,  // https://github.com/celo-org/celo-blockchain/blob/079f42f05036ae961f0f8f8f8360b904159d3e5e/params/config.go#L36-L38
            "https://us-central1-celo-pgpnp-mainnet.cloudfunctions.net",
            "FvreHfLmhBjwxHxsxeyrcOLtSonC9j7K3WrS4QapYsQH6LdaDTaNGmnlQMfFY04Bp/K4wAvqQwO9/bqPVCKf8Ze8OZo8Frmog4JY4xAiwrsqOXxug11+htjEe1pj4uMA"
    );

    public static CeloContext ALFAJORES = new CeloContext(
            ContractKit.ALFAJORES_TESTNET,
            44787,
            "https://us-central1-celo-phone-number-privacy.cloudfunctions.net",
            "kPoRxWdEdZ/Nd3uQnp3FJFs54zuiS+ksqvOm9x8vY6KHPG8jrfqysvIRU0wtqYsBKA7SoAsICMBv8C/Fb2ZpDOqhSqvr/sZbZoHmQfvbqrzbtDIPvUIrHgRS0ydJCMsA"
    );

    public final String networkAddress;
    public final int chainId;
    public final String odisURL;
    public final String odisPublicKey;

    public CeloContext(String networkAddress, int chainId, String odisUrl, String odisPublicKey) {
        this.networkAddress = networkAddress;
        this.chainId = chainId;
        this.odisURL = odisUrl;
        this.odisPublicKey = odisPublicKey;
    }

}
