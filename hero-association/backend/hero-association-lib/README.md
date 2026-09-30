# Hero Association libraries

This Maven aggregator currently contains the pure Java
hero-association-combat-engine module. It owns combat rules and snapshots,
not HTTP endpoints, persistence, Redis, RabbitMQ, or Quarkus lifecycle state.
Shared Hero progression formulas and UUIDv7 helpers also live here so Core
and Expedition apply the same calculations.

From backend/, test it and install the artifact for standalone Core or
Expedition builds:

~~~bash
./mvnw --batch-mode -pl hero-association-lib -am install
~~~

For a clean build of a consuming service without a separate install, use
the backend reactor:

~~~bash
./mvnw --batch-mode -pl hero-association-core -am package
./mvnw --batch-mode -pl hero-association-expedition -am package
~~~

The current live Quest flow remains in Core. Sharing the engine does not
switch battle ownership to Expedition.
