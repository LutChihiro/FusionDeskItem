package com.xfusion.fusiondesk.evaluation;
import com.xfusion.fusiondesk.model.*;
public record EvaluationCase(String id,String title,String description,TicketCategory expectedCategory,TicketPriority expectedPriority,boolean adversarial,String note) { }
