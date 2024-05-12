package io.hyperfoil.tools.horreum.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineProcessingStep;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineSourceStep;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineStep;
import io.hyperfoil.tools.horreum.pipeline.steps.IPipelineTerminatingStep;
import io.quarkus.logging.Log;

import java.util.LinkedList;
import java.util.List;

/*
    Simple pipeline implementation
*/
public class Pipeline implements IPipeline {
    private List<IPipelineStep> steps = new LinkedList<>();

    public Pipeline(List<IPipelineStep> steps) {
        this.steps = steps;
    }

    @Override
    public void run() {
        run(null);
    }

    @Override
    public void validate() throws InvalidPipelineException {
        boolean valid = true;
        IPipelineStep prevStep = null;
        for (IPipelineStep currentStep: steps) {
            if ( prevStep == null ){
                prevStep = currentStep;
                continue;
            }

            //TODO; compare previous return type with current input type
        }

    }

    @Override
    public void run(JsonNode input) {
        Object curJson = input;

        //TODO:: review inheritance model
        for (IPipelineStep currentStep: steps) {
            Log.info("Running step: " + currentStep.getClass().getName());
            if (currentStep instanceof IPipelineProcessingStep) {
                curJson = ((IPipelineProcessingStep) currentStep).run(curJson);
            } else if (currentStep instanceof IPipelineSourceStep) {
                curJson = ((IPipelineSourceStep) currentStep).run();
            } else if (currentStep instanceof IPipelineTerminatingStep) {
                ((IPipelineTerminatingStep) currentStep).run(curJson);
            } else {
                throw new RuntimeException("Unknown step type: " + currentStep.getClass().getName());
            }
            //run and cache each steps output
        }

    }


    public static class Builder{

        private List<IPipelineStep> steps = new LinkedList<>();

        Builder addStep(IPipelineStep step){
            steps.add(step);
            return this;
        }

        public Pipeline build(){
            return new Pipeline(steps);
        }
    }
}
