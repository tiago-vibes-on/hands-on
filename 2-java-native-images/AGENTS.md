# Java Native Images project instructions

- Keep `initial` as the JVM-based Hero Association application established by `1-containers/final`.
- Keep `final` behaviorally compatible with `initial`; native compilation and packaging are the subject of this project, not new API features.
- Keep JVM builds as the default. Native builds must be selected explicitly with `-Dnative` or the native Compose override.
- The local `-Dnative` path must fail fast when `native-image` is unavailable; it must not fall back to Docker.
- The local native path uses GraalVM on WSL and runs the application outside Docker.
- For the local native path, Docker provides PostgreSQL only. The application connects to `localhost`.
- The containerized native path builds the executable in Docker and connects to PostgreSQL using the Compose service hostname.
- Read `SPEC.md` before changing either checkpoint.
