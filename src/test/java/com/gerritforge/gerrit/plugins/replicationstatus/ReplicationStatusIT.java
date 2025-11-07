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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.extensions.restapi.Url.encode;
import static com.googlesource.gerrit.plugins.replication.ReplicationState.RefPushResult;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.ReplicationType.PULL;
import static com.gerritforge.gerrit.plugins.replicationstatus.ReplicationStatus.ReplicationType.PUSH;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.config.SitePaths;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.replication.events.RefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.events.RemoteRefReplicationEvent;
import com.googlesource.gerrit.plugins.replication.events.ReplicationScheduledEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(
    name = "replication-status",
    sysModule = "com.gerritforge.gerrit.plugins.replicationstatus.Module")
public class ReplicationStatusIT extends LightweightPluginDaemonTest {
  private static final String REF_MASTER = Constants.R_HEADS + Constants.MASTER;
  private static final String REMOTE = "ssh://some.remote.host";

  private static final Gson gson = newGson();

  @Inject protected SitePaths sitePaths;
  @Inject private ProjectOperations projectOperations;

  private EventHandler eventHandler;

  @Before
  public void setUp() throws IOException {
    eventHandler = plugin.getSysInjector().getInstance(EventHandler.class);
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId-1")
  public void shouldBeOKSuccessForAdminUsers() throws Exception {
    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));
    result.assertOK();

    assertThat(contentWithoutMagicJson(result)).isEqualTo(emptyReplicationStatus(project, REMOTE));
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId-1")
  public void shouldBeOKSuccessForProjectOwners() throws Exception {
    makeProjectOwner(user, project);
    RestResponse result = userRestSession.get(endpoint(project, REMOTE));
    result.assertOK();

    assertThat(contentWithoutMagicJson(result)).isEqualTo(emptyReplicationStatus(project, REMOTE));
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId-1")
  public void shouldBeForbiddenForNonProjectOwners() throws Exception {
    RestResponse result = userRestSession.get(endpoint(project, REMOTE));
    result.assertForbidden();

    assertThat(result.getEntityContent()).contains("Administrate Server or Project owner required");
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId-1")
  public void shouldBeForbiddenForAnonymousUsers() throws Exception {
    RestResponse result = anonymousRestSession.get(endpoint(project, REMOTE));
    result.assertForbidden();

    assertThat(result.getEntityContent()).contains("Administrate Server or Project owner required");
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId-1")
  public void shouldNotReportStatusOfReplicationsGeneratedOnDifferentNodes() throws Exception {
    eventHandler.onEvent(
        successReplicatedEvent("testInstanceId-2", System.currentTimeMillis(), REMOTE));

    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));
    result.assertOK();

    assertThat(contentWithoutMagicJson(result)).isEqualTo(emptyReplicationStatus(project, REMOTE));
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId-1")
  public void shouldReturnSuccessfulProjectReplicationStatus() throws Exception {
    long eventCreatedOn = System.currentTimeMillis();

    eventHandler.onEvent(successReplicatedEvent("testInstanceId-1", eventCreatedOn, REMOTE));
    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));

    result.assertOK();
    assertThat(contentWithoutMagicJson(result))
        .isEqualTo(successReplicationStatus(PUSH, REMOTE, project, eventCreatedOn));
  }

