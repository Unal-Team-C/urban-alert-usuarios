package com.urbanalert.users.domain.exception;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("Cannot delete or demote the last administrator");
    }
}
