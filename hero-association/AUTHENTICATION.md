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
| Local Keycloak environment | Complete | k3d runs Keycloak with a separate PostgreSQL database and the versioned Hero Association realm. |
| BFF session flow | Complete | Keycloak login, callback, local logout, server-side sessions, a secure session cookie, and CSRF protection are implemented. |
| Redis BFF token state | Complete | Quarkus stores Keycloak token state in BFF-owned Redis; the `postgres-bff` service has been removed. |
| Frontend integration | Complete | React bootstraps the BFF session, offers sign-in and sign-out, and sends CSRF headers for writes. |
| Game Core token validation | Complete | The BFF forwards its server-held Keycloak access token and Game Core rejects anonymous, invalid, or incorrectly addressed tokens. |
| Expedition token validation | Complete locally | Expedition validates its own Keycloak audience; BFF forwards the server-held token. Map and WebSocket reconnect pass in full k3d and individually switched hybrid mode. |
| Private Assets commands | Independent Assets service | Dedicated Market service credential for all private calls; new reservations also validate the player's Assets-audience token; Core resolves identity/leadership with the original token and a separate Core credential. Background settlement/refund recovery uses recorded reservation authorization. See [Assets contract](ASSETS_CONTRACT.md). |
| Account and manager onboarding | Complete | The first authenticated account request provisions an `Account`; React then requires a unique Manager display name before opening the game. |
| Agency authorization | Complete | `AgencyMember` binds Managers to agencies; every agency read and command requires membership, financial market commands require `LEADER`, and a Manager without a membership can create its first agency. |
| Google sign-in | Deferred (post-MVP) | Keep native email/password sign-in for the MVP; configure Google as a Keycloak identity provider later. |
| Service split | Complete | `backend/hero-association-core` owns game state and `backend/hero-association-bff` is the public proxy boundary. |
| Local identity-aware gateway limit | Complete | Envoy validates the browser session through BFF and solely enforces five market placements per second per user in full k3d and hybrid mode. Gateway Redis is separate from BFF session Redis. See [edge-auth runbook](deploy/k3d/EDGE_AUTH.md). |

The actionable checklist is in the [roadmap](ROADMAP.md): Milestone 9 covers
authentication work in the MVP, and Google sign-in is listed under Post-MVP.
Update this table and the roadmap together when an authentication stage changes.

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
  with email and password for the MVP. Google sign-in can be added through
  Keycloak as the first social identity provider after the MVP.
- Every persistent account, manager, and membership resource uses UUIDv7.

## Target topology

```text
Browser --HTTPS--> Envoy Gateway --app route--> React
                         |--/api, /auth--> Identity BFF --bearer token--> Game Core --> Core PostgreSQL
                         |                    |--session state--> Redis
                         |                    +--OIDC--> Keycloak
                         +--auth hostname--------------> Keycloak --> Keycloak PostgreSQL
```

Envoy Gateway is the only active browser-facing local edge in full k3d and
hybrid mode. Istio secures meshed service-to-service traffic in k3d. A selected
WSL service sits behind a private in-cluster bridge, keeping the public and
private Service URLs stable. The BFF owns browser sessions and Keycloak owns
identity. Envoy applies a global five-per-second, per-subject limit to market
order placement after asking BFF to validate the opaque session; it never
trusts a browser-supplied identity header. Game Core, Keycloak PostgreSQL,
and Redis remain private. See [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md).

The BFF owns the browser session, login, logout, callback, and CSRF handling.
It uses the OpenID Connect Authorization Code flow as a confidential server
client with PKCE. Keycloak tokens are kept only in Redis; the browser holds
only a secure, `HttpOnly`, `SameSite` session cookie.

During login, the browser is redirected to Keycloak's branded login page and
back to the BFF callback. This is a browser navigation, not a React call to a
Keycloak administration API. Post-MVP Google login would add a second redirect
from Keycloak to Google and then back to Keycloak.

