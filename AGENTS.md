# Hero Association repository instructions

- Work on the backend in `hero-association/backend`.
- Use English for source code, documentation, comments, and generated content.
- Keep the backend independently buildable and runnable.
- For behavior-changing work, read and update `hero-association/SPEC.md`.
- Keep `hero-association/backend/README.md` aligned with supported workflows.
- Run relevant checks and report their results.
- Do not commit, push, tag, or publish GitHub Releases.

## Data and collaboration conventions

- Name database tables after the singular form of their entities (for example,
  `hero`, not `heroes`).
- Until Flyway is introduced, local and pre-production schema changes do not
  require backwards-compatible data migrations. Once Flyway is used, preserve
  existing data with explicit migrations.
- Ask for clarification when a requirement is materially ambiguous, and call
  out requests that are unsafe or conflict with sound engineering practice.
