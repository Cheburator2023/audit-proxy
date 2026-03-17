package ru.vtb.auditproxy.exception;

public class AuditSendException extends RuntimeException {
    public AuditSendException(String message, Throwable cause) {
        super(message, cause);
    }
}