---
name: android-doc-governance
description: Apply when changing repository documentation, agent contracts, skills, commands, or documented conventions.
---
# android-doc-governance

Load this skill when changing `AGENTS.md`, `CLAUDE.md`, `README.md`, a skill,
repository commands, or documented conventions.

## Do not load

Do not load it for an application-only change that does not alter docs,
commands, agent contracts, or skills.

## Source ownership

- `AGENTS.md` is the concise repository-wide contract: invariant boundaries,
  safety rules, Delivery ownership, and contextual skill routing. Keep
  task-specific commands and procedures in their owning docs or skills.
- `CLAUDE.md` is a short compatibility pointer; do not duplicate `AGENTS.md`.
- `README.md` is for human setup, commands, CI prerequisites, and
  troubleshooting.
- `.delivery/README.md` is the authoritative operational reference for gates,
  evidence, jobs, CI, finalization, and recovery.
- Skills explain one workflow and should link to the canonical contract rather
  than copy it in full.

## Skill format

Give each skill a clear trigger and only the constraints or procedures that
change decisions for its task. Add exclusions, commands, checklists, and
provider examples when they prevent a likely mistake; do not require all of
them in every skill. Keep entrypoints concise and move conditional detail to
linked references. Review a skill above 150 lines for a useful split, without
treating that count as an enforced limit. Write in English, keep exact paths
and identifiers, and do not document speculative phases or unavailable tools.

## Update rules

Update `AGENTS.md` when a repository-wide invariant or skill route changes.
Update `README.md` only for human-facing setup and command changes. Update the
affected skill when its trigger or procedure changes. Keep
deterministic classification and gate rules in `.delivery/policy.v1.json` and
delivery code, not duplicated in every skill.

## Validation

```bash
git diff --check
```

Verify changed Markdown links, commands, and provider examples against the
repository. When changing CI documentation, confirm it agrees with the
checked-in emulator and AVD bootstrap workflows. Do not mention unavailable
packages, paths, or commands.
