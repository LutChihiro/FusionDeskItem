package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.model.*;import picocli.CommandLine.*;import java.util.List;
@Command(name="seed",description="Create idempotent demonstration tickets.")
public class SeedCommand implements Runnable{
 @ParentCommand FusionDeskCommand root;
 private record Seed(String title,String description,String submitter,TicketCategory category,TicketPriority priority,TicketStatus target){}
 public void run(){List<Seed> seeds=List.of(
  new Seed("VPN 无法连接","连接公司 VPN 时提示认证失败","seed-network",TicketCategory.NETWORK,TicketPriority.P1,TicketStatus.NEW),
  new Seed("新员工申请 Git 权限","新员工需要代码仓库访问权限","seed-account",TicketCategory.ACCOUNT_ACCESS,TicketPriority.P2,TicketStatus.IN_PROGRESS),
  new Seed("Office 启动闪退","Office 启动后立即退出","seed-software",TicketCategory.SOFTWARE_FAILURE,TicketPriority.P2,TicketStatus.RESOLVED),
  new Seed("三楼打印机没墨","三楼公共打印机需要更换墨盒","seed-hardware",TicketCategory.HARDWARE_OFFICE,TicketPriority.P3,TicketStatus.NEW),
  new Seed("CRM 页面返回 500","销售打开 CRM 客户页面返回 500","seed-business",TicketCategory.BUSINESS_SYSTEM,TicketPriority.P0,TicketStatus.CLOSED));
  int created=0;for(int i=0;i<seeds.size();i++){String marker="seed.v1."+i;if(root.database().metadataExists(marker))continue;Seed s=seeds.get(i);
   CreateTicketResult result=root.service().create(s.title(),s.description(),s.submitter(),s.priority(),s.category());Ticket t=result.ticket();
   while(t.status()!=s.target()){TicketStatus next=switch(t.status()){case NEW->TicketStatus.IN_PROGRESS;case IN_PROGRESS->TicketStatus.RESOLVED;case RESOLVED->TicketStatus.CLOSED;case CLOSED->throw new IllegalStateException("Closed seed cannot move");};t=root.service().transition(t.id(),next,t.version());}
   root.database().putMetadata(marker,Long.toString(t.id()));if(!result.duplicate())created++;}
  System.out.printf("FusionDesk seed completed. Created %d ticket(s).%n",created);}
}
