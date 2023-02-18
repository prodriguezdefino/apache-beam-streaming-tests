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

import com.google.bigtable.v2.Mutation;
import com.google.cloud.pso.beam.common.transport.ErrorTransport;
import com.google.cloud.pso.beam.pipelines.options.BigTableWriteOptions;
import com.google.cloud.pso.beam.transforms.aggregations.AggregationResultTransport;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import org.apache.beam.sdk.io.gcp.bigtable.BigtableIO;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.apache.beam.sdk.values.TupleTag;
import org.joda.time.Instant;

/**
 * Transform the <AggregationResultTransport> and writes it into BigTable.
 */
public class StoreInBigTable
        extends PTransform<PCollection<AggregationResultTransport>, PDone> {

  public static final TupleTag<ErrorTransport> FAILED_EVENTS = new TupleTag<>() {
  };

  StoreInBigTable() {
  }

  public static StoreInBigTable store() {
    return new StoreInBigTable();
  }

  public static TupleTag<ErrorTransport> failedEvents() {
    return FAILED_EVENTS;
  }

  @Override
  public PDone expand(PCollection<AggregationResultTransport> input) {
    var options = input.getPipeline().getOptions().as(BigTableWriteOptions.class);

    input
            .apply("TransformToMutations",
                    ParDo.of(
                            new TransformAggregationToMutation(
                                    options.getBTColumnFamilyName())))
            .apply("WriteOnBigTable",
                    BigtableIO
                            .write()
                            .withProjectId(options.getBTProjectId())
                            .withInstanceId(options.getBTInstanceId())
                            .withTableId(options.getBTTableId()));

    return PDone.in(input.getPipeline());
  }

  private static class TransformAggregationToMutation
          extends DoFn<AggregationResultTransport, KV<ByteString, Iterable<Mutation>>> {

    record MutationInfo(ByteString key, Instant timestamp, BoundedWindow window) {

    }

    private final String columnFamilyName;
    private Map<MutationInfo, List<Mutation>> mutations;

    public TransformAggregationToMutation(String columnFamilyName) {
      this.columnFamilyName = columnFamilyName;
    }

    @StartBundle
    public void startBundle() {
      mutations = Maps.newHashMap();
    }

    @ProcessElement
    public void processElement(ProcessContext context, BoundedWindow window) {
      var result = context.element();
      mutations
              .computeIfAbsent(
                      new MutationInfo(
                              ByteString.copyFromUtf8((String) result.getAggregationKey()),
                              context.timestamp(),
                              window),
                      mutInfo -> Lists.newArrayList())
              .add(
                      Mutation.newBuilder()
                              .setSetCell(
                                      Mutation.SetCell
                                              .newBuilder()
                                              .setTimestampMicros(result.getEventEpochInMillis()
                                                      .orElse(
                                                              Instant.now().getMillis() * 1000))
                                              .setValue(
                                                      ByteString.copyFrom(
                                                              Longs.toByteArray(
                                                                      (Long) result.getResult())))
                                              .setColumnQualifier(
                                                      ByteString.copyFromUtf8(
                                                              result.getAggregationName()))
                                              .setFamilyName(columnFamilyName)
                                              .build())
                              .build());
    }

    @FinishBundle
    public void finishBundle(FinishBundleContext context) {
      mutations.entrySet().forEach(entry -> {
        context.output(
                KV.of(
                        entry.getKey().key(),
                        entry.getValue()),
                entry.getKey().timestamp(),
                entry.getKey().window());
      });
    }
  }
}
