# Local AWS exploration with Floci

Floci is an optional local AWS emulator. It does not replace the normal
development environment in `backend/compose.infra.yaml`, and the application
does not connect to it yet. Run these commands from `hero-association/` in WSL;
Docker Desktop runs the container and exposes it to Windows at
`http://localhost:4566`.

```bash
docker compose -f compose.floci.yaml up --detach
docker compose -f compose.floci.yaml ps
curl --fail http://localhost:4566/_localstack/health
```

To explore S3 with an installed AWS CLI, use a terminal with **dummy**
credentials and an explicit local endpoint:

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
aws --endpoint-url=http://localhost:4566 s3 mb s3://hero-association-lab
aws --endpoint-url=http://localhost:4566 s3 ls
```

Keep `--endpoint-url` on every command so it cannot accidentally target a real
AWS account. The AWS CLI is optional and is not installed by this Compose file.

Without the AWS CLI, a quick S3 smoke test is possible with `curl`:

```bash
curl --fail --request PUT http://localhost:4566/hero-association-lab
curl --fail --request PUT --data-binary 'Hello from Hero Association' \
  http://localhost:4566/hero-association-lab/hello.txt
curl --fail http://localhost:4566/hero-association-lab/hello.txt
```

Stop Floci without deleting its named volume:

```bash
docker compose -f compose.floci.yaml down
```

The Floci Compose project has its own network and named volume. Only port
`4566` is published, on `127.0.0.1`. No changes to `heroassociation.test` or
the normal local database are required. The planned
`aws.heroassociation.test` and `auth.aws.heroassociation.test` hostnames are for
a later application deployment in the AWS lab, not for this initial emulator.

This initial setup deliberately does not mount the Docker daemon socket.
Floci's container-backed services, including RDS, EKS, Lambda, and CodeBuild,
need that socket; mounting it gives the emulator broad control over local
Docker containers. Enable it explicitly only when those services are needed
and the impact on the normal development stack has been considered.
