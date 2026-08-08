package com.xfusion.fusiondesk.cli;

import com.xfusion.fusiondesk.ai.OpenAiCompatibleLlmProvider;
import com.xfusion.fusiondesk.evaluation.*;
import picocli.CommandLine.*;
import java.nio.file.Path;

@Command(name="evaluate",description="Run the fixed AI triage dataset against the real configured model.")
public class EvaluateCommand implements Runnable {
    @Option(names="--prompt",required=true,description="baseline-v0 or v1")String promptVersion;
    @Override public void run(){var cases=new EvaluationCaseLoader().load();var report=new EvaluationRunner(OpenAiCompatibleLlmProvider.fromEnvironment()).run(promptVersion,cases,System.getenv("LLM_MODEL"));Path output=new EvaluationReportWriter(Path.of("evaluation-results")).write(report);EvaluationMetrics m=report.metrics();
        System.out.printf("AI evaluation completed.%n%nPrompt: %s%nModel: %s%nCases: %d%nSchema Valid: %d/%d (%.2f%%)%nCategory Accuracy: %.2f%%%nPriority Accuracy: %.2f%%%nExact Match: %.2f%%%nInjection Resistance: %.2f%%%nReport: %s%n",report.promptVersion(),report.model(),m.totalCases(),m.schemaValidCases(),m.totalCases(),m.schemaValidRate(),m.categoryAccuracy(),m.priorityAccuracy(),m.exactMatchRate(),m.injectionResistanceRate(),output);
        System.out.println("\nFailures:");boolean any=false;for(EvaluationResult r:report.results())if(!r.categoryCorrect()||!r.priorityCorrect()){any=true;System.out.printf("- %s: expected %s/%s, predicted %s%n",r.caseId(),r.expectedCategory(),r.expectedPriority(),r.schemaValid()?r.predictedCategory()+"/"+r.predictedPriority():"INVALID ("+r.error()+")");}if(!any)System.out.println("None");}
}
