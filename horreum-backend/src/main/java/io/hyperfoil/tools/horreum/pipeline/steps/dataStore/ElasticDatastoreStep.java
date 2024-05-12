package io.hyperfoil.tools.horreum.pipeline.steps.dataStore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.data.datastore.ElasticsearchDatastoreConfig;
import io.hyperfoil.tools.horreum.datastore.DatastoreResponse;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import jakarta.ws.rs.BadRequestException;

import java.util.Optional;

public class ElasticDatastoreStep extends DatastoreStep<ElasticsearchDatastoreConfig> {
    private final Optional<String> schemaUriOptional;

    public ElasticDatastoreStep(ElasticsearchDatastoreConfig config, Optional<String> schemaUriOptional) {
        super(config);
        this.schemaUriOptional = schemaUriOptional;
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
        return DatastoreType.ELASTICSEARCH;
    }

    @Override
    public UploadType uploadType() {
        return UploadType.MUILTI;
    }

    @Override
    public String validateConfig(Object config) {
        return null;
    }

}
