package io.hyperfoil.tools.horreum.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

public interface IPipeline {
    void run(JsonNode input);
    void run();

    void validate() throws InvalidPipelineException;
}
