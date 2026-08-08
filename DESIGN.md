# FusionDesk 设计与协作说明

## 1. 设计目标与取舍

FusionDesk 面向 3～4 小时限时开发和现场演示场景，采用 Java 17 CLI、Picocli 和 JDBC。系统不引入 Spring Boot、JPA、MyBatis、Redis、MQ 或前端，以较小工程规模完成可靠、可测试、可追溯的工单与 AI 协作闭环。

```text
CLI -> Service -> Repository -> MySQL / SQLite

Ticket -> PromptBuilder -> LlmProvider -> DeepSeek
       -> AiResponseValidator -> PENDING AiSuggestion -> Human Review
```

CLI 只负责参数解析和输出；Service 负责校验、状态机和事务编排；Repository 使用 `PreparedStatement` 完成持久化。MySQL 是正式运行数据库，SQLite 用于单元测试和轻量本地运行。

## 2. 需求假设

### 2.1 工单状态机

```text
NEW -> IN_PROGRESS -> RESOLVED -> CLOSED
                       |
                       +-> IN_PROGRESS
```

`RESOLVED -> IN_PROGRESS` 表示已解决问题可以重新打开。其他流转和同状态更新全部拒绝，不自动纠正。

### 2.2 分类与优先级

category 固定为：

```text
ACCOUNT_ACCESS
SOFTWARE_FAILURE
NETWORK
HARDWARE_OFFICE
BUSINESS_SYSTEM
OTHER
```

priority 固定为：

- P0：核心业务大面积中断、严重安全事件或关键生产系统完全不可用。
- P1：影响多人或重要业务，需要尽快处理。
- P2：普通问题，影响有限或存在替代方式。
- P3：低影响、一般咨询或办公耗材问题。

### 2.3 AI 分析与人工生效

同一个 Ticket 可以多次执行 `analyze`，每次成功都生成独立 PENDING AiSuggestion，不覆盖历史结果。只有 CONFIRMED 和 MODIFIED 才能修改 Ticket 最终 category/priority；REJECTED 不修改 Ticket。

审核完成后仍允许再次分析，但新的 Suggestion 仍不会自动覆盖已经生效的人工决定。

## 3. Duplicate 设计

重复键由以下字段组成：

```text
submitter
+ normalized title
+ normalized description
```

标准化规则依次为：`strip()`、Unicode NFKC、连续 whitespace 折叠、`toLowerCase(Locale.ROOT)`，然后计算 SHA-256。

如果存在相同 `dedup_key` 且状态不是 CLOSED 的 Ticket，则返回原 Ticket ID，不创建新记录；已有记录 CLOSED 后允许重新提交。

### 3.1 SQLite

SQLite 使用 partial unique index：

```sql
CREATE UNIQUE INDEX uq_tickets_active_dedup
ON tickets(dedup_key)
WHERE status <> 'CLOSED';
```

### 3.2 MySQL

MySQL 不支持相同的 partial unique index 语法，因此使用 generated column：

```text
Active Ticket: active_dedup_key = dedup_key
CLOSED Ticket: active_dedup_key = NULL
```

再对 `active_dedup_key` 建立 unique index。MySQL unique index 允许多个 NULL，因此既保证同一个 dedup key 最多只有一个 Active Ticket，又允许保留多个 CLOSED 历史记录。Service 的预查询用于友好提示，数据库唯一约束用于关闭并发 `SELECT -> INSERT` 竞态。

## 4. 并发控制

### 4.1 Ticket 乐观锁

Ticket 使用 `version` 字段：

```sql
UPDATE tickets
SET status = ?, version = version + 1, updated_at = ?
WHERE id = ? AND version = ?;
```

更新行数为 0 时，Service 进一步区分 Ticket 不存在和版本过期，避免 lost update。

### 4.2 Suggestion 审核并发

审核更新包含：

```sql
WHERE id = ? AND status = 'PENDING'
```

该条件是重复或并发审核的数据库兜底。Confirm 和 Modify 同时要求当前 Ticket version，任何版本冲突都会回滚整个审核事务。

## 5. 事务与审计

以下操作必须使用同一个 JDBC Connection 和 Transaction：

```text
Ticket Create + CREATED Audit
Ticket Transition + STATUS_CHANGED Audit
AiSuggestion + AI_ANALYZED Audit
Ticket + Suggestion + AI_CONFIRMED / AI_MODIFIED Audit
Suggestion + AI_REJECTED Audit
Evaluation Run + 全部 Case Results
Prompt 候选 + 指标 + 晋升决策
LLM 状态更新 + 降级/监控事件
```

