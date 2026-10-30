# Future: blockchain membership (design only, not built)

A sketch of how a loyalty / membership layer could sit on top of TravelPilot for a resort group, kept separate
from the planner so the AI part does not depend on it. Nothing here is implemented.

## Goal

Travelers who book through the group's properties (theme parks, cable cars, hotels) collect membership benefits
that work across brands and partners, are portable, and can be verified by partners without a shared database.

## Design

- **Identity:** the guest id stays anonymous. A member links a wallet (or a custodial wallet created for them)
  when they sign up; no personal data on chain.
- **Membership pass:** a non-transferable token (soulbound, ERC-5192 style) per member with a tier (Silver, Gold,
  Platinum). Tier changes are signed by the operator; partners read the tier on chain to apply discounts.
- **Points:** an off-chain ledger (Postgres) for everyday earn/burn, with a daily Merkle root of balances
  published on chain. Members can prove a balance to a partner with a Merkle proof; fees stay low and refunds or
  fraud fixes remain possible off chain.
- **Vouchers:** single-use vouchers as NFTs with an expiry, redeemed by the partner's scanner (signature check +
  on-chain burn), so a voucher cannot be used twice across partner systems.
- **Chain:** a low-fee EVM network or a permissioned chain run with partners; contracts audited; operator keys
  in an HSM.

## How it would touch TravelPilot

- The planner could take the member's tier as an input to scoring (e.g. boost places with member benefits), but
  benefits would be computed by the backend like prices (ADR-006), never by the model.
- A tool `getMemberBenefits(placeId)` (read-only) could let the chat answer "what discount do I get at Bà Nà?".

## Open questions

- Legal status of points and tokens in Vietnam; tax treatment of rewards.
- Wallet UX for non-crypto users (custodial by default, export later).
- Whether a shared database with partners would be simpler for the first years.
