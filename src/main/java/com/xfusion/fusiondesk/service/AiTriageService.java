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
    private final AuditRepository audits;private final PromptBuilder prompts;private final PromptVersionRepository promptVersions;private final LlmProviderStateRepository providerStates;private final LlmFailoverPolicy failoverPolicy;private final AiResponseValidator validator;private final LlmProvider primaryProvider;private final LlmProvider fallbackProvider;private final String primaryModel;private final String fallbackModel;
    private final ObjectMapper json=new ObjectMapper();
    public AiTriageService(DatabaseManager database,LlmProvider provider){this(database,provider,null);}
    public AiTriageService(DatabaseManager database,LlmProvider primary,LlmProvider fallback){this(database,primary,fallback,"primary","fallback",LlmFailoverPolicy.defaults());}
    public AiTriageService(DatabaseManager database,LlmProvider primary,LlmProvider fallback,String primaryModel,String fallbackModel,LlmFailoverPolicy policy){this(database,new TicketRepository(database),new AiSuggestionRepository(database),new AuditRepository(database),new PromptBuilder(),new PromptVersionRepository(database),new LlmProviderStateRepository(database),policy,new AiResponseValidator(),primary,fallback,primaryModel,fallbackModel);}
    public AiTriageService(DatabaseManager database,TicketRepository tickets,AiSuggestionRepository suggestions,AuditRepository audits,PromptBuilder prompts,PromptVersionRepository promptVersions,LlmProviderStateRepository providerStates,LlmFailoverPolicy policy,AiResponseValidator validator,LlmProvider primary,LlmProvider fallback,String primaryModel,String fallbackModel){this.database=database;this.tickets=tickets;this.suggestions=suggestions;this.audits=audits;this.prompts=prompts;this.promptVersions=promptVersions;this.providerStates=providerStates;this.failoverPolicy=policy;this.validator=validator;this.primaryProvider=primary;this.fallbackProvider=fallback;this.primaryModel=primaryModel;this.fallbackModel=fallbackModel;}
    public AiSuggestion analyze(long ticketId){
        Ticket ticket=tickets.findById(ticketId).orElseThrow(()->new TicketNotFoundException(ticketId));
        PromptVersion active=promptVersions.findActive();String userPrompt=prompts.userPrompt(ticket);Attempt attempt;if(providerStates.claimPrimaryProbe(Instant.now(),failoverPolicy)){attempt=analyzeWith(primaryProvider,active.systemPrompt(),userPrompt,false);if(attempt.failure()==null)providerStates.recordSuccess(attempt.response().model(),Instant.now(),failoverPolicy);else providerStates.recordFailure(primaryModel,safe(attempt.failure()),Instant.now(),failoverPolicy);}else attempt=new Attempt(null,null,false,new AiAnalysisException("Primary LLM circuit is open."));if(attempt.failure()!=null&&fallbackProvider!=null){attempt=analyzeWith(fallbackProvider,active.systemPrompt(),userPrompt,true);if(attempt.failure()==null)providerStates.recordFallbackUse(attempt.response().model());}if(attempt.failure()!=null)throw combined(attempt.failure());LlmResponse response=attempt.response();AiAnalysisResult result=attempt.result();boolean fallbackUsed=attempt.fallbackUsed();
        return database.inTransaction(c->{Instant now=Instant.now();AiSuggestion suggestion=suggestions.insert(c,ticketId,result,response.content(),response.model(),active.version(),now);
            audits.insert(c,ticketId,"AI_ANALYZED",null,auditJson(suggestion,fallbackUsed),now);return suggestion;});
    }
    private Attempt analyzeWith(LlmProvider provider,String system,String user,boolean fallback){try{LlmResponse response=provider.complete(system,user);if(response==null||response.content()==null||response.model()==null||response.model().isBlank())throw new AiAnalysisException("LLM returned an invalid response.");return new Attempt(response,validator.validate(response.content()),fallback,null);}catch(RuntimeException e){return new Attempt(null,null,fallback,e);}}
    private AiAnalysisException combined(RuntimeException finalFailure){String message=fallbackProvider==null?safe(finalFailure):"Primary and fallback LLM analysis both failed. Last error: "+safe(finalFailure);return new AiAnalysisException(message,finalFailure);}
    private String safe(Throwable e){String m=e.getMessage();return m==null||m.isBlank()?"LLM analysis failed.":m;}
    private record Attempt(LlmResponse response,AiAnalysisResult result,boolean fallbackUsed,RuntimeException failure){}
    private String auditJson(AiSuggestion s,boolean fallbackUsed){ObjectNode n=json.createObjectNode();n.put("suggestionId",s.id());n.put("category",s.suggestedCategory().name());n.put("priority",s.suggestedPriority().name());n.put("status",s.status().name());n.put("model",s.model());n.put("fallbackUsed",fallbackUsed);try{return json.writeValueAsString(n);}catch(JsonProcessingException e){throw new AiAnalysisException("Failed to serialize AI audit data.",e);}}
}