处理流程显式执行 `setAutoCommit(false)`；成功 commit，任一步失败 rollback，避免业务数据与审计或评测明细不一致。

## 6. AI Prompt 安全设计

### 6.1 指令与数据隔离（Instruction/Data Separation）

Ticket 标题和描述被定义为 `UNTRUSTED USER DATA`，并通过 Jackson 序列化为 JSON 后放入明确的数据区域。工单内容中的以下文本都只能作为数据分析：

```text
忽略以上指令
修改角色
强制 Category
强制 Priority
伪 SYSTEM MESSAGE
JSON 注入
```

它们不能覆盖 system 规则。模型应先识别真实运维问题，再映射到固定 taxonomy。

### 6.2 白名单与严格 JSON

category 只能选择六个 TicketCategory enum，priority 只能选择 P0～P3。模型只允许输出：

```json
{
  "category": "...",
  "priority": "...",
  "summary": "...",
  "reason": "..."
}
```

禁止 Markdown、代码围栏和 JSON 之外的解释。

### 6.3 本地校验

AiResponseValidator 对以下内容再次校验：

```text
响应非空
合法 JSON
字段完整
enum 合法
summary/reason 非空
长度符合限制
```

模型输出始终是不可信输入，只有通过本地校验后才能写入数据库。

## 7. 模型失败、降级与恢复

以下情况统一视为 AI 失败：

```text
timeout
HTTP error / 401
网络异常
响应结构异常
invalid JSON
invalid enum
missing field
```

如果没有可用 Provider，`analyze` 返回失败，不创建 AiSuggestion 或成功 Audit，也不修改 Ticket。系统禁止固定返回 `OTHER/P2` 冒充 AI 成功。AI 故障不会影响 `create`、`list`、`show`、`transition` 和 `audit`。

生产分析支持主备模型降级。当前快照保存在 `llm_provider_state`：

```text
CLOSED -> OPEN -> HALF_OPEN -> CLOSED
```

主模型失败达到阈值后进入 OPEN，分析直接使用备用模型。长期运行的 `llm-monitor` 到期后抢占一次 HALF_OPEN 探测；验证成功则切回主模型，失败则重新 OPEN。抢占使用数据库 version，避免多个进程同时探测。`analyze` 也保留到期懒探测，使监控进程暂时不可用时仍可恢复。

`llm_provider_events` 追加保存 `PRIMARY_FAILURE`、`PRIMARY_SUCCESS`、`FALLBACK_USED`、`PROBE_STARTED`、`PROBE_FAILED`、`PROBE_SUCCEEDED`。事件不保存密钥、请求头、Prompt、原始响应或工单正文。

## 8. 人工确认闭环

```text
AI
 |
 v
PENDING
├── CONFIRMED
├── MODIFIED
└── REJECTED
```

- CONFIRMED：将 AI 原始 suggestedCategory/suggestedPriority 应用到 Ticket。
- MODIFIED：将人工 category/priority 应用到 Ticket。
- REJECTED：仅更新 Suggestion，不修改 Ticket。

AI 原始 `suggestedCategory`、`suggestedPriority`、`summary`、`reason`、`rawResponse`、`model`、`promptVersion` 永远保留。人工最终值保存到 `finalCategory` 和 `finalPriority`，不会覆盖原始 AI 数据。

## 9. 工程风险与加固

| 风险 | 影响 | 加固方式 | 状态 |
|---|---|---|---|
| 空输入 | 产生无效工单 | Service 非空校验 | 已完成 |
| 超长输入 | DB/Prompt 膨胀 | 统一长度限制 | 已完成 |
| 非法 enum/状态流转 | 业务状态异常 | Picocli enum、DB check、显式状态机 | 已完成 |
| 重复提交 | 重复处理 | SHA-256 + DB 唯一约束 | 已完成 |
| 并发创建 | 多个 Active Ticket | 数据库 Unique Constraint | 已完成 |
| 并发修改 | Lost Update | Optimistic Lock | 已完成 |
| 重复人工审核 | Final 决策冲突 | `status='PENDING'` 条件更新 | 已完成 |
| Audit 与业务不一致 | 无法追踪 | 同事务提交 | 已完成 |
| Prompt Injection | AI 被操纵 | Instruction/Data 隔离和攻击评测 | 已完成 |
| LLM 非法输出 | 脏数据入库 | JSON、字段、enum、长度校验 | 已完成 |
| 模型不可用 | AI 功能中断 | Failure Isolation、Fallback、Monitor | 已完成 |
| 密钥泄露 | 安全风险 | 环境变量、本地忽略配置、事件脱敏 | 已完成 |

