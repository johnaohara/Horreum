package io.hyperfoil.tools.horreum.pipeline.steps;

public interface IPipelineSourceStep<T> extends IPipelineStep {
    T run ();
}
