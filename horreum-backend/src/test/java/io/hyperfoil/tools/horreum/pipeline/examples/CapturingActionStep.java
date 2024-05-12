package io.hyperfoil.tools.horreum.pipeline.examples;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;

import java.util.function.Consumer;

public class CapturingActionStep implements IPipelineProcessingStep<JsonNode, JsonNode> {

    private final Consumer<JsonNode> consumer;

    public CapturingActionStep(Consumer<JsonNode> consumer) {
        this.consumer = consumer;
    }

    @Override
    public JsonNode run(JsonNode data) {
        this.consumer.accept(data);
        return data;
    }
}
