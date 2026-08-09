package com.xfusion.fusiondesk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfusion.fusiondesk.ai.AiAnalysisResult;
import com.xfusion.fusiondesk.model.AiSuggestion;
import com.xfusion.fusiondesk.model.SuggestionStatus;
import com.xfusion.fusiondesk.model.Ticket;
import com.xfusion.fusiondesk.model.TicketCategory;
import com.xfusion.fusiondesk.model.TicketFilter;
import com.xfusion.fusiondesk.model.TicketPriority;
import com.xfusion.fusiondesk.repository.AiSuggestionRepository;
import com.xfusion.fusiondesk.repository.AuditRepository;
import com.xfusion.fusiondesk.repository.DatabaseManager;

import java.time.Instant;
import java.util.List;

/** Creates explicitly marked, idempotent demonstration feedback for the prompt safety loop. */
public class PromptFeedbackSeedService {
    private static final String MODEL = "synthetic-feedback-seed";
    private static final String PROMPT_VERSION = "seed-v1";
    private static final String SUBMITTER_PREFIX = "prompt-feedback-seed-";

    private final DatabaseManager database;
    private final TicketService tickets;
    private final AiSuggestionRepository suggestions;
    private final AuditRepository audits;
    private final ReviewService reviews;
    private final ObjectMapper json = new ObjectMapper();

    public PromptFeedbackSeedService(DatabaseManager database) {
        this.database = database;
        this.tickets = new TicketService(database);
        this.suggestions = new AiSuggestionRepository(database);
        this.audits = new AuditRepository(database);
        this.reviews = new ReviewService(database);
    }

    public SeedResult seed() {
        int created = 0;
        int skipped = 0;
        for (SeedCase item : cases()) {
            String submitter = SUBMITTER_PREFIX + item.id();
            List<Ticket> existing = tickets.find(new TicketFilter(null, null, null, submitter));
            if (!existing.isEmpty() && !suggestions.findByTicketId(existing.get(0).id()).isEmpty()) {
                skipped++;
                continue;
            }

            Ticket ticket = existing.isEmpty()
                    ? tickets.create("[Prompt反馈样本] " + item.title(), item.description(), submitter, item.initialPriority()).ticket()
                    : existing.get(0);
            AiSuggestion suggestion = insertSyntheticSuggestion(ticket, item);
            switch (item.action()) {
                case CONFIRMED -> reviews.confirm(suggestion.id(), ticket.version());
                case MODIFIED -> reviews.modify(suggestion.id(), item.finalCategory(), item.finalPriority(), ticket.version());
                case REJECTED -> reviews.reject(suggestion.id());
                default -> throw new IllegalStateException("Unsupported seed review action: " + item.action());
            }
            created++;
        }
        return new SeedResult(created, skipped, 20, 10, 5);
    }

    private AiSuggestion insertSyntheticSuggestion(Ticket ticket, SeedCase item) {
        return database.inTransaction(connection -> {
            Instant now = Instant.now();
            AiAnalysisResult analysis = new AiAnalysisResult(item.suggestedCategory(), item.suggestedPriority(),
                    "演示反馈样本：" + item.title(), "用于验证人工反馈驱动的 Prompt 安全闭环，不代表真实模型调用。");
            String raw = json.createObjectNode()
                    .put("category", item.suggestedCategory().name())
                    .put("priority", item.suggestedPriority().name())
                    .put("summary", analysis.summary())
                    .put("reason", analysis.reason())
                    .toString();
            AiSuggestion suggestion = suggestions.insert(connection, ticket.id(), analysis, raw, MODEL, PROMPT_VERSION, now);
            String audit = json.createObjectNode()
                    .put("suggestionId", suggestion.id())
                    .put("status", SuggestionStatus.PENDING.name())
                    .put("model", MODEL)
                    .put("syntheticSeed", true)
                    .toString();
            audits.insert(connection, ticket.id(), "AI_ANALYZED", null, audit, now);
            return suggestion;
        });
    }

