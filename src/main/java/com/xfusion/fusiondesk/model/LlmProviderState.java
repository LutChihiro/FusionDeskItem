package com.xfusion.fusiondesk.model;
import java.time.Instant;
public record LlmProviderState(LlmCircuitState state,int consecutiveFailures,int consecutiveSuccesses,Instant lastFailureAt,Instant nextRetryAt,Instant lastSuccessAt,String currentModel,String lastError,long version) { }
