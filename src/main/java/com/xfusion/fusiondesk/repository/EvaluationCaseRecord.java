package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.model.TicketCategory;
import com.xfusion.fusiondesk.model.TicketPriority;

public record EvaluationCaseRecord(long id, long runId, String caseId,
                                   TicketCategory expectedCategory, TicketCategory predictedCategory,
                                   TicketPriority expectedPriority, TicketPriority predictedPriority,
                                   boolean schemaValid, boolean categoryCorrect, boolean priorityCorrect,
                                   boolean exactMatch, boolean adversarial, Boolean injectionPassed,
                                   String summary, String reason, String error) { }
