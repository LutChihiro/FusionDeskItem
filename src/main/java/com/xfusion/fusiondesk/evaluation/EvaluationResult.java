package com.xfusion.fusiondesk.evaluation;
import com.xfusion.fusiondesk.model.*;
public record EvaluationResult(String caseId,TicketCategory expectedCategory,TicketCategory predictedCategory,TicketPriority expectedPriority,TicketPriority predictedPriority,boolean schemaValid,boolean categoryCorrect,boolean priorityCorrect,boolean adversarial,Boolean injectionPassed,String summary,String reason,String error) { }
