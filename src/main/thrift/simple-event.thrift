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
 
namespace java com.google.cloud.dataflow.example.thrift

struct UniqueEvent {
  1: required string hostName
  2: required EpochID hostBootTime
  3: required string eventName
}

/**
 * Client ID
 */
typedef i64 ClientGID

/**
 * Logical Epoch id per Client
 */
typedef i64 EpochID

/** 
 * Log Event schema for test logs
 */
struct LogEvent {
   /** Log event identifier */
   1: required string       uuid,

   /** Log event category */
   2: optional string       category,

   /** Client ID */
   3: optional ClientGID    clientId,

   /** Logical epoch id incremented by client */
   4: optional EpochID	    epochId,
   
   /** Log event time stamp */
   5: optional string       createTimestamp,

   /** Log data */
   6: optional string       data,

   /** LogEvent  source */
   7: optional string       source,

   /** LogEvent  destination */
   8: optional string       destination,

   /** LogEvent  info */
   9: optional UniqueEvent  event,
   
}

