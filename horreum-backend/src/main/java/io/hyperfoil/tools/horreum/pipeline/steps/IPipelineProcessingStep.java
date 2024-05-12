package io.hyperfoil.tools.horreum.pipeline.steps;

import com.fasterxml.jackson.databind.JsonNode;

public interface IPipelineProcessingStep<P, R> extends IPipelineStep {
    R run (P data);

}
