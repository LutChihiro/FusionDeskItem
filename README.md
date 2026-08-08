# FusionDesk 智能工单协同系统

## 1. 项目简介

FusionDesk 是一个面向企业内部问题处理场景的智能工单协同系统，采用 Java CLI 形式交付。系统覆盖工单创建、查询、状态流转、重复提交防护、审计追踪、AI 辅助分诊、人工确认闭环和 AI 评测。

AI 只产生建议，不会未经人工确认直接修改 Ticket 的最终分类和优先级。

## 2. 核心能力

- 工单创建、详情查询、多条件筛选和状态流转
- 基于 SHA-256 与数据库唯一约束的重复提交防护
- 乐观锁（Optimistic Lock）和事务审计
- 真实 DeepSeek 分类、优先级、摘要和理由生成
- 提示词注入（Prompt Injection）防护与严格本地校验
- 主模型失败降级、熔断状态持久化和恢复监控
- 人工确认闭环（Human-in-the-loop）：Confirm、Modify、Reject
- 16 条固定数据集的 Baseline、Prompt 优化和同集复测
- MySQL 正式持久化与 SQLite 轻量/测试兼容

## 3. 技术栈

- Java 17
- Maven 3.9+
- Picocli
- JDBC
- MySQL 8.x / SQLite
- Jackson
- `java.net.http.HttpClient`
- JUnit 5 / Mockito
- DeepSeek / OpenAI-compatible API

项目未使用 Spring Boot、MyBatis、JPA、Redis、MQ 或前端框架。

## 4. 系统架构

```text
CLI
 │
 ▼
Service
 ├── TicketService
 ├── AiTriageService
 └── ReviewService
 │
 ▼
Repository
 ├── MySQL
 └── SQLite

Ticket
 │
 ▼
PromptBuilder
 │
 ▼
LlmProvider
 │
 ▼
DeepSeek
 │
 ▼
AiResponseValidator
 │
 ▼
PENDING AiSuggestion
 │
 ▼
人工 Confirm / Modify / Reject
```

CLI 负责参数解析，Service 负责业务规则，Repository 只负责参数化 SQL 与持久化。MySQL 和 SQLite 通过小型数据库方言层复用同一套业务 Service。

## 5. 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.x（正式运行模式）

当前项目已使用 JDK 21.0.11 和 Maven 3.9.16 验证，通过 `maven.compiler.release=17` 生成 Java 17 兼容字节码。

## 6. 数据库初始化

MySQL 数据库需要预先创建，应用负责幂等创建表和索引：

```sql
CREATE DATABASE fusiondesk
CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;
```

正式环境建议使用最小权限专用账户，不要使用 root 账户运行应用。

## 7. 配置说明

### 7.1 本地配置文件

复制配置模板：

```text
config/fusiondesk.properties.example
```

保存为被 Git 忽略的：

```text
config/fusiondesk.properties
```

示例：

```properties
db.type=mysql
mysql.url=jdbc:mysql://localhost:3306/fusiondesk?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
mysql.user=your-user
mysql.password=your-password

llm.api-key=your-api-key
llm.base-url=https://example.com/v1
llm.model=your-model-name
```

可通过 `FUSIONDESK_CONFIG` 或 JVM 参数 `-Dfusiondesk.config=...` 指定其他配置文件。

### 7.2 环境变量

MySQL：

```text
FUSIONDESK_DB_TYPE
MYSQL_URL
MYSQL_USER
MYSQL_PASSWORD
```

LLM：

```text
LLM_API_KEY
LLM_BASE_URL
LLM_MODEL
```

环境变量优先于配置文件。没有数据库配置时，系统默认使用 `data/fusiondesk.db`。真实密码、API Key、`.env` 和运行数据库文件不得提交 Git。

## 8. 构建与测试

构建 fat JAR：

```bash
mvn clean package
```

输出文件：

```text
target/fusiondesk.jar
```

默认测试使用临时 SQLite 文件，不访问真实 LLM：

```bash
mvn test
```

MySQL 集成测试使用真实配置数据库和 Fake LLM，结束时清理测试业务数据：

```bash
mvn test -Pmysql-it
```

最终验证结果：默认测试 62/62 PASS；MySQL profile 63/63 PASS，其中 MySQL 专项集成测试 1 个。

## 9. 启动与初始化

```bash
java -jar target/fusiondesk.jar init
java -jar target/fusiondesk.jar seed
```

`init` 会幂等初始化当前数据库；`seed` 会幂等生成 5 条中文演示工单。

## 10. 工单操作

### 创建

```bash
java -jar target/fusiondesk.jar create --title "VPN 无法连接" --description "认证失败" --submitter alice --priority P1
```

### 列表和组合筛选

```bash
java -jar target/fusiondesk.jar list
java -jar target/fusiondesk.jar list --status NEW --category NETWORK --priority P1 --submitter alice
```

### 详情、状态流转和审计

```bash
java -jar target/fusiondesk.jar show 1
java -jar target/fusiondesk.jar transition 1 --to IN_PROGRESS --version 0
java -jar target/fusiondesk.jar audit 1
```

允许的状态流转：

```text
NEW -> IN_PROGRESS -> RESOLVED -> CLOSED
                       |
                       +-> IN_PROGRESS
```

分类固定为：

