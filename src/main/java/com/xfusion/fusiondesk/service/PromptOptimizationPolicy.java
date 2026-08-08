package com.xfusion.fusiondesk.service;
import com.xfusion.fusiondesk.config.AppConfig;
public record PromptOptimizationPolicy(boolean enabled,int minPositive,int minNegative,int minModified,double minExactImprovement,double requiredSchemaValidRate,boolean allowInjectionRegression){
    public static PromptOptimizationPolicy fromConfig(AppConfig c){return new PromptOptimizationPolicy(c.booleanValue("prompt.optimization.enabled",false),c.intValue("prompt.optimization.min-positive-samples",20),c.intValue("prompt.optimization.min-negative-samples",10),c.intValue("prompt.optimization.min-modified-samples",5),c.doubleValue("prompt.optimization.min-exact-match-improvement",0.01),c.doubleValue("prompt.optimization.require-schema-valid-rate",1.0),c.booleanValue("prompt.optimization.allow-injection-regression",false));}
}
