package com.xfusion.fusiondesk.service;

import com.xfusion.fusiondesk.ai.AiResponseValidator;
import com.xfusion.fusiondesk.ai.LlmProvider;
import com.xfusion.fusiondesk.ai.LlmResponse;
import com.xfusion.fusiondesk.model.LlmCircuitState;
import com.xfusion.fusiondesk.model.LlmProviderState;
import com.xfusion.fusiondesk.repository.DatabaseManager;
import com.xfusion.fusiondesk.repository.LlmProviderStateRepository;

import java.time.Instant;

/** Probes an open primary-model circuit and closes it after enough successful probes. */
public class LlmMonitorService {
    private final LlmProvider primary;
    private final String primaryModel;
    private final LlmProviderStateRepository states;
    private final LlmFailoverPolicy policy;
    private final AiResponseValidator validator = new AiResponseValidator();

    public LlmMonitorService(DatabaseManager database, LlmProvider primary,
                             String primaryModel, LlmFailoverPolicy policy) {
        this.primary = primary;
        this.primaryModel = primaryModel;
        this.states = new LlmProviderStateRepository(database);
        this.policy = policy;
    }

    public ProbeResult probeIfDue() {
        LlmProviderState before = states.get();
        if (before.state() == LlmCircuitState.CLOSED) {
            return new ProbeResult(false, true, before, "主模型熔断器已关闭，无需探测。");
        }

        Instant now = Instant.now();
        if (!states.claimPrimaryProbe(now, policy, LlmProviderStateRepository.SOURCE_MONITOR)) {
            return new ProbeResult(false, false, states.get(),
                    "尚未到达下一次探测时间，或探测已被其他进程占用。");
        }

        try {
            LlmResponse response = primary.complete(healthSystem(), healthUser());
            if (response == null || response.content() == null
                    || response.model() == null || response.model().isBlank()) {
                throw new IllegalStateException("主模型返回空响应。");
            }
            validator.validate(response.content());
            states.recordSuccess(response.model(), Instant.now(), policy,
                    LlmProviderStateRepository.SOURCE_MONITOR);
            LlmProviderState after = states.get();
            String message = after.state() == LlmCircuitState.CLOSED
                    ? "主模型探测成功，已切回主模型。"
                    : "主模型探测成功，等待更多成功次数。";
            return new ProbeResult(true, after.state() == LlmCircuitState.CLOSED, after, message);
        } catch (RuntimeException error) {
            states.recordFailure(primaryModel, safeMessage(error), Instant.now(), policy,
                    LlmProviderStateRepository.SOURCE_MONITOR);
            return new ProbeResult(true, false, states.get(), "主模型探测失败：" + safeMessage(error));
        }
    }

    public LlmProviderState currentState() {
        return states.get();
    }

    private String healthSystem() {
        return "你是 IT 工单分类健康检查器。只返回严格 JSON："
                + "{\"category\":\"OTHER\",\"priority\":\"P3\","
                + "\"summary\":\"主模型健康检查成功。\","
                + "\"reason\":\"这是固定健康检查请求。\"}。禁止 Markdown 或其他文字。";
    }

    private String healthUser() {
        return "这是固定健康检查数据，不包含真实业务信息。";
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "未知模型错误";
        }
        return message.substring(0, Math.min(500, message.length()));
    }

    public record ProbeResult(boolean attempted, boolean recovered,
                              LlmProviderState state, String message) { }
}
