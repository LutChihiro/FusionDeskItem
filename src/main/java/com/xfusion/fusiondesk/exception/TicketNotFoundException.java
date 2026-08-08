package com.xfusion.fusiondesk.exception;

public class TicketNotFoundException extends BusinessException {
    public TicketNotFoundException(long id) { super("Ticket not found: " + id); }
}
