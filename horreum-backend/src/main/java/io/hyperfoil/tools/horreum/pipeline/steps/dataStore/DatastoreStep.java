package io.hyperfoil.tools.horreum.pipeline.steps.dataStore;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.datastore.Datastore;
import io.hyperfoil.tools.horreum.datastore.DatastoreResponse;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;

public abstract class DatastoreStep<T> implements IPipelineProcessingStep<DatastoreStep.DatastoreRequestPayload, DatastoreResponse>, Datastore {
    private final T configuration;

    public DatastoreStep(T config) {
        this.configuration = config;
    }

    @Override
    public DatastoreResponse run(DatastoreStep.DatastoreRequestPayload data) {
        return handleRun(data.getData(), data.getMetadata());
    }

    //This is semantically the same as `io.hyperfoil.tools.horreum.datastore.DatastoreResponse`
    public static class DatastoreRequestPayload {
        private final JsonNode data;
        private final JsonNode metadata;

        public DatastoreRequestPayload(JsonNode data, JsonNode metadata) {
            this.data = data;
            this.metadata = metadata;
        }

        public JsonNode getData() {
            return data;
        }

        public JsonNode getMetadata() {
            return metadata;
        }
    }


}

