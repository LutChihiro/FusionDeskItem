package com.xfusion.fusiondesk.model;
public record PromptTrainingSample(long suggestionId,String label,String title,String description,TicketCategory suggestedCategory,TicketPriority suggestedPriority,TicketCategory expectedCategory,TicketPriority expectedPriority) { }
