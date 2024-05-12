package io.hyperfoil.tools.horreum.pipeline.steps.dataStore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.datastore.DatastoreResponse;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import jakarta.ws.rs.BadRequestException;

import java.util.Optional;

public class PostgresDatastoreStep extends DatastoreStep<Object> {

    public PostgresDatastoreStep(Object configuration) {
        super(configuration);
    }

    @Override
    public DatastoreResponse handleRun(JsonNode payload, JsonNode metadata, DatastoreConfigDAO config, Optional<String> schemaUriOptional, ObjectMapper mapper) throws BadRequestException {
        throw new RuntimeException("Not Supported");
    }

    @Override
    public DatastoreResponse handleRun(JsonNode payload, JsonNode metadata) throws BadRequestException {
        return new DatastoreResponse(payload, metadata);
    }

    @Override
    public DatastoreType type() {
        return DatastoreType.POSTGRES;
    }

    @Override
    public UploadType uploadType() {
        return UploadType.SINGLE;
    }

    @Override
    public String validateConfig(Object config) {
        return null;
    }

}
