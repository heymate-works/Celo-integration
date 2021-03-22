package works.heymate.celo;

public enum CeloError {

    NETWORK_ERROR("General network error"),

    CONTRACT_KIT_ERROR("Failed to initialize contractKit"),

    BLINDING_ERROR("Failed to blind the target"),
    ODIS_ERROR("Failed to run the blinded target through ODIS"),
    UNBLINDING_ERROR("Failed to unblind the target"),

    SALTING_ERROR("Failed to get salt"),

    ATTESTATION_STATUS("Failed to get attestation status"),
    ATTESTATION_VERIFICATION_STATUS("Failed to get verification status"),
    ATTESTATION_ACTIONABLES("Failed to get actionable attestations"),
    CANT_VERIFY_REVOKED_ACCOUNT("Account association with the phone number has been revoked"),
    MAX_ACTIONABLE_ATTESTATIONS_EXCEEDED("Can't have too many attestations at the same time"),
    ATTESTATION_SLOW_BLOCKS("Timed out while waiting for enough blocks before calling selectIssuers"),

    ATTESTATION_CODE_USED("Attestation code is already used"),
    BAD_ATTESTATION_CODE("Invalid attestation code format"),
    INVALID_ATTESTATION_CODE("Invalid attestation code"),

    INSUFFICIENT_BALANCE("Insufficient balance to perform the operation"),
    ;

    private String message;

    CeloError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
