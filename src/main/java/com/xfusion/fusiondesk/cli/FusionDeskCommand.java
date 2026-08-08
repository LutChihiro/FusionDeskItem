package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.repository.DatabaseManager;
import com.xfusion.fusiondesk.service.TicketService;
import picocli.CommandLine.Command;

@Command(name="fusiondesk",mixinStandardHelpOptions=true,version="FusionDesk 1.0",
    description="Local collaborative ticket management.",
    subcommands={InitCommand.class,SeedCommand.class,CreateCommand.class,ListCommand.class,ShowCommand.class,TransitionCommand.class,AuditCommand.class})
public class FusionDeskCommand implements Runnable {
    private final DatabaseManager database; private final TicketService service;
    public FusionDeskCommand(DatabaseManager database){this.database=database;this.service=new TicketService(database);}
    public DatabaseManager database(){return database;} public TicketService service(){return service;}
    @Override public void run(){System.out.println("Use --help to see available commands.");}
}
