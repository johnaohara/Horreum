package io.hyperfoil.tools.horreum.pipeline.steps;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.logging.Log;

public class LoggingStep implements IPipelineProcessingStep<JsonNode, JsonNode> {

    private final String message;

    public LoggingStep(String message) {
        this.message = message;
    }

    @Override
    public JsonNode run(JsonNode data) {
        Log.info(message + ": " + data);
        return data;
    }
}
