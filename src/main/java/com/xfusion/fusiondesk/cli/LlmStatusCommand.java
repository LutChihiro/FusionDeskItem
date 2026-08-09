package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.model.LlmProviderEvent;
import com.xfusion.fusiondesk.model.LlmProviderState;
import com.xfusion.fusiondesk.repository.LlmProviderStateRepository;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "llm-status", description = "查看主备模型熔断状态和最近的降级/恢复事件。")
public class LlmStatusCommand implements Runnable {
    @ParentCommand FusionDeskCommand root;

    @Override
    public void run() {
        LlmProviderStateRepository repository = new LlmProviderStateRepository(root.database());
        LlmProviderState state = repository.get();
        System.out.printf("LLM Provider State%n%nState: %s%nCurrent Model: %s%nFailures: %d%nSuccesses: %d%n"
                        + "Last Failure: %s%nLast Success: %s%nNext Retry: %s%nLast Error: %s%nVersion: %d%n%n",
                state.state(), value(state.currentModel()), state.consecutiveFailures(), state.consecutiveSuccesses(),
                value(state.lastFailureAt()), value(state.lastSuccessAt()), value(state.nextRetryAt()),
                value(state.lastError()), state.version());

        List<LlmProviderEvent> events = repository.findEvents();
        int from = Math.max(0, events.size() - 10);
        System.out.println("Recent Events");
        System.out.println("TIME | EVENT | SOURCE | FROM -> TO | PRIMARY | ACTIVE | ERROR");
        for (LlmProviderEvent event : events.subList(from, events.size())) {
            System.out.printf("%s | %s | %s | %s -> %s | %s | %s | %s%n",
                    value(event.createdAt()), event.eventType(), event.source(), value(event.fromState()),
                    value(event.toState()), value(event.primaryModel()), value(event.activeModel()), value(event.errorMessage()));
        }
        if (events.isEmpty()) System.out.println("No failover events.");
    }

    private String value(Object value) { return value == null ? "-" : value.toString(); }
}
