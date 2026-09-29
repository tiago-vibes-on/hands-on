# Local Jenkins CI/CD for k3d

Jenkins runs only on this computer. The controller is a Docker Compose service
with a private named volume and a UI bound to `127.0.0.1:15180`. The build
agent is a background process in the existing Ubuntu WSL installation, not
another VM or WSL distribution. GitHub is the source repository; neither
GitHub-hosted runners nor Floci/AWS execute these builds.

The single `hero-association-k3d` job polls the repository's trusted `main`
branch every few minutes. It checks out a clean revision on the WSL agent and
runs [the existing gated pipeline](../../pipeline/README.md): Maven tests,
frontend lint/build, three Docker image builds, archive-backed browser E2E,
k3d image import and rollout, k3d browser E2E, and market k6. The existing
promotion code restores previous application images if a post-rollout gate
fails. It does not reset Core data or restart normal Compose development.
Images go directly from the verified local archive into k3d; this setup does
not add a registry.

## Requirements

- Docker Desktop with WSL integration (or a Linux Docker engine), Docker
  Compose, and a running, already bootstrapped Hero Association k3d cluster.
- Java 25, Node.js 24/npm, Docker CLI, `kubectl`, `git`, `openssl`, and
  `curl` in the same WSL distribution. The existing ignored
  `deploy/k3d/.tools/k3d` binary is used if `k3d` is not on `PATH`.
- The readable, isolated `deploy/k3d/.kubeconfig` with current context
  `k3d-hero-association`. The agent uses this original ignored file from its
  clean checkout through `HERO_ASSOCIATION_K3D_KUBECONFIG`; it never copies
  the credential into the Jenkins workspace.
- A GitHub `main` commit containing this Jenkins configuration. The job
  deliberately checks out `https://github.com/tiago-vibes-on/hands-on.git`
  rather than uncommitted local changes.
- Enough free RAM and disk for the existing k3d stack, Jenkins, the JVM/Node
  builds, Playwright containers, and retained image archives.

The Jenkins controller can start while k3d is stopped, but deployment requires
the cluster. WSL and Docker must be running for builds. After they restart,
polling checks the latest `main` commit; a stopped local machine cannot run a
pipeline. If a commit is attempted while k3d is unavailable, preflight fails.
SCM polling does not retry that same failed revision merely because k3d later
starts; use **Build Now** once or push a new `main` commit. Normal development
remains independent.

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

The first build can be started with **Build Now** after this configuration is
on GitHub `main`; subsequent changes to `main` are picked up by SCM polling.
The job is intentionally not configured for pull requests or forks. It allows
one build at a time, and the Jenkins controller itself has zero executors.

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
access to Docker and the isolated k3d kubeconfig, so only trusted `main`
commits may run there. Do not add pull-request builds or expose the controller
to the LAN without a separate security design. Builds run in the agent's
workspace rather than the developer working copy.

All deployment logic stays in `pipeline/run-k3d-pipeline.sh`; Jenkins only
schedules that command and records its results. The small manifest and gate
evidence files are kept in Jenkins build records, while full archives remain
in the agent workspace for provenance and rollback. They consume disk; review
them before manual cleanup. This is a local lab, not a production CI server or
an AWS deployment.
