package com.xfusion.fusiondesk.ai;

public interface LlmProvider {
    LlmResponse complete(String systemPrompt, String userPrompt);
}
