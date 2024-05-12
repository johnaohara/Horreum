package io.hyperfoil.tools.horreum.pipeline.steps.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineSourceStep;

public class ObjectMappingStep implements IPipelineSourceStep<JsonNode> {


    private final String data;
    //TODO:: inject
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObjectMappingStep(String data) {
        this.data = data;
    }


    @Override
    public JsonNode run() {
        try {
            return objectMapper.readTree(this.data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
