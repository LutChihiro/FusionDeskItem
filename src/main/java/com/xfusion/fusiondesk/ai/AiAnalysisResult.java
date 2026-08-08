package com.xfusion.fusiondesk.ai;

import com.xfusion.fusiondesk.model.TicketCategory;
import com.xfusion.fusiondesk.model.TicketPriority;

public record AiAnalysisResult(TicketCategory category, TicketPriority priority,
                               String summary, String reason) { }
