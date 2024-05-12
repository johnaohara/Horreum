package io.hyperfoil.tools.horreum.pipeline.steps.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;

public class JaywayJsonPathExtractorStep implements IPipelineProcessingStep<JsonNode, JsonNode> {

    ObjectMapper objectMapper = new ObjectMapper();

    private final String jsonPathExpr;

    public JaywayJsonPathExtractorStep(String jsonPathExpr) {
        this.jsonPathExpr = jsonPathExpr;
    }

    @Override
    public JsonNode run(JsonNode data) {
        Object read = JsonPath.parse(data.toString()).read(jsonPathExpr);
        try {
            return objectMapper.readTree(read.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
