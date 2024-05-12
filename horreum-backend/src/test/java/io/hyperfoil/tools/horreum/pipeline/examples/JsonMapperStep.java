package io.hyperfoil.tools.horreum.pipeline.examples;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;

public class JsonMapperStep implements IPipelineProcessingStep<JsonNode, JsonNode> {
    @Override
    public JsonNode run(JsonNode data) {
        return null;
    }
}
