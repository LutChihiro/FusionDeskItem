package com.xfusion.fusiondesk.model;

import java.time.Instant;

public record AuditEvent(Long id, long ticketId, String eventType, String beforeData,
                         String afterData, Instant createdAt) { }