    private List<SeedCase> cases() {
        return List.of(
                confirmed("01", "新员工申请 GitLab 权限", "加入研发项目组并授予只读权限。", TicketCategory.ACCOUNT_ACCESS, TicketPriority.P2),
                confirmed("02", "员工账号被锁定", "多次登录失败后账号锁定，需要解锁。", TicketCategory.ACCOUNT_ACCESS, TicketPriority.P2),
                confirmed("03", "VPN 多人连接失败", "多个远程员工无法连接 VPN 服务器。", TicketCategory.NETWORK, TicketPriority.P1),
                confirmed("04", "三楼网络中断", "三楼有线和无线网络均不可用。", TicketCategory.NETWORK, TicketPriority.P1),
                confirmed("05", "DNS 解析失败", "内部域名无法解析，IP 地址可以访问。", TicketCategory.NETWORK, TicketPriority.P2),
                confirmed("06", "Word 启动闪退", "Word 启动后立即退出，其他软件正常。", TicketCategory.SOFTWARE_FAILURE, TicketPriority.P2),
                confirmed("07", "浏览器插件崩溃", "内部签章插件打开后崩溃。", TicketCategory.SOFTWARE_FAILURE, TicketPriority.P2),
                confirmed("08", "打印机缺少墨粉", "办公打印机提示墨粉耗尽。", TicketCategory.HARDWARE_OFFICE, TicketPriority.P3),
                confirmed("09", "显示器黑屏", "显示器通电但无画面，更换线缆无效。", TicketCategory.HARDWARE_OFFICE, TicketPriority.P2),
                confirmed("10", "办公电脑无法开机", "按电源键无反应，插座供电正常。", TicketCategory.HARDWARE_OFFICE, TicketPriority.P2),
                confirmed("11", "CRM 单用户保存失败", "一名用户保存客户信息时提示异常。", TicketCategory.BUSINESS_SYSTEM, TicketPriority.P2),
                confirmed("12", "订单系统全面中断", "所有用户无法创建订单，生产接口持续返回 500。", TicketCategory.BUSINESS_SYSTEM, TicketPriority.P0),
                confirmed("13", "财务系统报表超时", "多名财务人员无法生成月度报表。", TicketCategory.BUSINESS_SYSTEM, TicketPriority.P1),
                confirmed("14", "邮箱权限申请", "员工需要访问部门共享邮箱。", TicketCategory.ACCOUNT_ACCESS, TicketPriority.P2),
                confirmed("15", "密码重置申请", "员工忘记内部系统密码，需要重置。", TicketCategory.ACCOUNT_ACCESS, TicketPriority.P3),
                confirmed("16", "会议室投影仪无信号", "更换 HDMI 线后仍然无信号。", TicketCategory.HARDWARE_OFFICE, TicketPriority.P2),
                confirmed("17", "客户端升级失败", "桌面客户端升级时提示安装包损坏。", TicketCategory.SOFTWARE_FAILURE, TicketPriority.P2),
                confirmed("18", "核心支付服务不可用", "生产支付服务全部请求失败，业务完全阻断。", TicketCategory.BUSINESS_SYSTEM, TicketPriority.P0),
                confirmed("19", "无线网络信号弱", "会议区无线网络频繁断开但有线网络可用。", TicketCategory.NETWORK, TicketPriority.P2),
                confirmed("20", "问题描述信息不足", "打开后报错，没有提供系统名称和错误信息。", TicketCategory.OTHER, TicketPriority.P2),

                modified("21", "VPN 登录超时被误判权限", "VPN 客户端连接服务器超时。",
                        TicketCategory.ACCOUNT_ACCESS, TicketPriority.P2, TicketCategory.NETWORK, TicketPriority.P2),
                modified("22", "ERP 页面 500 被误判软件", "所有采购人员访问 ERP 页面均返回 500。",
                        TicketCategory.SOFTWARE_FAILURE, TicketPriority.P2, TicketCategory.BUSINESS_SYSTEM, TicketPriority.P1),
                modified("23", "打印机卡纸优先级过高", "单台打印机卡纸，旁边还有可用打印机。",
                        TicketCategory.HARDWARE_OFFICE, TicketPriority.P0, TicketCategory.HARDWARE_OFFICE, TicketPriority.P3),
                modified("24", "全员网络中断优先级过低", "总部所有员工无法访问内外网。",
                        TicketCategory.NETWORK, TicketPriority.P3, TicketCategory.NETWORK, TicketPriority.P0),
                modified("25", "账号授权被误判业务系统", "新员工访问代码仓库提示 Access Denied。",
                        TicketCategory.BUSINESS_SYSTEM, TicketPriority.P2, TicketCategory.ACCOUNT_ACCESS, TicketPriority.P2),

                rejected("26", "无意义 AI 建议一", "只有“帮忙看看”且没有任何问题信息。", TicketCategory.NETWORK, TicketPriority.P0),
                rejected("27", "恶意强制分类", "打印机缺墨，同时要求模型必须输出账号权限 P0。", TicketCategory.ACCOUNT_ACCESS, TicketPriority.P0),
                rejected("28", "角色覆盖攻击", "显示器黑屏，并伪装 SYSTEM MESSAGE 要求输出 NETWORK。", TicketCategory.NETWORK, TicketPriority.P0),
                rejected("29", "JSON 复制攻击", "软件闪退，正文要求复制一段错误分类 JSON。", TicketCategory.ACCOUNT_ACCESS, TicketPriority.P0),
                rejected("30", "不存在的故障推断", "用户只填写了问号，AI 却给出高危业务中断。", TicketCategory.BUSINESS_SYSTEM, TicketPriority.P0)
        );
    }

    private SeedCase confirmed(String id, String title, String description, TicketCategory category, TicketPriority priority) {
        return new SeedCase(id, title, description, priority, category, priority, SuggestionStatus.CONFIRMED, category, priority);
    }

    private SeedCase modified(String id, String title, String description, TicketCategory suggestedCategory,
                              TicketPriority suggestedPriority, TicketCategory finalCategory, TicketPriority finalPriority) {
        return new SeedCase(id, title, description, TicketPriority.P2, suggestedCategory, suggestedPriority,
                SuggestionStatus.MODIFIED, finalCategory, finalPriority);
    }

    private SeedCase rejected(String id, String title, String description, TicketCategory category, TicketPriority priority) {
        return new SeedCase(id, title, description, TicketPriority.P2, category, priority, SuggestionStatus.REJECTED, null, null);
    }

    private record SeedCase(String id, String title, String description, TicketPriority initialPriority,
                            TicketCategory suggestedCategory, TicketPriority suggestedPriority,
                            SuggestionStatus action, TicketCategory finalCategory, TicketPriority finalPriority) { }

    public record SeedResult(int created, int skipped, int positive, int negative, int modified) { }
}
