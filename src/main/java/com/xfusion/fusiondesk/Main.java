package com.xfusion.fusiondesk;

import com.xfusion.fusiondesk.cli.FusionDeskCommand;
import com.xfusion.fusiondesk.exception.BusinessException;
import com.xfusion.fusiondesk.repository.DatabaseManager;
import picocli.CommandLine;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        DatabaseManager database=DatabaseManager.defaultDatabase();
        try{database.initializeSchema();}catch(BusinessException e){System.err.println("Error: "+e.getMessage());System.exit(1);return;}
        CommandLine cli=new CommandLine(new FusionDeskCommand(database));
        cli.setExecutionExceptionHandler((ex,cmd,parse)->{if(ex instanceof BusinessException)cmd.getErr().println("Error: "+ex.getMessage());else cmd.getErr().println("Unexpected system error.");return 1;});
        cli.setParameterExceptionHandler((ex,args1)->{ex.getCommandLine().getErr().println("Error: "+ex.getMessage());return 2;});
        System.exit(cli.execute(args));
    }
}
