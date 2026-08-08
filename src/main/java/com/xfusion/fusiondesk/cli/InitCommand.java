package com.xfusion.fusiondesk.cli;
import picocli.CommandLine.*;
@Command(name="init",description="Initialize the FusionDesk database.")
public class InitCommand implements Runnable{@ParentCommand FusionDeskCommand root;public void run(){root.database().initializeSchema();System.out.println("FusionDesk database initialized successfully.");}}
