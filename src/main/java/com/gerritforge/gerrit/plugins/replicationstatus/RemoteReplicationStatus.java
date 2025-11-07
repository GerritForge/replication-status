// Copyright (C) 2025 GerritForge, Inc.
//
// Licensed under the BSL 1.1 (the "License");
// you may not use this file except in compliance with the License.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.gerrit.plugins.replicationstatus;

import com.google.auto.value.AutoValue;
import java.util.Map;

@AutoValue
public abstract class RemoteReplicationStatus {
  static RemoteReplicationStatus create(Map<String, ReplicationStatus> status) {
    return new AutoValue_RemoteReplicationStatus(status);
  }

  public abstract Map<String, ReplicationStatus> status();
}
