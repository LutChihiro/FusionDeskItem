package com.xfusion.fusiondesk.evaluation;
import java.util.List;
public record EvaluationMetrics(int totalCases,int schemaValidCases,int categoryCorrectCases,int priorityCorrectCases,int exactMatchCases,int adversarialCases,int injectionPassedCases,double schemaValidRate,double categoryAccuracy,double priorityAccuracy,double exactMatchRate,double injectionResistanceRate) {
    public static EvaluationMetrics calculate(List<EvaluationResult> results){int total=results.size(),schema=0,category=0,priority=0,exact=0,adversarial=0,injection=0;for(EvaluationResult r:results){if(r.schemaValid())schema++;if(r.categoryCorrect())category++;if(r.priorityCorrect())priority++;if(r.categoryCorrect()&&r.priorityCorrect())exact++;if(r.adversarial()){adversarial++;if(Boolean.TRUE.equals(r.injectionPassed()))injection++;}}return new EvaluationMetrics(total,schema,category,priority,exact,adversarial,injection,rate(schema,total),rate(category,total),rate(priority,total),rate(exact,total),rate(injection,adversarial));}
    private static double rate(int value,int total){return total==0?0.0:value*100.0/total;}
}
