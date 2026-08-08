package com.xfusion.fusiondesk.evaluation;
import java.util.List;
public record EvaluationReport(String promptVersion,String model,String datasetResource,String createdAt,EvaluationMetrics metrics,List<EvaluationResult> results) { }