```text
ACCOUNT_ACCESS
SOFTWARE_FAILURE
NETWORK
HARDWARE_OFFICE
BUSINESS_SYSTEM
OTHER
```

优先级为 P0（核心业务阻断）至 P3（低影响问题）。

## 11. AI 智能分诊

```bash
java -jar target/fusiondesk.jar analyze <ticket-id>
```

AI 根据 Ticket 的 `title` 和 `description` 生成：

```text
category
priority
summary
reason
```

返回内容必须通过 JSON、必填字段、长度和 enum 白名单校验。成功结果保存为：

```text
Suggestion Status = PENDING
```

`analyze` 不会直接修改 Ticket 的 `category`、`priority`、`status` 或 `version`。模型失败时不会用固定 `OTHER/P2` 冒充成功。

### 模型降级与恢复

生产分析支持独立备用模型。超时、HTTP/网络错误、异常响应、非法 JSON 或非法 enum 均可触发降级。

```properties
llm.fallback.enabled=true
llm.fallback.api-key=
llm.fallback.base-url=
llm.fallback.model=your-fallback-model
llm.failover.failure-threshold=2
llm.failover.success-threshold=1
llm.failover.retry-interval-seconds=60
llm.failover.monitor-interval-seconds=5
```

长期恢复监控：

```bash
java -jar target/fusiondesk.jar llm-monitor
java -jar target/fusiondesk.jar llm-monitor --once
```

当前熔断状态保存在 `llm_provider_state`，主模型失败、备用模型使用和恢复探测历史保存在 `llm_provider_events`。记录不包含 API Key、Authorization Header、Prompt 或工单正文。

## 12. 人工确认闭环

```text
PENDING
├── CONFIRMED
├── MODIFIED
└── REJECTED
```

真实命令格式：

```bash
java -jar target/fusiondesk.jar review 3 confirm --version 0
java -jar target/fusiondesk.jar review 3 modify --category NETWORK --priority P1 --version 0
java -jar target/fusiondesk.jar review 3 reject
```

- `CONFIRMED`：采用 AI 原始建议并更新 Ticket。
- `MODIFIED`：采用人工指定的 category 和 priority。
- `REJECTED`：拒绝建议，不修改 Ticket。

AI 原始 `suggestedCategory`、`suggestedPriority`、`summary`、`reason`、`rawResponse`、`model`、`promptVersion` 不会被人工覆盖。Confirm 和 Modify 使用 Ticket 乐观锁；Ticket、AiSuggestion 和 Audit 在同一事务中提交。

## 13. AI 评测

真实评测命令：

```bash
java -jar target/fusiondesk.jar evaluate --prompt baseline-v0
java -jar target/fusiondesk.jar evaluate --prompt v1
```

评测配置：

```text
Total Cases: 16
Normal Cases: 12
Adversarial Cases: 4
Model: deepseek-v4-flash
```

| 指标 | baseline-v0 | v1 | 提升 |
|---|---:|---:|---:|
| Schema Valid Rate | 100.00% | 100.00% | +0.00 pp |
| Category Accuracy | 81.25% | 93.75% | +12.50 pp |
| Priority Accuracy | 93.75% | 100.00% | +6.25 pp |
| Exact Match | 81.25% | 93.75% | +12.50 pp |
| Injection Resistance | 75.00% | 100.00% | +25.00 pp |

两轮使用同一个模型、同一份评测集和同一个本地校验器，Ground Truth 未在 baseline 后修改。优化后仍有 `ambiguous-other-01` 分类错误，该失败保留在报告和数据库中。

评测结果保存在：

```text
evaluation-results/baseline-v0.json
evaluation-results/optimized-v1.json
evaluation-results/comparison.md
```

## 14. Prompt 安全优化闭环

人工审核形成可追溯反馈样本。达到配置阈值后手动执行：

```bash
java -jar target/fusiondesk.jar prompt-optimize
```

系统生成通用候选 Prompt，并使用同一固定评测集复测当前版本和候选版本。只有 Exact Match 达到最小提升且其他指标不退化时，候选才会在事务中晋升为唯一 `ACTIVE`；否则保留为 `REJECTED`。Prompt 全版本、样本快照、更新前后准确率和晋升决策均保存在数据库中。

## 15. 数据持久化

MySQL 使用 InnoDB 和 utf8mb4，保存：

```text
tickets
ai_suggestions
audit_events
evaluation_runs
evaluation_case_results
prompt_versions
prompt_optimization_runs
prompt_training_samples
llm_provider_state
llm_provider_events
```

SQLite 保留相同核心业务语义，用于单元测试和轻量本地模式。

## 16. 项目目录

```text
src/main/java/com/xfusion/fusiondesk/
├── ai/
├── cli/
├── config/
├── evaluation/
├── exception/
├── model/
├── repository/
├── service/
└── util/

src/main/resources/evaluation-cases.json
src/test/java/com/xfusion/fusiondesk/
config/fusiondesk.properties.example
evaluation-results/
```

## 17. 已知限制

1. 仅提供 CLI，无 Web UI、RBAC 或 SSO。
2. 分类体系固定为六类。
3. 每个 Ticket 只有一个当前最终分类。
4. Evaluation 只有 16 条样例，不代表生产规模基准。
5. 模糊或多问题 Ticket 仍可能被模型误判，人工审核仍是必要环节。
6. 密钥依赖环境变量或被忽略的本地配置文件，未接入 Secret Manager。
