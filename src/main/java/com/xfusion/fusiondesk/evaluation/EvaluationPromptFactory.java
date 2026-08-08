package com.xfusion.fusiondesk.evaluation;
import com.xfusion.fusiondesk.ai.PromptBuilder;import com.xfusion.fusiondesk.exception.ValidationException;import com.xfusion.fusiondesk.model.*;import java.time.Instant;
public class EvaluationPromptFactory {
    private final PromptBuilder production=new PromptBuilder();
    public EvaluationPrompt build(String version,EvaluationCase c){return switch(version){
        case "baseline-v0" -> new EvaluationPrompt(production.systemPromptBaseline(),production.userPromptTemplateBaseline().formatted(c.title(),c.description()));
        case "v1", "v2", "v3" -> {Ticket t=new Ticket(0L,c.title(),c.description(),"evaluation",TicketStatus.NEW,null,TicketPriority.P2,0,"evaluation",Instant.EPOCH,Instant.EPOCH);yield switch(version){case "v1"->new EvaluationPrompt(production.systemPromptV1(),production.userPromptV1(t));case "v2"->new EvaluationPrompt(production.systemPromptV2(),production.userPromptV1(t));default->new EvaluationPrompt(production.systemPrompt(),production.userPrompt(t));};}
        default -> throw new ValidationException("Unsupported evaluation prompt: "+version+". Use baseline-v0, v1, v2 or v3.");};}
    public record EvaluationPrompt(String systemPrompt,String userPrompt){}
}
