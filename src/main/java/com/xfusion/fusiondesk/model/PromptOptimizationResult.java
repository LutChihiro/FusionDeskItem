package com.xfusion.fusiondesk.model;
public record PromptOptimizationResult(long runId,PromptVersion source,PromptVersion candidate,boolean promoted,String reason,double beforeExactMatch,double afterExactMatch,double beforeInjection,double afterInjection,int positiveSamples,int negativeSamples,int modifiedSamples) { }
