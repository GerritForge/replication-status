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

import static com.google.gerrit.server.project.ProjectResource.PROJECT_KIND;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatusProjectRemoteResource.REPLICATION_STATUS_PROJECT_REMOTE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.inject.Scopes;

class ReplicationStatusApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(ReplicationStatusAction.class).in(Scopes.SINGLETON);
    DynamicMap.mapOf(binder(), REPLICATION_STATUS_PROJECT_REMOTE_KIND);
    child(PROJECT_KIND, "remotes").to(ReplicationStatusProjectRemoteCollection.class);
    get(REPLICATION_STATUS_PROJECT_REMOTE_KIND, "replication-status")
        .to(ReplicationStatusAction.class);
  }
}
