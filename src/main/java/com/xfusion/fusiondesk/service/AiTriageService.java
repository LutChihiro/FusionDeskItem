package com.xfusion.fusiondesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xfusion.fusiondesk.ai.*;
import com.xfusion.fusiondesk.exception.*;
import com.xfusion.fusiondesk.model.*;
import com.xfusion.fusiondesk.repository.*;

import java.time.Instant;

public class AiTriageService {
    private final DatabaseManager database;private final TicketRepository tickets;private final AiSuggestionRepository suggestions;
    private final AuditRepository audits;private final PromptBuilder prompts;private final PromptVersionRepository promptVersions;private final AiResponseValidator validator;private final LlmProvider provider;
    private final ObjectMapper json=new ObjectMapper();
    public AiTriageService(DatabaseManager database,LlmProvider provider){this(database,new TicketRepository(database),new AiSuggestionRepository(database),new AuditRepository(database),new PromptBuilder(),new PromptVersionRepository(database),new AiResponseValidator(),provider);}
    public AiTriageService(DatabaseManager database,TicketRepository tickets,AiSuggestionRepository suggestions,AuditRepository audits,PromptBuilder prompts,PromptVersionRepository promptVersions,AiResponseValidator validator,LlmProvider provider){this.database=database;this.tickets=tickets;this.suggestions=suggestions;this.audits=audits;this.prompts=prompts;this.promptVersions=promptVersions;this.validator=validator;this.provider=provider;}
    public AiSuggestion analyze(long ticketId){
        Ticket ticket=tickets.findById(ticketId).orElseThrow(()->new TicketNotFoundException(ticketId));
        PromptVersion active=promptVersions.findActive();LlmResponse response=provider.complete(active.systemPrompt(),prompts.userPrompt(ticket));
        if(response==null||response.content()==null||response.model()==null||response.model().isBlank())throw new AiAnalysisException("LLM returned an invalid response.");
        AiAnalysisResult result=validator.validate(response.content());
        return database.inTransaction(c->{Instant now=Instant.now();AiSuggestion suggestion=suggestions.insert(c,ticketId,result,response.content(),response.model(),active.version(),now);
            audits.insert(c,ticketId,"AI_ANALYZED",null,auditJson(suggestion),now);return suggestion;});
    }
    private String auditJson(AiSuggestion s){ObjectNode n=json.createObjectNode();n.put("suggestionId",s.id());n.put("category",s.suggestedCategory().name());n.put("priority",s.suggestedPriority().name());n.put("status",s.status().name());n.put("model",s.model());try{return json.writeValueAsString(n);}catch(JsonProcessingException e){throw new AiAnalysisException("Failed to serialize AI audit data.",e);}}
}
