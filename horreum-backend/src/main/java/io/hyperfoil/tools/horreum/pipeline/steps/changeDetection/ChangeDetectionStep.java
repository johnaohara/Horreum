package io.hyperfoil.tools.horreum.pipeline.steps.changeDetection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionException;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionModel;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;

import java.util.ArrayList;
import java.util.List;

public abstract class ChangeDetectionStep implements IPipelineProcessingStep<JsonNode, JsonNode>, ChangeDetectionModel {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public JsonNode run(JsonNode data) {

        List<String> changes = new ArrayList<>();

        //TODO:: needs more consideration
        DataPointDAO dataPointDAO = new DataPointDAO();
        dataPointDAO.value = data.asDouble();
        try {
            analyze(List.of(dataPointDAO), change -> {
                changes.add(change.description);
            });
        } catch (ChangeDetectionException e) {
            throw new RuntimeException(e);
        }
        ArrayNode result = objectMapper.createArrayNode();

        for (String change : changes) {
            result.add(change);
        }

        return result;
    }
}
