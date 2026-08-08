package com.xfusion.fusiondesk.evaluation;
import com.xfusion.fusiondesk.ai.PromptBuilder;import com.xfusion.fusiondesk.exception.ValidationException;import com.xfusion.fusiondesk.model.*;import java.time.Instant;
public class EvaluationPromptFactory {
    private final PromptBuilder production=new PromptBuilder();
    public EvaluationPrompt build(String version,EvaluationCase c){return switch(version){
        case "baseline-v0" -> new EvaluationPrompt("""
            You are an IT ticket triage assistant. Analyze the ticket and return one JSON object with category, priority, summary, and reason.
            category must be one of: ACCOUNT_ACCESS, SOFTWARE_FAILURE, NETWORK, HARDWARE_OFFICE, BUSINESS_SYSTEM, OTHER.
            priority must be one of: P0, P1, P2, P3.
            Return JSON only, without Markdown.
            ""","Ticket title:\n"+c.title()+"\n\nTicket description:\n"+c.description());
        case "v1" -> {Ticket t=new Ticket(0L,c.title(),c.description(),"evaluation",TicketStatus.NEW,null,TicketPriority.P2,0,"evaluation",Instant.EPOCH,Instant.EPOCH);yield new EvaluationPrompt(production.systemPrompt(),production.userPrompt(t));}
        default -> throw new ValidationException("Unsupported evaluation prompt: "+version+". Use baseline-v0 or v1.");};}
    public record EvaluationPrompt(String systemPrompt,String userPrompt){}
}
