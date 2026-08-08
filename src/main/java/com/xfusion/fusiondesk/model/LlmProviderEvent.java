package com.xfusion.fusiondesk.model;

import java.time.Instant;

/** Immutable history entry for model failover and recovery monitoring. */
public record LlmProviderEvent(
        Long id,
        String eventType,
        String source,
        LlmCircuitState fromState,
        LlmCircuitState toState,
        String primaryModel,
        String activeModel,
        String errorMessage,
        Instant createdAt) {
}
