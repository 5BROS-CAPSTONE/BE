package com.fivebros.backend.common.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Required password fields also use @NotBlank.
        return value == null
                || (!value.isBlank()
                        && value.codePointCount(0, value.length()) >= PasswordPolicy.MIN_LENGTH
                        && value.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_UTF8_BYTES);
    }
}
