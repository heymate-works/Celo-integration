package works.heymate.celo;

public enum CeloError {

    NETWORK_ERROR("General network error"),

    CONTRACT_KIT_ERROR("Failed to initialize contractKit"),

    BLINDING_ERROR("Failed to blind the target"),
    ODIS_ERROR("Failed to run the blinded target through ODIS"),
    UNBLINDING_ERROR("Failed to unblind the target"),

    SALTING_ERROR("Failed to get salt"),
    ;

    private String message;

    CeloError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
