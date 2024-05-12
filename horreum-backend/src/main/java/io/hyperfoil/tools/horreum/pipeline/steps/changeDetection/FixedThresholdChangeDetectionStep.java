package io.hyperfoil.tools.horreum.pipeline.steps.changeDetection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.api.data.changeDetection.FixedThresholdDetectionConfig;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionException;
import io.hyperfoil.tools.horreum.changedetection.ChangeDetectionModel;
import io.hyperfoil.tools.horreum.changedetection.ModelType;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;
import io.quarkus.logging.Log;

import java.util.List;
import java.util.function.Consumer;

public class FixedThresholdChangeDetectionStep extends ChangeDetectionStep {

    private final FixedThresholdDetectionConfig config;

    public FixedThresholdChangeDetectionStep(FixedThresholdDetectionConfig config) {
        this.config = config;
    }

    @Override
    public ConditionConfig config() {
        return null;
    }

    @Override
    public ChangeDetectionModelType type() {
        return null;
    }

    @Override
    public void analyze(List<DataPointDAO> dataPoints, JsonNode configuration, Consumer<ChangeDAO> changeConsumer) throws ChangeDetectionException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void analyze(List<DataPointDAO> dataPoints, Consumer<ChangeDAO> changeConsumer) throws ChangeDetectionException {
        DataPointDAO dp = dataPoints.get(0);

        if (config.min.enabled) {
            if ((!config.min.inclusive && dp.value <= config.min.value) || dp.value < config.min.value) {
                ChangeDAO c = ChangeDAO.fromDatapoint(dp);
                c.description = String.format("%f is below lower bound %f (%s)", dp.value, config.min.value, config.min.inclusive ? "inclusive" : "exclusive");
                Log.debug(c.description);
                changeConsumer.accept(c);
                return;
            }
        }
        if (config.max.enabled) {
            if ((!config.max.inclusive && dp.value >= config.max.value) || dp.value > config.max.value) {
                ChangeDAO c = ChangeDAO.fromDatapoint(dp);
                c.description = String.format("%f is above upper bound %f (%s)", dp.value, config.max.value, config.max.inclusive ? "inclusive" : "exclusive");
                Log.debug(c.description);
                changeConsumer.accept(c);
            }
        }


    }

    @Override
    public ModelType getType() {
        return null;
    }


}
