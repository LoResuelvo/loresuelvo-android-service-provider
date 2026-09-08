# android-doc-governance

Load this skill when changing `AGENTS.md`, `CLAUDE.md`, `README.md`, a skill,
repository commands, or documented conventions.

## Do not load

Do not load it for an application-only change that does not alter docs,
commands, agent contracts, or skills.

## Source ownership

- `AGENTS.md` is the canonical agent contract: architecture, security, naming,
  testing policy, delivery workflow, and repository rules.
- `CLAUDE.md` is a short compatibility pointer; do not duplicate `AGENTS.md`.
- `README.md` is for human setup, commands, CI prerequisites, and
  troubleshooting.
- `.delivery/README.md` is the authoritative operational reference for gates,
  evidence, jobs, CI, finalization, and recovery.
- Skills explain one workflow and should link to the canonical contract rather
  than copy it in full.

## Skill format

Every skill contains when to load it, when not to load it, concise operational
rules, useful commands or a checklist, and current provider examples. Write
skills in English, keep exact paths and identifiers, and target fewer than 150
lines per skill. Do not document speculative phases or unavailable tools.

## Update rules

Update `AGENTS.md` when architecture, security, commands, conventions, or the
skill index changes. Update `README.md` only for human-facing setup and command
changes. Update the affected skill when its trigger or procedure changes. Keep
deterministic classification and gate rules in `.delivery/policy.v1.json` and
delivery code, not duplicated in every skill.

## Validation

```bash
git diff --check
```

Verify every Markdown link resolves, every documented command exists, and
examples such as `WelcomeViewModel`, `provider-welcome.feature`,
`ApiCategoryRepository`, and `make e2e FLAVOR=Dev` still exist. Confirm the
docs agree with CI's Pixel 6/API 34 emulator and its lack of an AVD bootstrap
workflow. Do not mention unavailable packages, paths, or commands.
