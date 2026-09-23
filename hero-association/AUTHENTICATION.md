# Authentication Architecture

## Purpose

This document defines the intended account, authentication, and service
boundary design for Hero Association. The BFF and Game Core are already split,
and local Keycloak is available. The BFF owns browser login, sessions, and
CSRF protection; Game Core owns Account provisioning and Manager onboarding.

## Implementation progress

| Stage | Status | What it means |
| --- | --- | --- |
| Architecture and boundaries | Complete | Keycloak, BFF, Game Core, and the account model are defined in this document. |
| Local Keycloak environment | Complete | Compose runs Keycloak with its own PostgreSQL database and imports the versioned Hero Association realm. |
| BFF session flow | Complete | Keycloak login, callback, local logout, server-side sessions, a secure session cookie, and CSRF protection are implemented. |
| Frontend integration | Complete | React bootstraps the BFF session, offers sign-in and sign-out, and sends CSRF headers for writes. |
| Game Core token validation | Complete | The BFF forwards its server-held Keycloak access token and Game Core rejects anonymous, invalid, or incorrectly addressed tokens. |
| Account and manager onboarding | Complete | The first authenticated account request provisions an `Account`; React then requires a unique Manager display name before opening the game. |
| Agency authorization | Complete | `AgencyMember` binds Managers to agencies; every agency read and command requires membership, and financial market commands require `LEADER`. |
| Google sign-in | Not started | Configure Google as a Keycloak identity provider after native login works. |
| Service split | Complete | `backend/hero-association-core` owns game state and `backend/hero-association-bff` is the public proxy boundary. |

