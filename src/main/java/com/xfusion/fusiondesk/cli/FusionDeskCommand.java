package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.repository.DatabaseManager;
import com.xfusion.fusiondesk.repository.AiSuggestionRepository;
import com.xfusion.fusiondesk.ai.LlmProvider;
import com.xfusion.fusiondesk.service.AiTriageService;
import com.xfusion.fusiondesk.service.TicketService;
import com.xfusion.fusiondesk.service.ReviewService;
import picocli.CommandLine.Command;

@Command(name="fusiondesk",mixinStandardHelpOptions=true,version="FusionDesk 1.0",
    description="Local collaborative ticket management.",
    subcommands={InitCommand.class,SeedCommand.class,CreateCommand.class,ListCommand.class,ShowCommand.class,TransitionCommand.class,AuditCommand.class,AnalyzeCommand.class,ReviewCommand.class,EvaluateCommand.class})
public class FusionDeskCommand implements Runnable {
    private final DatabaseManager database; private final TicketService service; private final AiSuggestionRepository suggestions;
    public FusionDeskCommand(DatabaseManager database){this.database=database;this.service=new TicketService(database);this.suggestions=new AiSuggestionRepository(database);}
    public DatabaseManager database(){return database;} public TicketService service(){return service;} public AiSuggestionRepository suggestions(){return suggestions;}
    public AiTriageService aiService(LlmProvider provider){return new AiTriageService(database,provider);}
    public ReviewService reviewService(){return new ReviewService(database);}
    @Override public void run(){System.out.println("Use --help to see available commands.");}
}