Game Core validates caller identity and must apply its own authorization. It
does not trust browser-provided manager or agency identifiers, or unsigned
headers such as `X-Manager-Id`. The BFF forwards its server-held, short-lived
Keycloak access token over the private service network. Game Core validates its
issuer, signature, expiry, subject, and `hero-association-core` audience.
Token exchange or a dedicated internal-token issuer can be evaluated only when
the service boundary needs additional isolation. Game Core binds the
authenticated subject to an Account. Authorizing agency identifiers through
membership is now enforced through `AgencyMember`. Managers without a
membership cannot access agency state or commands. An onboarded Manager without
a membership can create one empty agency as its `LEADER`; invitations remain
the next stage.

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
financial actions reserved for `LEADER`. An onboarded Manager with no
membership can create one empty Level 1 agency through `POST /api/v1/agencies`
and becomes its `LEADER`. Agency names are unique case-insensitively; the
current flow rejects a Manager who already has a membership. Invitations,
departure, and ownership transfer remain separate product rules.

## Browser contract

The React application calls only same-origin BFF endpoints. The initial
session endpoints should be:

```text
GET  /api/v1/session
GET  /auth/login
GET  /auth/logout
GET  /api/v1/account
POST /api/v1/account/manager
POST /api/v1/agencies
```

`GET /api/v1/session` is public and returns whether the browser has a BFF
session plus a small Keycloak identity summary. It returns no access, refresh,
or ID tokens. The frontend then calls the authenticated Account endpoints
through the BFF proxy; those endpoints return only Hero Association Account and
Manager data, never raw identity-provider credentials.
The Account response includes the Manager's agency memberships so React can
open an authorized agency. A Manager without memberships sees an explicit
no-agency screen rather than shared game data, with a form to create that
Manager's first empty agency.

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
When no BFF session exists, `/auth/logout` redirects straight back to the
frontend instead of returning a 404.

The signed-out frontend offers **Sign in** and **Create account**. Both begin
at the protected BFF login route; the registration action adds only the
standard OIDC `prompt=create` hint, which Quarkus forwards to Keycloak's
authorization request. Quarkus continues to generate and validate the
authorization-code state and PKCE values, so React never constructs a
Keycloak registration URL or handles identity-provider credentials.

The repository's Playwright E2E suite uses separate local frontend and BFF
ports, with its own versioned callback URIs. It completes a new user's native
Keycloak registration, signs out, signs back in, and verifies that both logins
resolve to the same Core Account ID and Keycloak subject. Those callback URIs
are limited to the isolated test stack; production clients must register only
their deployed BFF callback URI.

Game commands continue under `/api/v1/...`; the BFF requires an authenticated
session before forwarding them to Game Core. State-changing requests require a
signed double-submit CSRF cookie plus the short-lived raw token returned by the
session endpoint. The cookie is `HttpOnly`; React keeps the raw token only in
memory and sends it as `X-CSRF-TOKEN`.

The BFF must not expose Keycloak administrative endpoints, client secrets,
refresh tokens, or raw identity-provider credentials to the browser.

## Post-MVP: Google sign-in

Google is configured in Keycloak as an identity provider. The Google OAuth
client ID and client secret are held only in Keycloak configuration or its
secret store, never in the React bundle, source control, or Game Core.

The Google redirect URI is Keycloak's broker callback, for example in local
development:

```text
http://localhost:17180/realms/hero-association/broker/google/endpoint
```

Linking an existing email/password account to Google must preserve the same
Keycloak subject and therefore the existing Hero Association Account, Manager,
agencies, and inventory.

## Current local Keycloak setup

The active local Keycloak and its own PostgreSQL run in k3d. The browser uses
`https://auth.heroassociation.test`, and BFF reaches the private Keycloak
Service. Hybrid Quarkus services use private port-forwards and the same public
OIDC issuer. The k3d deployment generates ignored credentials; no Keycloak
client secret or CA private key is committed. The older Compose Keycloak
stack remains only for the E2E migration and optional packaged workflows.
First setup, trust, and switching commands are in
[LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md).

