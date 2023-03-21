/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.pso.beam.pipelines.transforms;

import com.google.cloud.pso.beam.common.transport.ErrorTransport;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PInput;
import org.apache.beam.sdk.values.POutput;
import org.apache.beam.sdk.values.PValue;
import org.apache.beam.sdk.values.TupleTag;

/** Represents the possible transformation errors for the BigTable Mutation objects */
public class MutationTransformationErrors implements POutput {

  private final Pipeline pipeline;
  private final PCollection<ErrorTransport> failedTransformations;
  private final TupleTag<ErrorTransport> failedTransformationsTag;

  MutationTransformationErrors(
      Pipeline pipeline,
      PCollection<ErrorTransport> failedTransformations,
      TupleTag<ErrorTransport> failedTransformationsTag) {
    this.pipeline = pipeline;
    this.failedTransformations = failedTransformations;
    this.failedTransformationsTag = failedTransformationsTag;
  }

  public static MutationTransformationErrors of(
      Pipeline pipeline,
      PCollection<ErrorTransport> failedTransformations,
      TupleTag<ErrorTransport> failedTransformationsTag) {
    return new MutationTransformationErrors(
        pipeline, failedTransformations, failedTransformationsTag);
  }

  @Override
  public Pipeline getPipeline() {
    return pipeline;
  }

  @Override
  public Map<TupleTag<?>, PValue> expand() {
    ImmutableMap.Builder<TupleTag<?>, PValue> output = ImmutableMap.builder();
    if (failedTransformationsTag != null) {
      output.put(failedTransformationsTag, Preconditions.checkNotNull(failedTransformations));
    }
    return output.build();
  }

  @Override
  public void finishSpecifyingOutput(
      String transformName, PInput input, PTransform<?, ?> transform) {}

  public PCollection<ErrorTransport> getFailedMutationTransformations() {
    if (failedTransformations == null) {
      throw new IllegalStateException("The failed transformations should not be null.");
    }
    return failedTransformations;
  }
}
