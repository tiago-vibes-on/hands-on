# Local Jenkins CI/CD for k3d

Jenkins runs only on this computer. The controller is a Docker Compose service
with a private named volume and a UI bound to `127.0.0.1:15180`. The build
agent is a background process in the existing Ubuntu WSL installation, not
another VM or WSL distribution. GitHub is the source repository; neither
GitHub-hosted runners nor Floci/AWS execute these builds.

Fifteen service jobs share the WSL agent's single executor:

| Service | Manual uncommitted build | Trusted `main` build | Local deploy |
| --- | --- | --- | --- |
| Core | `hero-association-core-build-worktree` | `hero-association-core-build-main` | `hero-association-core-deploy-local` |
| BFF | `hero-association-bff-build-worktree` | `hero-association-bff-build-main` | `hero-association-bff-deploy-local` |
| Expedition | `hero-association-expedition-build-worktree` | `hero-association-expedition-build-main` | `hero-association-expedition-deploy-local` |
| Market | `hero-association-market-build-worktree` | `hero-association-market-build-main` | `hero-association-market-deploy-local` |
| Frontend | `hero-association-frontend-build-worktree` | `hero-association-frontend-build-main` | `hero-association-frontend-deploy-local` |

Each build tests its backend service against disposable k3d dependencies
(frontend builds run lint and build), then creates one candidate image and a
checksummed six-image archive using the other five images currently running
in k3d. Disposable k3d E2E tests the exact combination without changing daily
data. Both worktree and trusted-`main` builds are started manually and never deploy.
Each deploy job is started separately with a verified artifact ID. A Jenkins
lock serializes trusted-`main` builds; a manual build of any service verifies
its candidate against the images currently deployed in k3d.
Deployment never rebuilds an image and rejects a baseline that changed after the build. The latest
successful deploy of a service wins. The two old combined jobs are disabled
without deleting their history.

No service job resets Core data or restarts normal Compose development.
Images go directly from the verified local archive into k3d; this setup does
not add a registry.

## Requirements

- Docker Desktop with WSL integration (or a Linux Docker engine) and Docker
  Compose. Every service build and deploy job requires a running,
  bootstrapped Hero Association k3d cluster.
- Expedition staged in k3d with its private Redis and RabbitMQ, plus
  `enable-expedition-integration.sh` applied. The archived and k3d browser
  gates now exercise the Map journey on every service promotion.
- Java 25, Node.js 24/npm, Docker CLI, `kubectl`, `git`, `rsync`, `openssl`, and
  `curl` in the same WSL distribution. The existing ignored
  `deploy/k3d/.tools/k3d` binary is used if `k3d` is not on `PATH`.
- The readable, isolated `deploy/k3d/.kubeconfig` with current context
  `k3d-hero-association`. The agent uses the original ignored file from the WSL project through
  `HERO_ASSOCIATION_K3D_KUBECONFIG`; it never copies the credential into the
  Jenkins workspace. The disposable E2E runner likewise reads the original
  ignored `tls/certs/local-ca.crt` through
  `HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE`; the snapshot does not contain the
  local CA certificate or its private key.
- For the `main` jobs, a GitHub `main` commit containing these service scripts.
  They check out `https://github.com/tiago-vibes-on/hands-on.git` rather than
  uncommitted local changes.
- Enough free RAM and disk for the existing k3d stack, Jenkins, the JVM/Node
  builds, Playwright containers, and retained image archives.

The root `.gitattributes` pins the extensionless Maven wrappers to LF. Keep
that rule: CRLF checkout makes their Linux shebangs unexecutable.

The Jenkins controller can start while k3d is stopped, but builds and deploys
require all six application services restored to full k3d mode. WSL and
Docker must be running for builds. No job polls Git
or reacts to a push; start a build or deploy explicitly when the cluster is
ready. Normal hot-reload development remains independent.

## One-time setup

From `hero-association/ci/jenkins` in WSL:

```bash
./setup-local.sh
docker compose -f compose.yaml up --detach --build
./enroll-agent.sh
./install-agent-service.sh
```

`setup-local.sh` validates the installed tools and k3d context. It creates
ignored, owner-only `.env` and `agent.env` files without replacing them on
later runs. `.env` contains a generated Jenkins admin password and the agent
workspace path; `agent.env` records the exact Java, Node, k3d, and kubeconfig
paths. Inspect or update `agent.env` if those tools move. Do not commit these
files. The default agent workspace is under the current user's
`~/.local/state/hero-association-jenkins/agent`.