`backend/keycloak/realm/hero-association-realm.json` is a versioned startup
import. It creates the `hero-association` realm, enables local email/password
registration, and creates the confidential `hero-association-bff` client plus
the `hero-association-core` and `hero-association-expedition`
resource-server audiences. The BFF client mappers add both audiences only
to access tokens. Its client secret is resolved from
`HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET` during the first import; it is never
committed to the repository. Local email verification is disabled because SMTP
is not configured. It registers callbacks for direct host development, the
isolated k3d browser test, and the local Envoy Gateway. Production must use a
separate realm configuration with only its deployed callback URLs and no
development credentials.

The realm selects the versioned `hero-association` CSS-only login theme in
`backend/keycloak/theme`. It extends Keycloak's `keycloak.v2` theme without
copying its templates, and local k3d Keycloak disables theme caching so CSS edits
are immediately visible during development. Production should use normal theme
caching.

Native registration asks for the standard email and password inputs, not first
or last name. Those optional Keycloak fields are hidden from end users and are
set directly as deterministic seed data while the product is in its early
stage.

The realm is imported only when it does not yet exist. An existing k3d realm
will not gain new fixture users merely from a file edit. The private
Expedition integration syncs its client and audience mapper explicitly;
other realm changes require deliberate Keycloak updates or a backed-up k3d
lab recreation. Use the disposable E2E namespace to validate new fixtures
without resetting daily accounts. Google remains a post-MVP task.

The versioned realm includes `user1@mail.com` / `user1` (Dawnwatch leader),
`user2@mail.com` / `user2` (Ironridge leader), and `user3@mail.com` / `user3`
(an intentionally unprovisioned onboarding user). It also includes
`manager1@mail.com` through `manager10@mail.com`, each with the matching
`managerN` password, seeded Manager, and agency membership. Three agencies now
have multiple Managers; the complete roles and credentials are in
[TEST_DATA.md](TEST_DATA.md). These weak credentials must never be used outside
local development or the isolated k3d lab.

The BFF uses Quarkus's Redis token-state manager. It stores Keycloak ID,
access, and refresh tokens in `redis-bff`; the browser receives only an opaque
session reference. Redis session state is intentionally ephemeral in local
development: restarting Redis invalidates BFF sessions and requires users to
sign in again. Production Redis must be BFF-owned, private, authenticated, TLS
protected, and encrypted at rest; it needs the same secret-management controls
as the Keycloak client secret.

## Deployment and local development

- Keep Keycloak PostgreSQL separate from Core PostgreSQL inside k3d.
- Use Envoy Gateway with the canonical local HTTPS hostnames in both full
  k3d and hybrid modes. Selected Vite/Quarkus services can run on WSL
  without changing the browser origin or OIDC redirect URI.
- Version realm, client, redirect, and theme configuration without secrets;
  supply client secrets through ignored local files and Kubernetes Secrets.
- Keep Game Core private and validate BFF-forwarded bearer tokens as
  defense in depth. The browser receives only the opaque BFF session.
- Use explicit k3d database reset/reseed commands only when changing the
  disposable schema. Do not reset data during Quarkus reload.

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
7. Completed for initial creation: allow a Manager without a membership to
   create one empty Level 1 agency as its leader. Invitations, membership
   departure, and leadership transfer remain to be specified.
8. Post-MVP: add a Google OAuth client and configure Keycloak's Google identity
   provider while preserving links to existing accounts.

## Deferred decisions

- Whether one Account may own more than one Manager in the future.
- Whether a manager may be an active member of more than one agency.
- Account deletion, retention, and recovery policy.
- Email-verification requirements before market and social features are
  enabled.
- MFA requirements and which privileged actions require step-up
  authentication.

Market validates the `hero-association-market` audience. BFF tokens include it
and Core; Market forwards the original player token only for current owner
context and new reservations. Durable workers store no JWTs and use the
dedicated private Assets credential for status, settlement and closure.

Assets has a dedicated bearer client and BFF access-token audience. BFF routes
gold transfers there without either private credential. Core and Assets share
`HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY` for staged commands, snapshots and
player permission RPCs; Market and Assets share the distinct Market credential.
Both credentials are at least 32 characters and are restricted by service identity
and path in the k3d mesh. Background recovery stores no bearer tokens.