The matching actionable checklist is in [Milestone 9 of the roadmap](ROADMAP.md#milestone-9--authentication-and-real-time-updates). Update this table and that checklist together whenever an authentication stage is completed.

## Decisions

- Keycloak is the identity provider. Hero Association does not implement an
  OAuth 2.0 or OpenID Connect authorization server and does not store player
  passwords.
- The browser interacts only with the Identity BFF. It never calls Game Core
  directly and never stores Keycloak access or refresh tokens.
- The Identity BFF is the only public backend entry point. Game Core is a
  private service, reachable only by the BFF and other explicitly authorized
  internal services.
- Keycloak provides a Hero Association-branded account experience, initially
  with email and password. Google sign-in is added through Keycloak as the
  first social identity provider.
- Every persistent account, manager, and membership resource uses UUIDv7.

## Target topology

```text
Browser (React) -> Caddy -----------------> Identity BFF
        |                    |                    |
        | HTTPS session      |                    +-> BFF session PostgreSQL
        | cookie             |
        |                    +--------------------> Keycloak
        |                                             |
        |                                             +-> Keycloak PostgreSQL
        |
        | server-to-server access token
        v
Game Core -----------------------> PostgreSQL
```

Caddy is the edge gateway in the containerized local and deployed topology. It
serves the React build, proxies `/api` and `/auth` to the BFF, and exposes
Keycloak on its own authentication hostname. It does not make authentication
decisions: the BFF owns the browser session and Keycloak owns identity. Game
Core and all PostgreSQL services remain private.

The BFF owns the browser session, login, logout, callback, and CSRF handling.
It uses the OpenID Connect Authorization Code flow as a confidential server
client with PKCE. Keycloak tokens are stored in the BFF session database; the
browser only holds a secure, `HttpOnly`, `SameSite` session cookie.

During login, the browser is redirected to Keycloak's branded login page and
back to the BFF callback. This is a browser navigation, not a React call to a
Keycloak administration API. Google login is a second redirect from Keycloak
to Google and then back to Keycloak.

Game Core validates caller identity and must apply its own authorization. It
does not trust browser-provided manager or agency identifiers, or unsigned
headers such as `X-Manager-Id`. The BFF forwards its server-held, short-lived
Keycloak access token over the private service network. Game Core validates its
issuer, signature, expiry, subject, and `hero-association-core` audience.
Token exchange or a dedicated internal-token issuer can be evaluated only when
the service boundary needs additional isolation. Game Core binds the
authenticated subject to an Account. Authorizing agency identifiers through
membership is now enforced through `AgencyMember`. Managers without a
membership cannot access agency state or commands; agency creation and
invitations are the next stage.

## Account and game identity

Authentication identity is deliberately distinct from in-game identity:

```text
Keycloak subject -> Account -> Manager -> AgencyMember -> Agency
```

### Account

`Account` is the Hero Association record corresponding to one Keycloak user.
It must have a unique, immutable Keycloak subject and may keep a current email
for display and communication. Email is not a primary key because it can
change. Suggested initial fields are:

```text
id                  UUIDv7
keycloakSubject     unique string
email               nullable/current value
emailVerified       boolean
status              ACTIVE | SUSPENDED | DELETED
createdAt
lastLoginAt
```

`GET /api/v1/account` provisions the Account on its first authenticated call
and refreshes its current email, verified state, and login timestamp on later
calls. The first-launch flow then requires `POST /api/v1/account/manager` to
choose a unique, case-insensitive Manager display name of 3 to 100 characters;
it never treats a Keycloak username as the manager name.

### Manager and agency membership

`Manager` remains the in-game identity and belongs to exactly one Account in
the initial design. A manager joins agencies through `AgencyMember`:

```text
AgencyMember
- id                UUIDv7
- agencyId
- managerId
- role              LEADER | MANAGER
- joinedAt
```

An agency has exactly one `LEADER`; invited collaborators use `MANAGER`.
`Agency.leader` remains a convenient direct reference and agrees with the
seeded `LEADER` membership. Both roles can access agency state and operate its
gameplay commands. Market-order creation and cancellation are currently
financial actions reserved for `LEADER`. Invitations, departure, ownership
transfer, and agency creation remain separate product rules.

## Browser contract

The React application calls only same-origin BFF endpoints. The initial
session endpoints should be:

```text
GET  /api/v1/session
GET  /auth/login
GET  /auth/logout
GET  /api/v1/account
POST /api/v1/account/manager
```

`GET /api/v1/session` is public and returns whether the browser has a BFF
session plus a small Keycloak identity summary. It returns no access, refresh,
or ID tokens. The frontend then calls the authenticated Account endpoints
through the BFF proxy; those endpoints return only Hero Association Account and
Manager data, never raw identity-provider credentials.
The Account response includes the Manager's agency memberships so React can
open an authorized agency. A Manager without memberships sees an explicit
no-agency screen rather than shared game data.

`GET /auth/login` begins the authorization-code redirect. After Keycloak
authenticates the browser and calls `/auth/callback`, the BFF returns the user
to the frontend. `GET /auth/logout` begins OIDC RP-initiated logout: it clears
the BFF session and sends the browser to Keycloak to end its SSO session.
Keycloak returns to the BFF's fixed `/auth/post-logout` callback, whose state
must match Quarkus's post-logout cookie before the BFF redirects to the
frontend. The logout response does not clear browser cookies globally, because
that short-lived state cookie is required for the callback; Quarkus removes the
BFF session cookie as part of the logout flow. The callback is registered as
the client's only post-logout redirect URI.

The signed-out frontend offers **Sign in** and **Create account**. Both begin
at the protected BFF login route; the registration action adds only the
standard OIDC `prompt=create` hint, which Quarkus forwards to Keycloak's
authorization request. Quarkus continues to generate and validate the
authorization-code state and PKCE values, so React never constructs a
Keycloak registration URL or handles identity-provider credentials.

The repository's Playwright E2E test uses separate local frontend and BFF
ports, with its own versioned callback URIs. They are limited to the isolated
test stack; production clients must register only their deployed BFF callback
URI.

Game commands continue under `/api/v1/...`; the BFF requires an authenticated
session before forwarding them to Game Core. State-changing requests require a
signed double-submit CSRF cookie plus the short-lived raw token returned by the
session endpoint. The cookie is `HttpOnly`; React keeps the raw token only in
memory and sends it as `X-CSRF-TOKEN`.

The BFF must not expose Keycloak administrative endpoints, client secrets,
refresh tokens, or raw identity-provider credentials to the browser.

## Google sign-in

Google is configured in Keycloak as an identity provider. The Google OAuth
client ID and client secret are held only in Keycloak configuration or its
secret store, never in the React bundle, source control, or Game Core.

The Google redirect URI is Keycloak's broker callback, for example in local
development:

```text
http://localhost:8180/realms/hero-association/broker/google/endpoint
```

Linking an existing email/password account to Google must preserve the same
Keycloak subject and therefore the existing Hero Association Account, Manager,
agencies, and inventory.

## Current local Keycloak setup

`backend/compose.yaml` and `backend/compose.native.yaml` run Keycloak on
`http://localhost:8180` with a dedicated PostgreSQL database and expose the
BFF session database at `localhost:5433` for host-based BFF development.
Before starting either Compose stack, copy `backend/.env.example` to
`backend/.env` and replace every placeholder. The `.env` file is ignored by
Git. The optional `backend/compose.caddy.yaml` overlay instead runs the full
containerized stack through `https://heroassociation.test`, with Keycloak at
`https://auth.heroassociation.test`. Its exact hosts-file, certificate-trust,
and startup instructions are in [`backend/README.md`](backend/README.md#local-https-gateway).

`backend/keycloak/realm/hero-association-realm.json` is a versioned startup
import. It creates the `hero-association` realm, enables local email/password
registration, and creates the confidential `hero-association-bff` client plus
the `hero-association-core` resource-server audience. The BFF client mapper
adds that audience only to access tokens. Its client secret is resolved from
`HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET` during the first import; it is never
committed to the repository. Local email verification is disabled because SMTP
is not configured. It registers callbacks for direct host development, the
isolated browser test, and the local Caddy gateway. Production must use a
separate realm configuration with only its deployed callback URLs and no
development credentials.

The realm selects the versioned `hero-association` CSS-only login theme in
`backend/keycloak/theme`. It extends Keycloak's `keycloak.v2` theme without
copying its templates, and local Compose disables theme caching so CSS edits
are immediately visible during development. Production should use normal theme
caching.

The realm is imported only when it does not yet exist. To deliberately recreate
the local Keycloak realm, stop the stack with `docker compose down --volumes`
from `backend/`, then start it again. Google is intentionally not configured
until its social-login task is implemented.

The versioned realm includes two development-only test users:
`user1@mail.com` / `user1` corresponds to the seeded User 1 Manager and leads
Dawnwatch Agency; `user2@mail.com` / `user2` corresponds to the seeded User 2
Manager and leads Ironridge Exchange. These credentials must never be used
outside local development.

The BFF uses Quarkus's database token-state manager. It stores Keycloak's ID,
access, and refresh tokens in `postgres-bff`, not in the browser and not in
Keycloak's database. Quarkus creates the internal
`oidc_db_token_state_manager` table on startup. It is session infrastructure,
not an Account table, and it is recreated with the local volume. A production
deployment must provide a durable BFF-owned database and secret storage.

## Deployment and local development

- Run Keycloak and its dedicated PostgreSQL database in Docker Compose for
  local development. It must not share Game Core's game-state database.
- Keep Vite plus Quarkus dev mode as the default local editing workflow. Use
  the Caddy Compose overlay when validating the packaged, same-origin HTTPS
  and OIDC flow.
- Configure realm, clients, redirect URIs, and theme through versioned,
  non-secret configuration where possible.
- Store client secrets, Google credentials, and production signing material in
  environment-specific secret storage. Do not add them to `.env` files tracked
  by Git.
- Caddy exposes the React application, BFF routes, and Keycloak login endpoint
  publicly. Game Core has no browser CORS configuration and no public ingress,
  and still validates the BFF-forwarded bearer token as defense in depth.

## Delivery sequence

1. Completed: rename the current backend as `hero-association-core`, create
   `hero-association-bff` as an independently buildable Quarkus service, and
   make the BFF the frontend's unauthenticated proxy boundary.
2. Completed: add local Keycloak Compose support, a versioned
   `hero-association` realm, and non-secret environment templates.
3. Completed: add BFF session/login/logout/callback endpoints, server-side
   token storage, CSRF protection, protected proxy routes, and React session
   bootstrap. Keep the game prototype accessible only through the BFF.
4. Completed: validate BFF-forwarded access tokens in Game Core and reject
   anonymous requests. The authenticated Keycloak subject is now available to
   Core.
5. Completed: add the `account` table, provision Accounts from the Keycloak
   subject, and require Manager onboarding with a unique display name.
6. Completed: add `agency_member`, bind the seeded agencies to their Managers,
   and enforce membership plus leader-only market permissions on Core routes.
7. Add agency creation and invitations, then specify membership departure and
   leadership transfer.
8. Add a Google OAuth client and configure Keycloak's Google identity provider.
   transfer before exposing collaborative agency management.

## Deferred decisions

- Whether one Account may own more than one Manager in the future.
- Whether a manager may be an active member of more than one agency.
- Account deletion, retention, and recovery policy.
- Email-verification requirements before market and social features are
  enabled.
- MFA requirements and which privileged actions require step-up
  authentication.
