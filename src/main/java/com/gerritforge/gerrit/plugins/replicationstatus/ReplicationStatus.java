// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.gerrit.plugins.replicationstatus;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.gerritforge.gerrit.plugins.replicationstatus.proto.Cache;

@AutoValue
public abstract class ReplicationStatus {
  static final String CACHE_NAME = "replication_status";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static ReplicationStatus create(ReplicationType type, ReplicationStatusResult status, long when) {
    return new AutoValue_ReplicationStatus(type, status, when);
  }

  public abstract ReplicationType type();

  public abstract ReplicationStatusResult status();

  public abstract long when();

  public boolean isFailure() {
    return status().isFailure();
  }

  @AutoValue
  public abstract static class Key {
    static ReplicationStatus.Key create(Project.NameKey projectName, String remote, String ref) {
      return new AutoValue_ReplicationStatus_Key(projectName, remote, ref);
    }

    abstract Project.NameKey projectName();

    abstract String remote();

    abstract String ref();

    enum Serializer implements CacheSerializer<ReplicationStatus.Key> {
      INSTANCE;

      @Override
      public byte[] serialize(ReplicationStatus.Key object) {
        return Protos.toByteArray(
            Cache.ReplicationStatusKeyProto.newBuilder()
                .setProject(object.projectName().get())
                .setRemote(object.remote())
                .setRef(object.ref())
                .build());
      }

      @Override
      public ReplicationStatus.Key deserialize(byte[] in) {
        Cache.ReplicationStatusKeyProto proto =
            Protos.parseUnchecked(Cache.ReplicationStatusKeyProto.parser(), in);
        return ReplicationStatus.Key.create(
            Project.nameKey(proto.getProject()), proto.getRemote(), proto.getRef());
      }
    }
  }

  enum Serializer implements CacheSerializer<ReplicationStatus> {
    INSTANCE;

    @Override
    public byte[] serialize(ReplicationStatus object) {
      return Protos.toByteArray(
          Cache.ReplicationStatusProto.newBuilder()
              .setWhen(object.when())
              .setStatus(object.status().name())
              .setType(object.type().name())
              .build());
    }

    @Override
    public ReplicationStatus deserialize(byte[] in) {
      Cache.ReplicationStatusProto proto =
          Protos.parseUnchecked(Cache.ReplicationStatusProto.parser(), in);

      return ReplicationStatus.create(
          ReplicationStatus.ReplicationType.valueOf(proto.getType()),
          ReplicationStatus.ReplicationStatusResult.valueOf(proto.getStatus()),
          proto.getWhen());
    }
  }

  enum ReplicationStatusResult {
    FAILED,
    NOT_ATTEMPTED,
    SUCCEEDED,
    SCHEDULED,
    UNKNOWN;

    static ReplicationStatusResult fromString(String result) {
      switch (result.toLowerCase()) {
        case "succeeded":
          return SUCCEEDED;
        case "not-attempted":
          return NOT_ATTEMPTED;
        case "scheduled":
          return SCHEDULED;
        case "failed":
          return FAILED;
        default:
          logger.atSevere().log(
              "Could not parse result into a valid replication status: %s", result);
          return UNKNOWN;
      }
    }

    public boolean isFailure() {
      return this == FAILED || this == UNKNOWN;
    }
  }

  enum ReplicationType {
    PUSH,
    PULL;
  }
}
