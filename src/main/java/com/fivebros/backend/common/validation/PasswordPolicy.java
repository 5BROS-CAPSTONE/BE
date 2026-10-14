package com.fivebros.backend.common.validation;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_UTF8_BYTES = 72;
    public static final String VALIDATION_MESSAGE = "Password must contain at least " + MIN_LENGTH
            + " characters and at most " + MAX_UTF8_BYTES + " UTF-8 bytes";
    public static final String DESCRIPTION = "Ít nhất " + MIN_LENGTH + " ký tự, tối đa " + MAX_UTF8_BYTES + " byte UTF-8.";

    private PasswordPolicy() {
    }
}
