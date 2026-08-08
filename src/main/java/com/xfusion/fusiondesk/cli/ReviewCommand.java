package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.exception.ValidationException;
import com.xfusion.fusiondesk.model.*;
import picocli.CommandLine.*;

@Command(name="review",description="Confirm, modify, or reject one pending AI suggestion.")
public class ReviewCommand implements Runnable {
    @ParentCommand FusionDeskCommand root;
    @Parameters(index="0",paramLabel="SUGGESTION_ID")long suggestionId;
    @Parameters(index="1",paramLabel="ACTION",description="confirm, modify, or reject")String action;
    @Option(names="--version")Long version;
    @Option(names="--category")TicketCategory category;
    @Option(names="--priority")TicketPriority priority;
    @Override public void run(){switch(action.toLowerCase(java.util.Locale.ROOT)){
        case "confirm" -> printConfirm(root.reviewService().confirm(suggestionId,requiredVersion()));
        case "modify" -> printModify(root.reviewService().modify(suggestionId,category,priority,requiredVersion()));
        case "reject" -> printReject(root.reviewService().reject(suggestionId));
        default -> throw new ValidationException("Review action must be confirm, modify, or reject.");
    }}
    private long requiredVersion(){if(version==null)throw new ValidationException("--version is required for confirm and modify.");return version;}
    private void printConfirm(ReviewResult r){AiSuggestion s=r.suggestion();System.out.printf("AI suggestion confirmed.%n%nSuggestion ID: %d%nTicket ID: %d%n%nAI Category: %s%nAI Priority: %s%n%nFinal Category: %s%nFinal Priority: %s%n%nSuggestion Status: %s%nTicket Version: %d%n",s.id(),s.ticketId(),s.suggestedCategory(),s.suggestedPriority(),s.finalCategory(),s.finalPriority(),s.status(),r.ticketAfter().version());}
    private void printModify(ReviewResult r){AiSuggestion s=r.suggestion();System.out.printf("AI suggestion modified and applied.%n%nSuggestion ID: %d%nTicket ID: %d%n%nAI Suggestion:%nCategory: %s%nPriority: %s%n%nHuman Decision:%nCategory: %s%nPriority: %s%n%nSuggestion Status: %s%nTicket Version: %d%n",s.id(),s.ticketId(),s.suggestedCategory(),s.suggestedPriority(),s.finalCategory(),s.finalPriority(),s.status(),r.ticketAfter().version());}
    private void printReject(ReviewResult r){AiSuggestion s=r.suggestion();System.out.printf("AI suggestion rejected.%n%nSuggestion ID: %d%nTicket ID: %d%n%nAI Category: %s%nAI Priority: %s%n%nSuggestion Status: %s%n%nThe ticket was not modified.%n",s.id(),s.ticketId(),s.suggestedCategory(),s.suggestedPriority(),s.status());}
}
