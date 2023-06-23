/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
 
namespace java com.google.cloud.pso.beam.generator.thrift

struct SimpleEvent {
  1: required string location
  2: required Epoch startup
  3: required string description
}

struct Carrier {
  1: required string id
  2: required i64 value
}

enum Source {
   SOURCE_1,
   SOURCE_2,
   SOURCE_3,
   SOURCE_4,
   SOURCE_5,
   SOURCE_6,
   SOURCE_7,
   SOURCE_8,
   SOURCE_9,
   SOURCE_10,
   SOURCE_11,
   SOURCE_12,
   SOURCE_13,
   SOURCE_14,
   SOURCE_15,
   SOURCE_16,
   SOURCE_17,
   SOURCE_18,
   SOURCE_19,
   SOURCE_20,
   SOURCE_21,
   SOURCE_22,
   SOURCE_23,
   SOURCE_24,
   SOURCE_25,
   SOURCE_26,
   SOURCE_27,
   SOURCE_28,
   SOURCE_29,
   SOURCE_30
}

typedef i64 ExternalId

typedef i64 Epoch

struct CompoundEvent {
   1: required string                uuid,
   2: optional string                name,
   3: optional ExternalId            externalId,
   4: optional Epoch	               clientEpoch,
   5: optional string                createdTimestamp,
   6: optional string                data,
   7: optional Source                source,
   8: optional string                destination,
   9: optional set<SimpleEvent>      events,
   10: optional list<Carrier>        carriers
}

struct SimplerCompoundEvent {
   1: required string                uuid,
   2: optional string                name,
   3: optional ExternalId            externalId,
   4: optional Epoch	               clientEpoch,
   5: optional string                createdTimestamp,
   6: optional string                data,
   7: optional string                source,
   8: optional string                destination,
   9: optional SimpleEvent           events,
   10: optional SimpleEvent           events2,
   11: optional SimpleEvent           events3,
   12: optional SimpleEvent           events4,
   13: optional SimpleEvent           events5,
}

struct MapCompoundEvent {
   1: required map<string, CompoundEvent>     eventMap,
}

struct ListMapCompoundEvent {
   1: required list<map<string, CompoundEvent>>        listOfEventMaps,
}
