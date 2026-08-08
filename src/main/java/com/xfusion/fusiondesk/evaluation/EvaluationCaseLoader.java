package com.xfusion.fusiondesk.evaluation;
import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.ObjectMapper;import com.xfusion.fusiondesk.exception.BusinessException;
import java.io.InputStream;import java.util.List;
public class EvaluationCaseLoader {
    public static final String RESOURCE="evaluation-cases.json";private final ObjectMapper json=new ObjectMapper();
    public List<EvaluationCase> load(){try(InputStream in=Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE)){if(in==null)throw new BusinessException("Evaluation dataset not found: "+RESOURCE);List<EvaluationCase> cases=json.readValue(in,new TypeReference<>(){});if(cases.size()<10)throw new BusinessException("Evaluation dataset must contain at least 10 cases.");return List.copyOf(cases);}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("Failed to load evaluation dataset.",e);}}
}
