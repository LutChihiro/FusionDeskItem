package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.model.*;import picocli.CommandLine.*;
@Command(name="create",description="Create a ticket.")
public class CreateCommand implements Runnable{
 @ParentCommand FusionDeskCommand root;@Option(names="--title",required=true)String title;@Option(names="--description",required=true)String description;
 @Option(names="--submitter",required=true)String submitter;@Option(names="--priority",required=true)TicketPriority priority;
 public void run(){CreateTicketResult r=root.service().create(title,description,submitter,priority);if(r.duplicate())System.out.printf("Duplicate ticket detected.%n%nExisting ticket ID: %d%n",r.ticket().id());
 else System.out.printf("Ticket created successfully.%n%nID: %d%nStatus: %s%nPriority: %s%n",r.ticket().id(),r.ticket().status(),r.ticket().priority());}
}
