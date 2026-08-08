package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.model.Ticket;import picocli.CommandLine.*;
@Command(name="show",description="Show ticket details.")
public class ShowCommand implements Runnable{@ParentCommand FusionDeskCommand root;@Parameters(index="0",paramLabel="ID")long id;
 public void run(){Ticket t=root.service().get(id);System.out.printf("ID: %d%nTitle: %s%nDescription: %s%nSubmitter: %s%nStatus: %s%nCategory: %s%nPriority: %s%nVersion: %d%nCreated At: %s%nUpdated At: %s%n",
 t.id(),t.title(),t.description(),t.submitter(),t.status(),t.category()==null?"-":t.category(),t.priority(),t.version(),t.createdAt(),t.updatedAt());}}
