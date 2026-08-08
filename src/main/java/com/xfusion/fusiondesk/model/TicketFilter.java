package com.xfusion.fusiondesk.model;

public record TicketFilter(TicketStatus status, TicketCategory category,
                           TicketPriority priority, String submitter) { }
