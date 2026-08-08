package com.xfusion.fusiondesk.service;
import com.xfusion.fusiondesk.config.AppConfig;import com.xfusion.fusiondesk.exception.ValidationException;import java.time.Duration;
public record LlmFailoverPolicy(int failureThreshold,int successThreshold,Duration retryInterval,Duration monitorInterval){
 public LlmFailoverPolicy{if(failureThreshold<1||successThreshold<1||retryInterval==null||retryInterval.isZero()||retryInterval.isNegative()||monitorInterval==null||monitorInterval.isZero()||monitorInterval.isNegative())throw new ValidationException("LLM failover thresholds and intervals must be positive.");}
 public static LlmFailoverPolicy defaults(){return new LlmFailoverPolicy(2,1,Duration.ofSeconds(60),Duration.ofSeconds(5));}
 public static LlmFailoverPolicy fromConfig(AppConfig c){return new LlmFailoverPolicy(c.intValue("llm.failover.failure-threshold",2),c.intValue("llm.failover.success-threshold",1),Duration.ofSeconds(c.intValue("llm.failover.retry-interval-seconds",60)),Duration.ofSeconds(c.intValue("llm.failover.monitor-interval-seconds",5)));}
}