  @Test
  public void shouldReturnScheduledProjectReplicationStatus() throws Exception {
    long eventCreatedOn = System.currentTimeMillis();

    eventHandler.onEvent(scheduledEvent(null, eventCreatedOn, REF_MASTER, REMOTE));
    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));

    result.assertOK();
    assertThat(contentWithoutMagicJson(result))
        .isEqualTo(scheduledReplicationStatus(PUSH, REMOTE, project, eventCreatedOn));
  }

  @Test
  public void shouldConsumeEventsThatHaveNoInstanceId() throws Exception {
    long eventCreatedOn = System.currentTimeMillis();

    eventHandler.onEvent(successReplicatedEvent(null, eventCreatedOn, REMOTE));
    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));

    result.assertOK();
    assertThat(contentWithoutMagicJson(result))
        .isEqualTo(successReplicationStatus(PUSH, REMOTE, project, eventCreatedOn));
  }

  @Test
  public void shouldNotConsumeEventsWhenNodeInstanceIdIsNullButEventHasIt() throws Exception {
    eventHandler.onEvent(
        successReplicatedEvent("testInstanceId-2", System.currentTimeMillis(), REMOTE));

    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));
    result.assertOK();

    assertThat(contentWithoutMagicJson(result)).isEqualTo(emptyReplicationStatus(project, REMOTE));
  }

  @Test
  public void shouldConsumeEventsWhenBothNodeAndEventHaveNoInstanceId() throws Exception {
    long eventCreatedOn = System.currentTimeMillis();

    eventHandler.onEvent(successReplicatedEvent(null, eventCreatedOn, REMOTE));
    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));

    result.assertOK();
    assertThat(contentWithoutMagicJson(result))
        .isEqualTo(successReplicationStatus(PUSH, REMOTE, project, eventCreatedOn));
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId-1")
  public void shouldShowFailedInPayloadWhenRefCouldntBeReplicated() throws Exception {
    long eventCreatedOn = System.currentTimeMillis();

    eventHandler.onEvent(failedReplicatedEvent("testInstanceId-1", eventCreatedOn, REMOTE));
    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));

    result.assertOK();
    assertThat(contentWithoutMagicJson(result))
        .isEqualTo(failedReplicationStatus(PUSH, REMOTE, project, eventCreatedOn));
  }

  @Test
  public void shouldReturnScheduledProjectFetchReplicationStatus() throws Exception {
    long eventCreatedOn = System.currentTimeMillis();

    eventHandler.onEvent(fetchScheduledEvent(null, eventCreatedOn, REF_MASTER, REMOTE));
    RestResponse result = adminRestSession.get(endpoint(project, REMOTE));

    result.assertOK();
    assertThat(contentWithoutMagicJson(result))
        .isEqualTo(scheduledReplicationStatus(PULL, REMOTE, project, eventCreatedOn));
  }

  private String contentWithoutMagicJson(RestResponse response) throws IOException {
    return response.getEntityContent().substring(RestApiServlet.JSON_MAGIC.length);
  }

  private AccountGroup.UUID createGroup(String name, TestAccount member) throws RestApiException {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name(name);
    groupInput.members = Collections.singletonList(String.valueOf(member.id().get()));
    return AccountGroup.uuid(gApi.groups().create(groupInput).get().id);
  }

  private void makeProjectOwner(TestAccount user, Project.NameKey project) throws RestApiException {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allow(Permission.OWNER)
                .ref(AccessSection.ALL)
                .group(createGroup("projectOwners", user)))
        .update();
  }

  private RefReplicatedEvent replicatedEvent(
      @Nullable String instanceId,
      long when,
      String ref,
      String remote,
      RefPushResult status,
      RemoteRefUpdate.Status refStatus)
      throws URISyntaxException {
    RefReplicatedEvent replicatedEvent =
        new RefReplicatedEvent(project.get(), ref, new URIish(remote), status, refStatus);
    replicatedEvent.instanceId = instanceId;
    replicatedEvent.eventCreatedOn = when;

    return replicatedEvent;
  }

  private ReplicationScheduledEvent scheduledEvent(
      @Nullable String instanceId, long when, String ref, String remote) throws URISyntaxException {
    ReplicationScheduledEvent scheduledEvent =
        new ReplicationScheduledEvent(project.get(), ref, new URIish(remote));
    scheduledEvent.instanceId = instanceId;
    scheduledEvent.eventCreatedOn = when;

    return scheduledEvent;
  }

  private RemoteRefReplicationEvent fetchScheduledEvent(
      @Nullable String instanceId, long when, String ref, String remote) throws URISyntaxException {
    RemoteRefReplicationEvent scheduledFetchEvent =
        new RemoteRefReplicationEvent(
            "fetch-ref-replication-scheduled", project.get(), ref, new URIish(remote), null);
    scheduledFetchEvent.instanceId = instanceId;
    scheduledFetchEvent.eventCreatedOn = when;

    return scheduledFetchEvent;
  }

  private RefReplicatedEvent successReplicatedEvent(
      @Nullable String instanceId, long when, String remoteUrl) throws URISyntaxException {

    return replicatedEvent(
        instanceId,
        when,
        REF_MASTER,
        remoteUrl,
        RefPushResult.SUCCEEDED,
        RemoteRefUpdate.Status.OK);
  }

  private RefReplicatedEvent failedReplicatedEvent(
      @Nullable String instanceId, long when, String remoteUrl) throws URISyntaxException {

    return replicatedEvent(
        instanceId,
        when,
        REF_MASTER,
        remoteUrl,
        RefPushResult.FAILED,
        RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD);
  }

  private static String endpoint(Project.NameKey project, String remote) {
    return String.format(
        "/projects/%s/remotes/%s/replication-status", project.get(), encode(remote));
  }

  private String emptyReplicationStatus(Project.NameKey project, String remoteUrl)
      throws URISyntaxException {
    return gson.toJson(
        ProjectReplicationStatus.create(
            ImmutableMap.of(remoteUrl, RemoteReplicationStatus.create(Collections.emptyMap())),
            ProjectReplicationStatus.ProjectReplicationStatusResult.OK,
            project.get()));
  }

  private String successReplicationStatus(
      ReplicationStatus.ReplicationType type, String remote, Project.NameKey project, long when)
      throws URISyntaxException {
    return successReplicationStatus(
        type, remote, project, when, ReplicationStatus.ReplicationStatusResult.SUCCEEDED);
  }

  private String scheduledReplicationStatus(
      ReplicationStatus.ReplicationType type, String remote, Project.NameKey project, long when)
      throws URISyntaxException {
    return successReplicationStatus(
        type, remote, project, when, ReplicationStatus.ReplicationStatusResult.SCHEDULED);
  }

  private String successReplicationStatus(
      ReplicationStatus.ReplicationType type,
      String remote,
      Project.NameKey project,
      long when,
      ReplicationStatus.ReplicationStatusResult replicationStatusResult)
      throws URISyntaxException {
    return projectReplicationStatus(
        type,
        remote,
        project,
        when,
        ProjectReplicationStatus.ProjectReplicationStatusResult.OK,
        replicationStatusResult);
  }

  private String failedReplicationStatus(
      ReplicationStatus.ReplicationType type, String remote, Project.NameKey project, long when)
      throws URISyntaxException {
    return projectReplicationStatus(
        type,
        remote,
        project,
        when,
        ProjectReplicationStatus.ProjectReplicationStatusResult.FAILED,
        ReplicationStatus.ReplicationStatusResult.FAILED);
  }

  private String projectReplicationStatus(
      ReplicationStatus.ReplicationType type,
      String remoteUrl,
      Project.NameKey project,
      long when,
      ProjectReplicationStatus.ProjectReplicationStatusResult projectReplicationStatusResult,
      ReplicationStatus.ReplicationStatusResult replicationStatusResult) {
    return gson.toJson(
        ProjectReplicationStatus.create(
            ImmutableMap.of(
                remoteUrl,
                RemoteReplicationStatus.create(
                    ImmutableMap.of(
                        REF_MASTER,
                        ReplicationStatus.create(type, replicationStatusResult, when)))),
            projectReplicationStatusResult,
            project.get()));
  }
}
