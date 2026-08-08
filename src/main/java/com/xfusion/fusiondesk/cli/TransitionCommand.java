package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.model.*;import picocli.CommandLine.*;
@Command(name="transition",description="Transition a ticket status using optimistic locking.")
public class TransitionCommand implements Runnable{@ParentCommand FusionDeskCommand root;@Parameters(index="0",paramLabel="ID")long id;
 @Option(names="--to",required=true)TicketStatus to;@Option(names="--version",required=true)long version;
 public void run(){Ticket before=root.service().get(id),after=root.service().transition(id,to,version);System.out.printf("Ticket status updated.%n%n%s -> %s%n%nVersion: %d%n",before.status(),after.status(),after.version());}}