`enroll-agent.sh` waits for Jenkins, retrieves the agent's connection secret,
and downloads that controller's matching `agent.jar`. Both files are ignored
and owner-only. Re-run it if the Jenkins controller volume is replaced, then
restart the agent service. The service installer creates a user-level systemd
unit and starts it; no `sudo` is needed. The agent connects over Jenkins
WebSocket, so no extra Jenkins agent TCP port is published.

Open <http://localhost:15180> from the Windows browser. Sign in as `admin`;
the generated password is in the ignored `.env` file:

```bash
sed -n 's/^JENKINS_ADMIN_PASSWORD=//p' .env
```

Worktree builds are available immediately. The six trusted-`main` jobs also
run only when selected manually; each checks out GitHub `main` at build time.
A successful build records an artifact ID but does not deploy it.

When `jenkins.yaml` changes locally, wait for running jobs to finish, then
run `docker compose -f compose.yaml restart jenkins` in this directory to
reload the generated jobs. The `main` jobs still use GitHub's committed code.

The jobs are intentionally not configured for pull requests or forks. The
single agent executor allows one build or deployment at a time, and the Jenkins controller itself has zero executors.

## Build and deploy one service

Open <http://localhost:15180> and select the matching
`hero-association-<service>-build-worktree` job, then choose **Build Now**.
It snapshots tracked, staged, unstaged, and non-ignored new files before
building only that service. Jenkins runs the matching disposable k3d component
gate for a backend service, or frontend lint and build, then builds the image,
assembles a six-image archive with the current k3d Core/BFF/Expedition/Market/Assets/frontend
baseline, and runs the disposable k3d archive suite. The full archive
is retained under the WSL agent work directory; the build record includes
`artifact-id.txt`, the manifest, and the E2E result. If you edit source while
a snapshot is being taken, rerun the build. Ignored files and credentials
are not copied.

To deploy, open `hero-association-<service>-deploy-local`, choose **Build with
Parameters**, and paste the successful build artifact ID into `ARTIFACT_ID`.
The deploy job checks the five unchanged k3d service images against the
archive, imports and rolls only the selected service, verifies all running
Pod image digests, then runs the k3d browser, Map, and market k6 suites. If the
baseline has changed, build again before deploying. If a post-rollout gate
fails, the selected service image is restored. No deploy job rebuilds an
image. Trusted-`main` builds use the same explicit deploy jobs as worktree
builds. The controller uses the Lockable Resources plugin to serialize
trusted-`main` builds; deployment remains a separate manual action.

The retained archives consume disk. Do not delete an archive before its
deployment is finished. These are local study artifacts, not releases.

## Status and stopping

```bash
docker compose -f compose.yaml ps
docker compose -f compose.yaml logs --follow jenkins
systemctl --user status hero-association-jenkins-agent.service
journalctl --user -u hero-association-jenkins-agent.service --follow
```

To stop local CI without deleting Jenkins history or the application's k3d
data:

```bash
systemctl --user stop hero-association-jenkins-agent.service
docker compose -f compose.yaml down
```

To start it again, run `docker compose -f compose.yaml up --detach` and
`systemctl --user start hero-association-jenkins-agent.service`. Do not run
`docker compose down --volumes` unless you intentionally want to discard the
Jenkins controller's users, configuration, and build history. The systemd
service can be disabled with
`systemctl --user disable --now hero-association-jenkins-agent.service`.

## Security and scope

The controller is reachable only through host loopback, with sign-in required.
Its container has no Docker socket or kubeconfig. The WSL build agent does have
access to Docker and the isolated k3d kubeconfig. The worktree build jobs do not invoke deployment, but they execute local
uncommitted code on that privileged agent and are not a security sandbox. Run only your own trusted
edits there. Do not add pull-request builds or expose the controller to the
LAN without a separate security design. Builds run in the agent's workspace
rather than the developer working copy.

The service jobs use the build, archive-E2E, and k3d promotion commands in
`pipeline/` through their dedicated runners. The older all-in-one pipeline
remains available manually but its Jenkins jobs are disabled. Full archives
remain under the agent work directory for provenance; this is a local lab,
not a production CI server or AWS deployment.
