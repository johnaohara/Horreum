package io.hyperfoil.tools.horreum.pipeline.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineSourceStep;
import io.hyperfoil.tools.horreum.pipeline.steps.dataStore.DatastoreStep;

public class DummyRequestGenerationStep implements IPipelineSourceStep<DatastoreStep.DatastoreRequestPayload> {

    private  final JsonNode data, metadata;
    public DummyRequestGenerationStep(JsonNode data, JsonNode metadata) {
        this.data = data;
        this.metadata = metadata;
    }

    @Override
    public DatastoreStep.DatastoreRequestPayload run() {
        return new DatastoreStep.DatastoreRequestPayload(this.data, this.metadata);
    }

}
