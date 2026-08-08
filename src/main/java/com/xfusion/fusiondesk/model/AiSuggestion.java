package com.xfusion.fusiondesk.model;

import java.time.Instant;

public record AiSuggestion(Long id, Long ticketId, TicketCategory suggestedCategory,
                           TicketPriority suggestedPriority, String summary, String reason,
                           String rawResponse, String model, String promptVersion,
                           SuggestionStatus status, TicketCategory finalCategory,
                           TicketPriority finalPriority, Instant createdAt, Instant reviewedAt) { }
