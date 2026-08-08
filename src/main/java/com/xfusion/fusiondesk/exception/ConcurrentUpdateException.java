package com.xfusion.fusiondesk.exception;

public class ConcurrentUpdateException extends BusinessException {
    public ConcurrentUpdateException() { super("Ticket was modified by another operation. Please reload and retry."); }
}
