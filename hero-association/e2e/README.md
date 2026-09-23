# Hero Association E2E tests

This independent Node.js project runs browser tests across React, the BFF,
Keycloak, Game Core, and PostgreSQL. It uses Playwright with Chromium.

## Run the authentication flow

```bash
npm --prefix ../frontend install
npm install
npm run test:auth
```

The test starts a temporary Docker Compose project named
`hero-association-e2e`, builds the Core and BFF containers, and starts Vite at
`http://127.0.0.1:15173`. It uses separate ports (`15432`, `15434`, `18080`,
`18081`, and `18180`) and test-only database volumes, so it never shares data
or ports with normal local development. The test tears its Compose project and
volumes down when it completes, including after a failed test setup.

The default command runs Chromium in Playwright's official Docker image, using
the same version as the pinned `@playwright/test` package. This avoids relying
on browser libraries installed on the host. Docker is therefore required for
both the application stack and the browser. `npm run test:host` is available
only for machines where `npm run install:browsers` has installed Chromium and
its system dependencies.

The initial test signs in with the versioned local Keycloak account
`user1@mail.com` / `user1`, verifies the seeded agency screen, signs out, and
confirms that the next sign-in displays Keycloak credentials rather than
reusing the prior SSO session.

The frontend dependencies must be installed before the suite runs because the
test starts Vite directly. The test setup owns both the temporary Compose
project and Vite process, and stops them during teardown.

Browser artifacts are written to ignored `test-results/` and
`playwright-report/` directories when appropriate.
