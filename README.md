# Replication-status

Record and display the repository's replication status without having to dig
into the Gerrit replication_log

Consumes replication events and updates a cache with the latest replication
status of specific refs to specific remotes.

## License

This project is licensed under the **Business Source License 1.1** (BSL 1.1).
This is a "source-available" license that balances free, open-source-style access to the code
with temporary commercial restrictions.

* The full text of the BSL 1.1 is available in the [LICENSE.md](LICENSE.md) file in this
  repository.
* If your intended use case falls outside the **Additional Use Grant** and you require a
  commercial license, please contact [GerritForge Sales](https://gerritforge.com/contact).


## Dependencies

The @PLUGIN@ depends on the [replication plugin](https://gerrit.googlesource.com/plugins/replication/).
Check the [configuration section](./src/main/resources/Documentation/config.md) for more details.

## REST API

The cache information is then exposed via a project's resource REST endpoint:

```bash
curl -v --user <user> '<gerrit-server>/a/projects/<project-name>/remotes/<remote-url>/replication-status'
```

* <project-name>: an (url-encoded) project repository
* <remote-url>: an (url-encoded) remote URL for the replication

For instance, to assess the replication status of the project `some/project` to
the
`https://github.com/some/project.git` URL, the following endpoint should be
called:

```bash
curl -v --user <user> '<gerrit-server>/a/projects/some%2Fproject/remotes/https%3A%2F%2Fgithub.com%2Fsome%2Fproject.git/replication-status'
```

A payload, similar to this may be returned:

```
{
  "remotes": {
    "https://github.com/some/project.git": {
      "status": {
        "refs/changes/01/1/meta": {
          "type": "PUSH",
          "status": "SUCCEEDED",
          "when": 1626688830
        },
        "refs/changes/03/3/meta": {
          "type": "PUSH",
          "status": "SUCCEEDED",
          "when": 1626688854
        },
        "refs/changes/03/3/1": {
          "type": "PUSH",
          "status": "SUCCEEDED",
          "when": 1626688854
        },
        "refs/changes/02/2/1": {
          "type": "PUSH",
          "status": "SUCCEEDED",
          "when": 1626688844
        },
        "refs/changes/02/2/meta": {
          "type": "PUSH",
          "status": "SUCCEEDED",
          "when": 1626688844
        },
        "refs/changes/01/1/1": {
          "type": "PUSH",
          "status": "SUCCEEDED",
          "when": 1626688830
        },
        "refs/changes/04/4/meta": {
          "type": "PULL",
          "status": "SUCCEEDED",
          "when": 1628000641
        },
        "refs/changes/04/4/1": {
          "type": "PULL",
          "status": "SUCCEEDED",
          "when": 1628000641
        }
      }
    }
  },
  "status": "OK",
  "project": "some/project"
}
```

### HTTP status

The endpoint returns different HTTP response code depending on the result:

* 200 OK - The endpoint was called successfully, and a payload returned
* 404 Not Found - Project was not found
* 500 Failure - An unexpected server error occurred
* 403 Forbidden - The user has no permission to query the endpoint. Only
  Administrators and project owners are allowed

### Overall status

The REST-API response shows a `status` field, which shows the overall
replication-status of the projects for the specified remote.

- `OK` - all the refs have successfully replicated
- `FAILED` - Some refs have not replicated successfully

### TODO

* Does not consume pull-replication events.
