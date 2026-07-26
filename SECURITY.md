# Security policy

## Reporting a vulnerability

Please report suspected security vulnerabilities in this repository
**privately**, not through a public issue or pull request.

- Preferred: use GitHub's [private vulnerability
  reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability)
  — open the **Security** tab on this repository and select **Report a
  vulnerability**. This creates a private advisory visible only to the
  maintainer and lets you attach details, and the maintainer to coordinate a
  fix, without exposing the report publicly beforehand.
- Alternative: email <code@itur.cat> with a description of the issue, steps
  to reproduce, and its potential impact.

Please include:

- The affected version or commit.
- Steps to reproduce, or a proof of concept.
- The potential impact as you understand it.

## Scope

This policy covers the `itur-android` application itself: its Kotlin/Compose
source, build configuration, and the demo/local/prod flavours it ships.

It does not cover:

- Other Itur repositories (each has, or will have, its own security policy).
- Vulnerabilities in third-party dependencies themselves — please report
  those upstream, though a report here that identifies one is still welcome
  so this project can track and update the affected dependency.
- Findings that require physical access to an unlocked, already-compromised
  device.

## What to expect

This is a single-maintainer open-source project without a dedicated
security team or a fixed service-level agreement. Reports are triaged and
acknowledged on a best-effort basis, generally within a few days. There is
no bug-bounty program.

Once a reported vulnerability is confirmed, a fix is prioritized ahead of
other work, and the reporter is credited in the resulting advisory unless
they ask not to be.

## Supported versions

This project has no versioned release process yet: only the latest `main`
is supported. Please reproduce against it before reporting.
