package works.heymate.celo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttestationCodeUtil {

    public static final String ATTESTATION_URL_REGEX = "(?:celo:\\/\\/wallet\\/v\\/)?([a-zA-Z0-9=\\+\\/_-]{87,88})";
    public static final Pattern ATTESTATION_URL_PATTERN = Pattern.compile(ATTESTATION_URL_REGEX);

    public static final String ATTESTATION_CODE_REGEX = "([0-9]{8})";
    public static final Pattern ATTESTATION_CODE_PATTERN = Pattern.compile(ATTESTATION_CODE_REGEX);

    public static String extractURL(String text) {
        text = text.replaceAll("([¿§])", "_");

        Matcher matcher = ATTESTATION_URL_PATTERN.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    public static String extractCode(String text) {
        Matcher matcher = ATTESTATION_CODE_PATTERN.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

}
