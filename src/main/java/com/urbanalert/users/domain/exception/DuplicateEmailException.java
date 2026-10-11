package com.urbanalert.users.domain.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("The email address is already registered: " + email);
    }
}
