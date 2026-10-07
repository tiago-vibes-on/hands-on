#!/usr/bin/env python3
"""Move the existing Hero browser bindings to 8088/8443 without resetting data."""
import argparse
import json
from pathlib import Path
import socket
import subprocess

DIRECTORY = Path(__file__).resolve().parent
CLUSTER = "hero-association"
BALANCER = "k3d-hero-association-serverlb"
APP_ORIGIN = "https://heroassociation.test:8443"
AUTH_ORIGIN = "https://auth.heroassociation.test:8443"
RECEIPT = DIRECTORY / "secrets/browser-port-change.json"


def run(arguments, data=None):
    result = subprocess.run([str(item) for item in arguments], input=data, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(f"{arguments[0]} failed (exit {result.returncode}); no captured credentials printed")
    return result.stdout


def save(receipt):
    RECEIPT.parent.mkdir(mode=0o700, exist_ok=True)
    RECEIPT.touch(mode=0o600, exist_ok=True)
    RECEIPT.chmod(0o600)
    RECEIPT.write_text(json.dumps(receipt, indent=2) + "\n")


def balancer():
    observed = json.loads(run(["docker", "inspect", BALANCER]))[0]
    labels = observed["Config"].get("Labels", {})
    if labels.get("k3d.cluster") != CLUSTER or labels.get("k3d.role") != "loadbalancer":
        raise RuntimeError("Refusing a foreign load balancer")
    return observed


def bindings(http, https):
    return {f"{container}/tcp": [{"HostIp": "127.0.0.1", "HostPort": str(host)}]
            for container, host in ((80, http), (443, https), (6443, 16550))}


def nodes():
    names = run(["docker", "ps", "-a", "--filter", "label=k3d.cluster=" + CLUSTER,
                 "--format", "{{.Names}}"] ).splitlines()
    observed = json.loads(run(["docker", "inspect", *names]))
    return sorted((item["Name"], item["Id"], sorted(
        mount["Name"] for mount in item["Mounts"] if mount["Type"] == "volume"))
        for item in observed if item["Config"].get("Labels", {}).get("k3d.role") in ("server", "agent"))


def kube(*arguments, data=None):
    command = ["kubectl", "--kubeconfig", DIRECTORY / ".kubeconfig", "--context", "k3d-" + CLUSTER]
    observed = json.loads(run(command + ["config", "view", "--minify", "-o", "json"]))
    if observed["clusters"][0]["cluster"]["server"] != "https://127.0.0.1:16550":
        raise RuntimeError("Refusing a foreign Kubernetes API endpoint")
    return run(command + list(arguments), data=data)


def move_ports():
    observed = balancer()
    current = observed["HostConfig"]["PortBindings"]
    if current == bindings(8088, 8443):
        print("Hero browser bindings already use 8088/8443.")
        return
    if current != bindings(80, 443):
        raise RuntimeError("Expected the existing loopback 80/443 bindings; inspect before maintenance")
    for port in (8088, 8443):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    before = nodes()
    receipt = {"cluster": CLUSTER, "phase": "prepared", "nodes": before,
               "old_ports": current, "balancer_was_running": observed["State"]["Running"]}
    save(receipt)
    run(["k3d", "node", "edit", BALANCER,
         "--port-delete", "127.0.0.1:80:80", "--port-delete", "127.0.0.1:443:443",
         "--port-add", "127.0.0.1:8088:80", "--port-add", "127.0.0.1:8443:443"])
    if balancer()["HostConfig"]["PortBindings"] != bindings(8088, 8443) or nodes() != before:
        raise RuntimeError("Port/node verification failed; preserve the private receipt and inspect")
    if not receipt["balancer_was_running"]:
        run(["docker", "stop", BALANCER])
    receipt["phase"] = "ports-changed"
    save(receipt)
    print("Hero bindings changed; node containers and volumes preserved. Start Hero, then run reconcile.")


def reconcile():
    if balancer()["HostConfig"]["PortBindings"] != bindings(8088, 8443):
        raise RuntimeError("Change the Hero browser bindings before reconciling URLs")
    receipt = json.loads(RECEIPT.read_text()) if RECEIPT.exists() else {"cluster": CLUSTER}
    if receipt.get("cluster") != CLUSTER:
        raise RuntimeError("Refusing a foreign maintenance receipt")
    workloads = {item["metadata"]["name"]: item for item in
                 json.loads(kube("-n", CLUSTER, "get", "deployments", "-o", "json"))["items"]}
    expected = ("keycloak", "core", "bff", "expedition", "market", "assets", "world", "quest")
    if any(name not in workloads for name in expected):
        raise RuntimeError("Expected the existing full Hero stack; do not bootstrap/reset it for port maintenance")
    for name in expected:
        selector = json.loads(kube("-n", CLUSTER, "get", "service/" + name, "-o", "json"))["spec"]["selector"]
        if "hybrid" in selector or workloads[name]["spec"]["replicas"] == 0:
            raise RuntimeError("Restore hybrid services before port maintenance")
    if "resources" not in receipt:
        receipt["resources"] = [{"name": name, "spec": workloads[name]["spec"]} for name in expected]
        receipt["phase"] = "reconciling"
        save(receipt)
    for name in expected:
        if name == "keycloak":
            values = {"KC_HOSTNAME": AUTH_ORIGIN}
        else:
            values = {"HERO_ASSOCIATION_OIDC_ISSUER": AUTH_ORIGIN + "/realms/hero-association"}
        if name == "bff":
            values.update({"HERO_ASSOCIATION_FRONTEND_URL": APP_ORIGIN,
                "HERO_ASSOCIATION_OIDC_AUTHORIZATION_PATH": AUTH_ORIGIN + "/realms/hero-association/protocol/openid-connect/auth",
                "QUARKUS_OIDC_END_SESSION_PATH": AUTH_ORIGIN + "/realms/hero-association/protocol/openid-connect/logout"})
        kube("-n", CLUSTER, "set", "env", "deployment/" + name, "--containers=" + name,
             *(key + "=" + value for key, value in values.items()))
    kube("-n", CLUSTER, "patch", "httproute/redirect-to-https", "--type=merge", "-p", json.dumps({
        "spec": {"rules": [{"filters": [{"type": "RequestRedirect", "requestRedirect": {
            "scheme": "https", "port": 8443, "statusCode": 301}}]}]}}))
    realm = DIRECTORY / "secrets/hero-association-realm.json"
    run(["node", DIRECTORY / "generate-realm.mjs",
         DIRECTORY / "../../backend/keycloak/realm/hero-association-realm.json", realm])
    manifest = kube("-n", CLUSTER, "create", "configmap", "hero-association-k3d-realm",
                    "--from-file=" + str(realm), "--dry-run=client", "-o", "json")
    kube("apply", "-f", "-", data=manifest)
    for name in expected:
        kube("-n", CLUSTER, "rollout", "status", "deployment/" + name, "--timeout=300s")
        print("Ready: " + name, flush=True)
    # Realm imports skip existing realms. Update the existing client through
    # Keycloak's admin API, retaining its users, IDs, credentials, and sessions.
    run(["node", DIRECTORY / "sync-expedition-realm.mjs"])
    receipt["phase"] = "reconciled"
    save(receipt)
    print("Hero URLs and existing Keycloak callbacks reconciled to HTTPS 8443. Run the browser smoke checks.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("ports", "reconcile"))
    arguments = parser.parse_args()
    if arguments.command == "ports":
        move_ports()
    else:
        reconcile()


if __name__ == "__main__":
    main()
