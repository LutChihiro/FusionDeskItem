package com.xfusion.fusiondesk.cli;
import com.xfusion.fusiondesk.ai.OpenAiCompatibleLlmProvider;import com.xfusion.fusiondesk.config.AppConfig;import com.xfusion.fusiondesk.model.PromptOptimizationResult;import com.xfusion.fusiondesk.service.*;import picocli.CommandLine.*;
@Command(name="prompt-optimize",description="基于人工审核样本生成候选 Prompt，并通过固定评测集决定是否晋升。")
public class PromptOptimizeCommand implements Runnable{
 @ParentCommand FusionDeskCommand root;
 public void run(){AppConfig c=AppConfig.current();PromptOptimizationResult r=new PromptOptimizationService(root.database(),OpenAiCompatibleLlmProvider.fromEnvironment(),PromptOptimizationPolicy.fromConfig(c)).optimize(c.value("LLM_MODEL","llm.model"));System.out.printf("Prompt 优化运行完成。%n%n运行 ID: %d%n原版本: %s%n候选版本: %s%n正样本: %d%n负样本: %d%n人工修正样本: %d%n优化前 Exact Match: %.2f%%%n优化后 Exact Match: %.2f%%%n优化前 Injection Resistance: %.2f%%%n优化后 Injection Resistance: %.2f%%%n是否晋升: %s%n原因: %s%n",r.runId(),r.source().version(),r.candidate().version(),r.positiveSamples(),r.negativeSamples(),r.modifiedSamples(),r.beforeExactMatch(),r.afterExactMatch(),r.beforeInjection(),r.afterInjection(),r.promoted()?"是":"否",r.reason());}
}
