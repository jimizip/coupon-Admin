package com.example.coupon_admin.validator;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;

    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
}
