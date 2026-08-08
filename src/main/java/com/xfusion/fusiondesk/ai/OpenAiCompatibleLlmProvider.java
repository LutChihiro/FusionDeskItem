package com.xfusion.fusiondesk.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.xfusion.fusiondesk.exception.AiAnalysisException;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class OpenAiCompatibleLlmProvider implements LlmProvider {
    private final HttpClient client; private final URI endpoint; private final String apiKey; private final String model; private final ObjectMapper json;
    public OpenAiCompatibleLlmProvider(HttpClient client,String baseUrl,String apiKey,String model){
        if(blank(apiKey)||blank(baseUrl)||blank(model))throw missingConfiguration();
        this.client=client;this.apiKey=apiKey;this.model=model;this.json=new ObjectMapper();
        String normalized=baseUrl.strip().replaceAll("/+$","");
        try{this.endpoint=URI.create(normalized+"/chat/completions");if(!"https".equalsIgnoreCase(endpoint.getScheme())&&!"http".equalsIgnoreCase(endpoint.getScheme()))throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){throw new AiAnalysisException("LLM_BASE_URL is invalid.");}
    }
    public static OpenAiCompatibleLlmProvider fromEnvironment(){return new OpenAiCompatibleLlmProvider(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),System.getenv("LLM_BASE_URL"),System.getenv("LLM_API_KEY"),System.getenv("LLM_MODEL"));}
    @Override public LlmResponse complete(String systemPrompt,String userPrompt){
        ObjectNode body=json.createObjectNode();body.put("model",model);body.put("temperature",0);ArrayNode messages=body.putArray("messages");messages.addObject().put("role","system").put("content",systemPrompt);messages.addObject().put("role","user").put("content",userPrompt);
        final HttpRequest request;try{request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();}
        catch(Exception e){throw new AiAnalysisException("Failed to build LLM request.",e);}
        final HttpResponse<String> response;try{response=client.send(request,HttpResponse.BodyHandlers.ofString());}
        catch(HttpTimeoutException e){throw new AiAnalysisException("LLM request timed out.",e);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiAnalysisException("LLM request was interrupted.",e);}
        catch(Exception e){throw new AiAnalysisException("LLM request failed.",e);}
        if(response.statusCode()<200||response.statusCode()>=300)throw new AiAnalysisException("LLM request failed with HTTP "+response.statusCode()+".");
        try{JsonNode root=json.readTree(response.body()),choices=root.get("choices");if(choices==null||!choices.isArray()||choices.isEmpty())throw invalid();JsonNode first=choices.get(0),message=first.get("message"),content=message==null?null:message.get("content");if(content==null||!content.isTextual()||content.asText().isBlank())throw invalid();String responseModel=root.path("model").asText(model);return new LlmResponse(content.asText(),responseModel.isBlank()?model:responseModel);}
        catch(AiAnalysisException e){throw e;}catch(Exception e){throw invalid();}
    }
    private static boolean blank(String v){return v==null||v.isBlank();}
    private static AiAnalysisException missingConfiguration(){return new AiAnalysisException("LLM configuration is missing. Please configure LLM_API_KEY, LLM_BASE_URL and LLM_MODEL.");}
    private AiAnalysisException invalid(){return new AiAnalysisException("LLM returned an invalid response.");}
}
