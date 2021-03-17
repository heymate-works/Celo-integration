package works.heymate.celo;

import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.utils.Numeric;

import java.security.SecureRandom;

public class CeloAccount {

    /**
     * This method is time consuming. You might wanna consider to run it in a separate thread.
     * @return
     */
    public static CeloAccount randomAccount() {
        byte[] privateKeyBytes = new byte[16];
        new SecureRandom().nextBytes(privateKeyBytes);

        ECKeyPair keyPair = ECKeyPair.create(privateKeyBytes);

        String privateKey = keyPair.getPrivateKey().toString(16);
        String publicKey = keyPair.getPublicKey().toString(16);

        return new CeloAccount(privateKey, publicKey);
    }

    /**
     * This method is time consuming. You might wanna consider to run it in a separate thread.
     * @param mnemonic
     * @return
     */
    public static CeloAccount fromMnemonic(String mnemonic) {
        try {
            byte[] privateKeyBytes = MnemonicUtils.generateEntropy(mnemonic);

            ECKeyPair keyPair = ECKeyPair.create(privateKeyBytes);

            String privateKey = keyPair.getPrivateKey().toString(16);
            String publicKey = keyPair.getPublicKey().toString(16);

            return new CeloAccount(privateKey, publicKey);
        } catch (Throwable t) {
            return null;
        }
    }

    public final String privateKey;
    public final String publicKey;

    public CeloAccount(String privateKey, String publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public String getMnemonic() {
        return MnemonicUtils.generateMnemonic(Numeric.hexStringToByteArray(privateKey));
    }

}
