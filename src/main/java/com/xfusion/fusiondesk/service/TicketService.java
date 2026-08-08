package com.xfusion.fusiondesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xfusion.fusiondesk.exception.*;
import com.xfusion.fusiondesk.model.*;
import com.xfusion.fusiondesk.repository.*;
import com.xfusion.fusiondesk.util.DedupKeyGenerator;

import java.time.Instant;
import java.util.List;

public class TicketService {
    public static final int TITLE_MAX_LENGTH=200, DESCRIPTION_MAX_LENGTH=5000, SUBMITTER_MAX_LENGTH=100;
    private final DatabaseManager database; private final TicketRepository tickets; private final AuditRepository audits;
    private final ObjectMapper json=new ObjectMapper();

    public TicketService(DatabaseManager database){this(database,new TicketRepository(database),new AuditRepository(database));}
    public TicketService(DatabaseManager database,TicketRepository tickets,AuditRepository audits){this.database=database;this.tickets=tickets;this.audits=audits;}

    public CreateTicketResult create(String title,String description,String submitter,TicketPriority priority){
        return create(title,description,submitter,priority,null);
    }
    public CreateTicketResult create(String title,String description,String submitter,TicketPriority priority,TicketCategory category){
        String cleanTitle=validateText("Title",title,TITLE_MAX_LENGTH), cleanDescription=validateText("Description",description,DESCRIPTION_MAX_LENGTH),
            cleanSubmitter=validateText("Submitter",submitter,SUBMITTER_MAX_LENGTH);
        if(priority==null)throw new ValidationException("Priority must be one of P0, P1, P2, P3.");
        String key=DedupKeyGenerator.generate(cleanSubmitter,cleanTitle,cleanDescription);
        var existing=tickets.findActiveByDedupKey(key); if(existing.isPresent())return new CreateTicketResult(existing.get(),true);
        try{
            return database.inTransaction(c->{
                var inside=tickets.findActiveByDedupKey(c,key); if(inside.isPresent())return new CreateTicketResult(inside.get(),true);
                Instant now=Instant.now(); Ticket ticket=tickets.insert(c,cleanTitle,cleanDescription,cleanSubmitter,category,priority,key,now);
                audits.insert(c,ticket.id(),"CREATED",null,ticketJson(ticket),now); return new CreateTicketResult(ticket,false);
            });
        }catch(DatabaseException e){
            if(database.isDuplicateKey(e)){var raced=tickets.findActiveByDedupKey(key);if(raced.isPresent())return new CreateTicketResult(raced.get(),true);}
            throw e;
        }
    }
    public Ticket get(long id){return tickets.findById(id).orElseThrow(()->new TicketNotFoundException(id));}
    public List<Ticket> find(TicketFilter filter){return tickets.find(filter);}
    public List<AuditEvent> audit(long id){get(id);return audits.findByTicketId(id);}

    public Ticket transition(long id,TicketStatus target,long expectedVersion){
        if(target==null)throw new ValidationException("Target status is required.");
        if(expectedVersion<0)throw new ValidationException("Version must not be negative.");
        return database.inTransaction(c->{
            Ticket before=tickets.findById(c,id).orElseThrow(()->new TicketNotFoundException(id));
            if(before.version()!=expectedVersion)throw new ConcurrentUpdateException();
            if(!allowed(before.status(),target))throw new ValidationException("Invalid status transition: "+before.status()+" -> "+target);
            Instant now=Instant.now(); int changed=tickets.updateStatusWithVersion(c,id,target,expectedVersion,now);
            if(changed==0){if(tickets.findById(c,id).isEmpty())throw new TicketNotFoundException(id);throw new ConcurrentUpdateException();}
            Ticket after=new Ticket(before.id(),before.title(),before.description(),before.submitter(),target,before.category(),before.priority(),before.version()+1,before.dedupKey(),before.createdAt(),now);
            audits.insert(c,id,"STATUS_CHANGED",statusJson(before.status(),before.version()),statusJson(after.status(),after.version()),now);return after;
        });
    }
    private boolean allowed(TicketStatus from,TicketStatus to){return switch(from){
        case NEW -> to==TicketStatus.IN_PROGRESS; case IN_PROGRESS -> to==TicketStatus.RESOLVED;
        case RESOLVED -> to==TicketStatus.CLOSED||to==TicketStatus.IN_PROGRESS; case CLOSED -> false;};}
    private String validateText(String field,String value,int max){if(value==null||value.strip().isEmpty())throw new ValidationException(field+" must not be blank.");
        String stripped=value.strip();if(stripped.length()>max)throw new ValidationException(field+" must not exceed "+max+" characters.");return stripped;}
    private String ticketJson(Ticket t){ObjectNode n=json.createObjectNode();n.put("id",t.id());n.put("title",t.title());n.put("description",t.description());n.put("submitter",t.submitter());n.put("status",t.status().name());
        if(t.category()==null)n.putNull("category");else n.put("category",t.category().name());n.put("priority",t.priority().name());n.put("version",t.version());n.put("createdAt",t.createdAt().toString());return write(n);}
    private String statusJson(TicketStatus status,long version){ObjectNode n=json.createObjectNode();n.put("status",status.name());n.put("version",version);return write(n);}
    private String write(ObjectNode node){try{return json.writeValueAsString(node);}catch(JsonProcessingException e){throw new BusinessException("Failed to serialize audit data",e);}}
}
