package com.xfusion.fusiondesk.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.fusiondesk.exception.AiAnalysisException;
import com.xfusion.fusiondesk.model.*;
import java.util.Set;

public class AiResponseValidator {
    private static final Set<String> FIELDS=Set.of("category","priority","summary","reason");
    private final ObjectMapper json=new ObjectMapper();
    public AiAnalysisResult validate(String raw){
        if(raw==null||raw.isBlank())throw invalid("LLM returned an empty response.");
        final JsonNode root;try{root=json.readTree(raw);}catch(Exception e){throw invalid("LLM returned an invalid response.");}
        if(!root.isObject()||root.size()!=4||!root.propertyStream().allMatch(e->FIELDS.contains(e.getKey())))throw invalid("LLM returned an invalid response.");
        String category=text(root,"category"),priority=text(root,"priority"),summary=text(root,"summary"),reason=text(root,"reason");
        final TicketCategory typedCategory;try{typedCategory=TicketCategory.valueOf(category);}catch(IllegalArgumentException e){throw invalid("LLM returned an invalid category.");}
        final TicketPriority typedPriority;try{typedPriority=TicketPriority.valueOf(priority);}catch(IllegalArgumentException e){throw invalid("LLM returned an invalid priority.");}
        summary=bounded("summary",summary,200);reason=bounded("reason",reason,500);
        return new AiAnalysisResult(typedCategory,typedPriority,summary,reason);
    }
    private String text(JsonNode root,String field){JsonNode value=root.get(field);if(value==null||!value.isTextual())throw invalid("LLM returned an invalid response.");return value.asText();}
    private String bounded(String field,String value,int max){String clean=value.strip();if(clean.isEmpty()||clean.length()>max)throw invalid("LLM returned an invalid "+field+".");return clean;}
    private AiAnalysisException invalid(String message){return new AiAnalysisException(message);}
}
