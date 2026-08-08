package com.xfusion.fusiondesk.model;
import java.time.Instant;
public record PromptVersion(long id,String version,String systemPrompt,String userPromptTemplate,PromptStatus status,String sourceModel,Instant createdAt,Instant activatedAt) { }
