package com.xfusion.fusiondesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xfusion.fusiondesk.exception.*;
import com.xfusion.fusiondesk.model.*;
import com.xfusion.fusiondesk.repository.*;
import java.time.Instant;

public class ReviewService {
    private final DatabaseManager database;private final TicketRepository tickets;private final AiSuggestionRepository suggestions;private final AuditRepository audits;private final ObjectMapper json=new ObjectMapper();
    public ReviewService(DatabaseManager database){this(database,new TicketRepository(database),new AiSuggestionRepository(database),new AuditRepository(database));}
    public ReviewService(DatabaseManager database,TicketRepository tickets,AiSuggestionRepository suggestions,AuditRepository audits){this.database=database;this.tickets=tickets;this.suggestions=suggestions;this.audits=audits;}
    public ReviewResult confirm(long suggestionId,long expectedTicketVersion){return apply(suggestionId,null,null,expectedTicketVersion,false);}
    public ReviewResult modify(long suggestionId,TicketCategory category,TicketPriority priority,long expectedTicketVersion){if(category==null)throw new ValidationException("Final category is required.");if(priority==null)throw new ValidationException("Final priority is required.");return apply(suggestionId,category,priority,expectedTicketVersion,true);}
    public ReviewResult reject(long suggestionId){return database.inTransaction(c->{AiSuggestion before=requirePending(c,suggestionId);Ticket ticket=tickets.findById(c,before.ticketId()).orElseThrow(()->new TicketNotFoundException(before.ticketId()));Instant now=Instant.now();
        if(suggestions.markReviewed(c,suggestionId,SuggestionStatus.REJECTED,null,null,now)==0)throw alreadyReviewed();AiSuggestion after=reviewed(before,SuggestionStatus.REJECTED,null,null,now);audits.insert(c,ticket.id(),"AI_REJECTED",suggestionState(before),suggestionState(after),now);return new ReviewResult(after,ticket,ticket);});}
    private ReviewResult apply(long suggestionId,TicketCategory category,TicketPriority priority,long expectedVersion,boolean modified){if(expectedVersion<0)throw new ValidationException("Version must not be negative.");return database.inTransaction(c->{AiSuggestion before=requirePending(c,suggestionId);Ticket ticket=tickets.findById(c,before.ticketId()).orElseThrow(()->new TicketNotFoundException(before.ticketId()));if(ticket.version()!=expectedVersion)throw new ConcurrentUpdateException();
        TicketCategory finalCategory=modified?category:before.suggestedCategory();TicketPriority finalPriority=modified?priority:before.suggestedPriority();Instant now=Instant.now();if(tickets.updateClassificationWithVersion(c,ticket.id(),finalCategory,finalPriority,expectedVersion,now)==0)throw new ConcurrentUpdateException();SuggestionStatus status=modified?SuggestionStatus.MODIFIED:SuggestionStatus.CONFIRMED;
        if(suggestions.markReviewed(c,suggestionId,status,finalCategory,finalPriority,now)==0)throw alreadyReviewed();AiSuggestion after=reviewed(before,status,finalCategory,finalPriority,now);Ticket updated=new Ticket(ticket.id(),ticket.title(),ticket.description(),ticket.submitter(),ticket.status(),finalCategory,finalPriority,ticket.version()+1,ticket.dedupKey(),ticket.createdAt(),now);
        audits.insert(c,ticket.id(),modified?"AI_MODIFIED":"AI_CONFIRMED",reviewBefore(ticket,before),reviewAfter(updated,after),now);return new ReviewResult(after,ticket,updated);});}
    private AiSuggestion requirePending(java.sql.Connection c,long id)throws java.sql.SQLException{AiSuggestion s=suggestions.findById(c,id).orElseThrow(()->new BusinessException("AI suggestion not found: "+id));if(s.status()!=SuggestionStatus.PENDING)throw alreadyReviewed();return s;}
    private BusinessException alreadyReviewed(){return new BusinessException("Suggestion has already been reviewed.");}
    private AiSuggestion reviewed(AiSuggestion s,SuggestionStatus status,TicketCategory category,TicketPriority priority,Instant at){return new AiSuggestion(s.id(),s.ticketId(),s.suggestedCategory(),s.suggestedPriority(),s.summary(),s.reason(),s.rawResponse(),s.model(),s.promptVersion(),status,category,priority,s.createdAt(),at);}
    private String reviewBefore(Ticket t,AiSuggestion s){ObjectNode n=json.createObjectNode();putCategory(n,"category",t.category());n.put("priority",t.priority().name());n.put("version",t.version());n.put("suggestionId",s.id());n.put("suggestedCategory",s.suggestedCategory().name());n.put("suggestedPriority",s.suggestedPriority().name());return write(n);}
    private String reviewAfter(Ticket t,AiSuggestion s){ObjectNode n=json.createObjectNode();n.put("category",t.category().name());n.put("priority",t.priority().name());n.put("version",t.version());n.put("suggestionId",s.id());n.put("suggestedCategory",s.suggestedCategory().name());n.put("suggestedPriority",s.suggestedPriority().name());n.put("finalCategory",s.finalCategory().name());n.put("finalPriority",s.finalPriority().name());n.put("status",s.status().name());return write(n);}
    private String suggestionState(AiSuggestion s){ObjectNode n=json.createObjectNode();n.put("suggestionId",s.id());n.put("status",s.status().name());n.put("suggestedCategory",s.suggestedCategory().name());n.put("suggestedPriority",s.suggestedPriority().name());return write(n);}
    private void putCategory(ObjectNode n,String field,TicketCategory category){if(category==null)n.putNull(field);else n.put(field,category.name());}
    private String write(ObjectNode n){try{return json.writeValueAsString(n);}catch(JsonProcessingException e){throw new BusinessException("Failed to serialize review audit data",e);}}
}
