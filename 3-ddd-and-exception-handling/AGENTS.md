# DDD and Exception Handling project instructions

- Keep `initial` as an independent copy of `2-java-native-images/final`.
- Keep JVM builds as the default and native builds explicit with `-Dnative` or the native Compose override.
- In `final`, use the `domain`, `application`, `api`, and `repository` package layout.
- Keep one `Hero` model. Do not introduce a separate `HeroEntity` unless the persistence and domain representations genuinely diverge.
- Keep `HeroRepository` in `repository`; do not add a repository interface to `domain` for this project.
- Expose only `/api/v1/heroes` from `final`; do not retain the unversioned route.
- Treat a hero alias as unique. `POST`, `PUT`, and `PATCH` must reject an alias already used by another hero with `409 Conflict`.
- Read `SPEC.md` before changing either checkpoint.
