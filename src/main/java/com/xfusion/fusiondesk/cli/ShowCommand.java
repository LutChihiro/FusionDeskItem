package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.model.*;import picocli.CommandLine.*;import java.util.List;
@Command(name="show",description="Show ticket details.")
public class ShowCommand implements Runnable{@ParentCommand FusionDeskCommand root;@Parameters(index="0",paramLabel="ID")long id;
 public void run(){Ticket t=root.service().get(id);System.out.printf("ID: %d%nTitle: %s%nDescription: %s%nSubmitter: %s%nStatus: %s%nCategory: %s%nPriority: %s%nVersion: %d%nCreated At: %s%nUpdated At: %s%n",
 t.id(),t.title(),t.description(),t.submitter(),t.status(),t.category()==null?"-":t.category(),t.priority(),t.version(),t.createdAt(),t.updatedAt());
 List<AiSuggestion> items=root.suggestions().findByTicketId(id);if(items.isEmpty()){System.out.printf("%nAI Suggestions: None%n");return;}System.out.printf("%nAI Suggestions (latest first):%n");
 for(AiSuggestion s:items)System.out.printf("%nSuggestion ID: %d%nSuggested Category: %s%nSuggested Priority: %s%nSummary: %s%nReason: %s%nModel: %s%nPrompt Version: %s%nStatus: %s%nFinal Category: %s%nFinal Priority: %s%nCreated At: %s%nReviewed At: %s%n",s.id(),s.suggestedCategory(),s.suggestedPriority(),s.summary(),s.reason(),s.model(),s.promptVersion(),s.status(),s.finalCategory()==null?"-":s.finalCategory(),s.finalPriority()==null?"-":s.finalPriority(),s.createdAt(),s.reviewedAt()==null?"-":s.reviewedAt());}}
