package io.hyperfoil.tools.horreum.pipeline.steps.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;

public class PostgresJsonPathExtractorStep implements IPipelineProcessingStep<JsonNode, JsonNode> {

    private final String jsonPathExpr;

    public PostgresJsonPathExtractorStep(String jsonPathExpr) {
        this.jsonPathExpr = jsonPathExpr;
    }

    @Override
    public JsonNode run(JsonNode data) {
        return null;
    }
}
