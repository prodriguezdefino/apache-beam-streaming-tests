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

typedef i64 ExternalId

typedef i64 Epoch

struct CompoundEvent {
   1: required string                uuid,
   2: optional string                name,
   3: optional ExternalId            externalId,
   4: optional Epoch	               clientEpoch,
   5: optional string                createdTimestamp,
   6: optional string                data,
   7: optional string                source,
   8: optional string                destination,
   9: optional set<SimpleEvent>      events,
   10: optional list<Carrier>        carriers
}

struct MapCompoundEvent {
   1: required map<string, CompoundEvent>     eventMap,
}

struct ListMapCompoundEvent {
   1: required list<map<string, CompoundEvent>>        listOfEventMaps,
}
