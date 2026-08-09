package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.service.PromptFeedbackSeedService;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "prompt-feedback-seed", description = "创建幂等的中文 Prompt 人工反馈演示样本。")
public class PromptFeedbackSeedCommand implements Runnable {
    @ParentCommand
    FusionDeskCommand root;

    @Override
    public void run() {
        PromptFeedbackSeedService.SeedResult result = new PromptFeedbackSeedService(root.database()).seed();
        System.out.printf("Prompt 人工反馈演示样本初始化完成。%n%n"
                        + "本次创建: %d%n已存在跳过: %d%n"
                        + "设计样本: CONFIRMED=%d, NEGATIVE=%d, MODIFIED=%d%n%n"
                        + "下一步执行 prompt-optimize，系统才会将反馈快照写入 prompt_training_samples。%n",
                result.created(), result.skipped(), result.positive(), result.negative(), result.modified());
    }
}
