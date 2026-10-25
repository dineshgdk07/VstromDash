# Security

## Reporting a problem

Please don't open a public issue for a security problem. Use GitHub's
[private vulnerability reporting](../../security/advisories/new) for this repository instead, with
steps to reproduce and the app version.

You'll get a reply within a week. Fixes go out as soon as they're ready, and you'll be credited
unless you'd rather not be.

## Scope

Things worth reporting include:

- Ways another app or a nearby device could read the ride history, documents, contacts or location
  this app stores.
- Ways to trigger crash-alert SMS without a crash, or to stop a real one being sent.
- Problems in the Bluetooth link that could send unintended commands to the bike's cluster.
- A secret or personal data accidentally committed to this repository.

## Safety

The crash alert is a best-effort aid, not a certified emergency system. Reports about detection
accuracy are welcome as normal issues.
