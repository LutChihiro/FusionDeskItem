package com.xfusion.fusiondesk.model;

import java.time.Instant;

public record Ticket(Long id, String title, String description, String submitter,
                     TicketStatus status, TicketCategory category, TicketPriority priority,
                     long version, String dedupKey, Instant createdAt, Instant updatedAt) { }
