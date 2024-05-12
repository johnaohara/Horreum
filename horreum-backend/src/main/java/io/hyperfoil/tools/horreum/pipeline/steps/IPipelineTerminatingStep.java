package io.hyperfoil.tools.horreum.pipeline.steps;

public interface IPipelineTerminatingStep<T>  extends IPipelineStep {
    void run (T data);
}
