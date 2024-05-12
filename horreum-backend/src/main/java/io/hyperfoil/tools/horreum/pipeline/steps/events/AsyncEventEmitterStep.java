package io.hyperfoil.tools.horreum.pipeline.steps.events;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineTerminatingStep;
import io.quarkus.logging.Log;

public class AsyncEventEmitterStep implements IPipelineTerminatingStep<JsonNode> {

    private final AsyncEventChannels channel;

    public AsyncEventEmitterStep(AsyncEventChannels channel) {
        this.channel = channel;
    }

    @Override
    public void run(JsonNode data) {
        Log.info("Emitting payload to event channels: " + channel);
    }
}
