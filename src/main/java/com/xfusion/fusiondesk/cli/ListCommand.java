package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.model.*;import picocli.CommandLine.*;import java.util.List;
@Command(name="list",description="List and filter tickets.")
public class ListCommand implements Runnable{
 @ParentCommand FusionDeskCommand root;@Option(names="--status")TicketStatus status;@Option(names="--category")TicketCategory category;
 @Option(names="--priority")TicketPriority priority;@Option(names="--submitter")String submitter;
 public void run(){List<Ticket> items=root.service().find(new TicketFilter(status,category,priority,submitter));System.out.println("ID | STATUS | CATEGORY | PRIORITY | SUBMITTER | TITLE");
  for(Ticket t:items)System.out.printf("%d | %s | %s | %s | %s | %s%n",t.id(),t.status(),t.category()==null?"-":t.category(),t.priority(),t.submitter(),t.title());
  if(items.isEmpty())System.out.println("No tickets found.");}
}
