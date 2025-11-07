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

import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.Key;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.ReplicationStatusResult;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.ReplicationStatusResult.SCHEDULED;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.ReplicationType;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.ReplicationType.PULL;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.ReplicationType.PUSH;

import com.google.common.cache.Cache;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.replication.events.RefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.events.RemoteRefReplicationEvent;
import com.googlesource.gerrit.plugins.replication.events.ReplicationScheduledEvent;
import java.util.Optional;

class EventHandler implements EventListener {
  private final Cache<ReplicationStatus.Key, ReplicationStatus> replicationStatusCache;
  private final String nodeInstanceId;

  @Inject
  EventHandler(
      @Named(ReplicationStatus.CACHE_NAME)
          Cache<ReplicationStatus.Key, ReplicationStatus> replicationStatusCache,
      @Nullable @GerritInstanceId String nodeInstanceId) {
    this.replicationStatusCache = replicationStatusCache;
    this.nodeInstanceId = nodeInstanceId;
  }

  @Override
  public void onEvent(Event event) {
    if (shouldConsume(event) && (event instanceof RemoteRefReplicationEvent)) {
      RemoteRefReplicationEvent replicationEvent = (RemoteRefReplicationEvent) event;
      putCacheEntry(
          replicationType(event),
          replicationEvent,
          replicationEvent.targetUri,
          Optional.ofNullable(replicationEvent.status).orElse(SCHEDULED.name()));
    }
  }

  private <T extends RemoteRefReplicationEvent> void putCacheEntry(
      ReplicationType type, T replicationEvent, String remote, String status) {
    Key cacheKey =
        Key.create(replicationEvent.getProjectNameKey(), remote, replicationEvent.getRefName());

    replicationStatusCache.put(
        cacheKey,
        ReplicationStatus.create(
            type, ReplicationStatusResult.fromString(status), replicationEvent.eventCreatedOn));
  }

  private boolean shouldConsume(Event event) {
    return (nodeInstanceId == null && event.instanceId == null)
        || (nodeInstanceId != null && nodeInstanceId.equals(event.instanceId));
  }

  private static ReplicationType replicationType(Event event) {
    if (event instanceof ReplicationScheduledEvent || event instanceof RefReplicatedEvent) {
      return PUSH;
    }
    return PULL;
  }
}