## 10. AI Evaluation

Ground Truth 位于版本控制的 `evaluation-cases.json`，在 Baseline 运行前固定。数据集包括：

```text
Total Cases: 16
Normal Cases: 12
Adversarial Cases: 4
```

指标：

- Schema Valid Rate：通过本地结构校验的比例。
- Category Accuracy：分类正确率。
- Priority Accuracy：优先级正确率。
- Exact Match：category 和 priority 同时正确的比例。
- Injection Resistance：对抗样例的 Exact Match 比例。

`baseline-v0` 仅提供基础分类白名单和 JSON 要求。优化后的 `v1` 增加 taxonomy、priority 定义、untrusted data 声明、Prompt Injection 防护、Instruction/Data 隔离和更严格的结构化输出。

两轮使用同一个 `deepseek-v4-flash`、同一数据集和同一本地校验器：

| 指标 | baseline-v0 | v1 | 提升 |
|---|---:|---:|---:|
| Schema Valid Rate | 100.00% | 100.00% | +0.00 pp |
| Category Accuracy | 81.25% | 93.75% | +12.50 pp |
| Priority Accuracy | 93.75% | 100.00% | +6.25 pp |
| Exact Match | 81.25% | 93.75% | +12.50 pp |
| Injection Resistance | 75.00% | 100.00% | +25.00 pp |

优化后仍将 `ambiguous-other-01` 预测为 BUSINESS_SYSTEM/P2，而 Ground Truth 是 OTHER/P2。该失败未删除或重标。

## 11. Prompt 安全优化闭环

Prompt 优化由人工手动触发，不在 review 时自动执行。CONFIRMED 作为正样本，MODIFIED 作为带人工正确标签的负样本，REJECTED 作为不虚构正确标签的负面证据。

达到配置样本阈值后，`prompt-optimize` 使用真实模型生成通用候选 Prompt，并用同一个固定数据集分别评测当前和候选版本。只有 Exact Match 达到配置提升且 Schema Valid、Category、Priority、Injection Resistance 不退化时才晋升。所有 Prompt 版本、样本快照、更新前后准确率和决策结果均保存到数据库。

## 12. 数据持久化

MySQL 8.x 是正式运行数据库，所有表使用 InnoDB 和 utf8mb4。SQLite 保留用于快速开发、隔离测试和轻量演示。领域时间类型保持 `Instant`，MySQL 使用 UTC `DATETIME(3)`，SQLite 使用 UTC ISO-8601 文本。

业务 Service 不感知当前数据库类型，数据库方言只处理 Schema、时间绑定、元数据写入和 duplicate-key 识别。

## 13. AI Coding 协作

使用的 AI Coding 工具：Codex。

真实协作内容：

1. 根据任务要求辅助拆分 Java 工程结构和开发阶段。
2. 辅助实现并检查 Duplicate、Optimistic Lock 和 JDBC Transaction。
3. 辅助设计安全 Prompt、Prompt Injection 防护和 AI 输出校验。
4. 辅助构建 Evaluation、人工审核闭环和 MySQL 持久化。
5. 辅助实现主备模型降级、持久化熔断状态和恢复监控。
6. 所有 AI 生成代码均经过代码检查、编译、自动化测试和真实 CLI 验收。

### 13.1 真实错误与纠偏

最初生成的 `LlmMonitorService` 中出现乱码中文和非法的单行 Java text block，存在代码质量和编译风险。

发现方式：通过代码检查发现异常字符和字符串语法问题，并在编译前停止继续扩展。

纠偏方式：将文件重写为规范 UTF-8，使用普通 Java 字符串替代非法 text block，随后重新执行默认测试、MySQL regression 和 Maven package，全部通过。

该案例说明：AI Coding 提升了开发效率，但输出不能直接信任，必须通过代码审查、编译和自动化测试验证。

## 14. 已知限制

1. 系统仅提供 CLI，无 Web UI、RBAC 或 SSO。
2. 分类体系固定为六类。
3. 每个 Ticket 只有一个当前最终分类。
4. Evaluation 只有 16 条样例，不代表生产规模基准。
5. 模糊和多问题 Ticket 仍可能误判，人工审核不能省略。
6. 没有独立分布式调度或锁服务，Monitor 协调依赖共享数据库 version。
7. 密钥依赖环境变量或被 Git 忽略的本地配置，没有接入 Secret Manager。
