package io.hyperfoil.tools.horreum.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.hyperfoil.tools.horreum.api.data.changeDetection.FixThresholdConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.FixedThresholdDetectionConfig;
import io.hyperfoil.tools.horreum.pipeline.examples.CapturingActionStep;
import io.hyperfoil.tools.horreum.pipeline.steps.LoggingStep;
import io.hyperfoil.tools.horreum.pipeline.steps.changeDetection.FixedThresholdChangeDetectionStep;
import io.hyperfoil.tools.horreum.pipeline.steps.dataStore.PostgresDatastoreStep;
import io.hyperfoil.tools.horreum.pipeline.steps.extractor.JaywayJsonPathExtractorStep;
import io.hyperfoil.tools.horreum.pipeline.steps.utils.DatasourcePayloadExtractor;
import io.hyperfoil.tools.horreum.pipeline.utils.DummyRequestGenerationStep;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ComplexPipelineTest {
    static ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void complexLoggingPipelineTest() throws JsonProcessingException, InvalidPipelineException {

        String data = """
                { 
                    "metric" : 1.0,
                    "timestamp" : 1234567890,
                    "tags" : ["tag1", "tag2"]
                }
                """;

        String metadata = """
                { }
                """;


        Pipeline.Builder builder = new Pipeline.Builder();

        //convert from string to Object
        builder.addStep(new DummyRequestGenerationStep(
                objectMapper.readTree(data),
                objectMapper.readTree(metadata))
        );

        //pass payload into datastore retriever
        builder.addStep(new PostgresDatastoreStep(null));

        //extract data JsonNode from DatasourcePayload
        builder.addStep(new DatasourcePayloadExtractor());

        //run extractor
        builder.addStep(new JaywayJsonPathExtractorStep("$.timestamp"));

        //Log Object
        builder.addStep(new LoggingStep("Extracted value"));

        FixedThresholdDetectionConfig fixedThresholdDetectionConfig = new FixedThresholdDetectionConfig();
        fixedThresholdDetectionConfig.builtIn = true;

        FixThresholdConfig maxThresholdConfig = new FixThresholdConfig();
        maxThresholdConfig.inclusive = true;
        maxThresholdConfig.value = 1234567889.0;
        maxThresholdConfig.enabled = true;

        FixThresholdConfig minThresholdConfig = new FixThresholdConfig();
        minThresholdConfig.inclusive = null;
        minThresholdConfig.value = null;
        minThresholdConfig.enabled = false;

        fixedThresholdDetectionConfig.max = maxThresholdConfig;
        fixedThresholdDetectionConfig.min = minThresholdConfig;

        //Run Change Detection
        builder.addStep(new FixedThresholdChangeDetectionStep(fixedThresholdDetectionConfig));

        //Log changes
        builder.addStep(new LoggingStep("Changes detected"));

        AtomicReference<JsonNode> result = new AtomicReference<>();

        builder.addStep(new CapturingActionStep( capturedData -> result.set(capturedData)));

        Pipeline pipeline = builder.build();

        pipeline.validate();
        pipeline.run();

        JsonNode output = result.get();
        assertNotNull(output);
        assertInstanceOf(ArrayNode.class, output);
        assertEquals(1, output.size());

    }
}