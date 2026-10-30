# ADR-008: Anonymous guest tokens instead of accounts

- Status: Accepted
- Date: 2026-10-30

## Context

The product has no sign-up: a traveler opens the page and plans a trip. Trips and chats must still be private to
the browser that created them, and rate limits need a stable identity. Until Phase 6 the frontend sent a
self-chosen `X-Guest-Id` header, which anyone could forge to read other trips.

## Options

1. Accounts (email/password or OAuth): strongest identity, but friction, personal data and more to secure.
2. Server-issued anonymous token: the server picks a random guest id and signs it; the browser keeps the token.
3. Keep the client-chosen id: no security.

## Decision

`POST /api/v1/guest` issues an HS256 JWT (`sub` = random UUID, 7 days, renewed when a valid token is presented)
signed with `GUEST_TOKEN_SECRET`. Only our exact header is accepted (no `alg: none`), signatures are compared in
constant time. Controllers take a `Guest` argument, so an endpoint cannot forget authentication; other guests'
trips return 404. New tokens are limited per IP so limits cannot be reset by creating guests.

## Consequences

- No personal data is stored; losing the token (cleared storage, new device) loses access to the trips by design.
- The token lives in `localStorage`, so an XSS bug would expose it: text-only rendering and CSP mitigate this.
- `GUEST_TOKEN_SECRET` must be set in production, otherwise every restart invalidates all tokens.
- Accounts can be added later on top: link a guest id to a user when they sign up.
