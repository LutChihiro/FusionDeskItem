package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.model.AuditEvent;import picocli.CommandLine.*;
@Command(name="audit",description="Show ticket audit history.")
public class AuditCommand implements Runnable{@ParentCommand FusionDeskCommand root;@Parameters(index="0",paramLabel="ID")long id;
 public void run(){System.out.println("TIME | EVENT | BEFORE | AFTER");for(AuditEvent e:root.service().audit(id))System.out.printf("%s | %s | %s | %s%n",e.createdAt(),e.eventType(),e.beforeData()==null?"-":e.beforeData(),e.afterData()==null?"-":e.afterData());}}
