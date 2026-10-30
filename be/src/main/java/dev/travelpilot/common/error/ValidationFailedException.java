package dev.travelpilot.common.error;

import java.util.Map;

/** Business validation failure with per-field messages; rendered as 400 VALIDATION_FAILED with "errors". */
public class ValidationFailedException extends ApiException {

    private final Map<String, String> errors;

    public ValidationFailedException(Map<String, String> errors) {
        super(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), null);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
