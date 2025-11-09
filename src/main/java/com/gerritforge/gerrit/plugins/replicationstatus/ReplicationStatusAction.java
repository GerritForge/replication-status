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

import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.CACHE_NAME;

import com.google.common.cache.Cache;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

class ReplicationStatusAction implements RestReadView<ReplicationStatusProjectRemoteResource> {
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final Cache<ReplicationStatus.Key, ReplicationStatus> replicationStatusCache;

  @Inject
  ReplicationStatusAction(
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      @Named(CACHE_NAME) Cache<ReplicationStatus.Key, ReplicationStatus> replicationStatusCache) {
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.replicationStatusCache = replicationStatusCache;
  }

  @Override
  public Response<ProjectReplicationStatus> apply(ReplicationStatusProjectRemoteResource resource)
      throws AuthException, PermissionBackendException, BadRequestException,
          ResourceConflictException, IOException {

    Project.NameKey projectNameKey = resource.getProjectNameKey();
    String remoteURL = resource.getRemoteUrl();

    checkIsOwnerOrAdmin(projectNameKey);

    ProjectReplicationStatus.ProjectReplicationStatusResult overallStatus =
        ProjectReplicationStatus.ProjectReplicationStatusResult.OK;
    Map<String, RemoteReplicationStatus> remoteStatuses = new HashMap<>();
    try (Repository git = repoManager.openRepository(projectNameKey)) {

      Map<String, ReplicationStatus> refStatuses = new HashMap<>();
      for (Ref r : git.getRefDatabase().getRefs()) {
        ReplicationStatus replicationStatus =
            replicationStatusCache.getIfPresent(
                ReplicationStatus.Key.create(projectNameKey, remoteURL, r.getName()));

        if (replicationStatus != null) {
          refStatuses.put(r.getName(), replicationStatus);
          if (replicationStatus.isFailure()) {
            overallStatus = ProjectReplicationStatus.ProjectReplicationStatusResult.FAILED;
          }
        }
      }
      remoteStatuses.put(remoteURL, RemoteReplicationStatus.create(refStatuses));

      ProjectReplicationStatus projectStatus =
          ProjectReplicationStatus.create(remoteStatuses, overallStatus, projectNameKey.get());

      return Response.ok(projectStatus);

    } catch (RepositoryNotFoundException e) {
      throw new BadRequestException(
          String.format("Project %s does not exist", projectNameKey.get()));
    }
  }

  private void checkIsOwnerOrAdmin(Project.NameKey project) throws AuthException {
    if (!permissionBackend.currentUser().testOrFalse(GlobalPermission.ADMINISTRATE_SERVER)
        && !permissionBackend
            .currentUser()
            .project(project)
            .testOrFalse(ProjectPermission.WRITE_CONFIG)) {
      throw new AuthException("Administrate Server or Project owner required");
    }
  }
}
