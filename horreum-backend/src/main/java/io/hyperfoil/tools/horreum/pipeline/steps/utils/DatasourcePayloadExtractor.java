package io.hyperfoil.tools.horreum.pipeline.steps.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.datastore.DatastoreResponse;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;

public class DatasourcePayloadExtractor implements IPipelineProcessingStep<DatastoreResponse, JsonNode> {

    @Override
    public JsonNode run(DatastoreResponse data) {
        return data.payload;
    }
}
