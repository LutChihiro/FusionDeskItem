package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.ai.LlmProviderSet;
import com.xfusion.fusiondesk.config.AppConfig;
import com.xfusion.fusiondesk.exception.ValidationException;
import com.xfusion.fusiondesk.service.LlmFailoverPolicy;
import com.xfusion.fusiondesk.service.LlmMonitorService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Command(name = "llm-monitor", description = "长期运行，定时探测熔断后的主模型并在恢复后自动切回。")
public class LlmMonitorCommand implements Callable<Integer> {
    @ParentCommand
    FusionDeskCommand root;

    @Option(names = "--once", description = "仅执行一次到期探测后退出。")
    boolean once;

    @Override
    public Integer call() throws Exception {
        AppConfig config = AppConfig.current();
        LlmProviderSet providers = LlmProviderSet.fromConfig(config);
        if (providers.fallback() == null) {
            throw new ValidationException("llm-monitor requires llm.fallback.enabled=true.");
        }

        LlmFailoverPolicy policy = LlmFailoverPolicy.fromConfig(config);
        LlmMonitorService monitor = new LlmMonitorService(
                root.database(), providers.primary(), providers.primaryModel(), policy);

        if (once) {
            print(monitor.probeIfDue());
            return 0;
        }

        System.out.printf("LLM Monitor 已启动。检查间隔：%d 秒。按 Ctrl+C 停止。%n",
                policy.monitorInterval().toSeconds());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "fusiondesk-llm-monitor");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch stopped = new CountDownLatch(1);
        Thread shutdownHook = new Thread(() -> {
            scheduler.shutdownNow();
            stopped.countDown();
        }, "fusiondesk-llm-monitor-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                LlmMonitorService.ProbeResult result = monitor.probeIfDue();
                if (result.attempted()) {
                    print(result);
                }
            } catch (RuntimeException error) {
                System.err.println("LLM Monitor error: " + error.getMessage());
            }
        }, 0, policy.monitorInterval().toSeconds(), TimeUnit.SECONDS);

        try {
            stopped.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            scheduler.shutdownNow();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown is already in progress.
            }
        }
        return 0;
    }

    private void print(LlmMonitorService.ProbeResult result) {
        System.out.printf("%s | state=%s | failures=%d | nextRetry=%s | %s%n",
                Instant.now(),
                result.state().state(),
                result.state().consecutiveFailures(),
                result.state().nextRetryAt() == null ? "-" : result.state().nextRetryAt(),
                result.message());
    }
}
