package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.ai.LlmProviderSet;
import com.xfusion.fusiondesk.config.AppConfig;
import com.xfusion.fusiondesk.exception.AiAnalysisException;
import com.xfusion.fusiondesk.model.AiSuggestion;
import picocli.CommandLine.*;
import java.util.concurrent.Callable;

@Command(name="analyze",description="Generate a pending AI triage suggestion.")
public class AnalyzeCommand implements Callable<Integer> {
    @ParentCommand FusionDeskCommand root;@Parameters(index="0",paramLabel="TICKET_ID")long ticketId;
    @Override public Integer call(){
        try{LlmProviderSet providers=LlmProviderSet.fromConfig(AppConfig.current());AiSuggestion s=root.aiService(providers.primary(),providers.fallback()).analyze(ticketId);
            System.out.printf("AI suggestion generated successfully.%n%nSuggestion ID: %d%nTicket ID: %d%n%nCategory: %s%nPriority: %s%nSummary: %s%nReason: %s%n%nModel: %s%nPrompt Version: %s%nStatus: %s%n%nThis is an AI suggestion only.%nIt has NOT been applied to the ticket.%n",s.id(),s.ticketId(),s.suggestedCategory(),s.suggestedPriority(),s.summary(),s.reason(),s.model(),s.promptVersion(),s.status());return 0;
        }catch(AiAnalysisException e){System.err.printf("AI analysis failed: %s%n%nThe ticket has not been modified.%nCore ticket functions remain available.%n",e.getMessage());return 1;}
    }
}
