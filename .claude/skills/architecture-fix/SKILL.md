---
name: architecture-fix
description: 'Fix architectureCheck findings by pure refactoring, one commit per kind. Triggers: "architectureCheck failed", a red build with an architecture finding, or a finding kind name.'
---

# Fixing architectureCheck findings

## Rules

- Pure refactor: move files, split files, move declarations, narrow visibilities; `package` and `import` lines
  follow. No behaviour change. Signatures change only as the fix requires (an inversion, a visibility change).
  Tests move with the source they test.
- One commit per finding kind, in the order below.
- Never edit `config/architecture/catalogue.jsonc` to make a finding go away, except to add a library to
  `libraries` (`librariesWithoutRole`). Never add an `allowedSuppressions` entry without asking the user.
- A finding you can't fix is reported to the user, never hidden. When unsure how to fix one, stop and ask with
  concrete options.

## Get the findings

```bash
GRADLE_ENCRYPTION_KEY=$(openssl rand -base64 16) ./gradlew architectureCheck
```

The findings are printed and written to `build/reports/architecture/report.txt` (and `.md`, `.html`).

## Order

Re-run `architectureCheck` after each step: each step changes the next one's findings.

1. `forbiddenDependencies`
2. `classesPlacedTooLow`
3. `packageCycles`
4. `packagesOutsideModuleRoot`
5. `librariesWithoutRole`
6. `unusedMembers`, then `membersTooVisible`
7. `stringDependencies` and `forbiddenSuppressions`

Layers, from abstract to concrete: Helper → Domain → Infrastructure. Each may depend only on itself and the layers
above it. A package's layer is its deepest `domain` or `infrastructure` segment; any other package is Helper.

## Per kind

- `forbiddenDependencies`: move the class (the whole file) to the package of the layer it needs:
  `….domain` or `….infrastructure` of its module. When callers in a higher layer would then reach a lower one,
  invert the dependency instead (interface in the higher layer, implementation in the lower one) and tell the
  user. An app's wiring (its `main`) is Infrastructure: it goes in `cat.coeli.alerts.<app>.infrastructure`.
- `classesPlacedTooLow`: move the file from `….infrastructure…` to the `….domain` package of its module.
- `packageCycles`: move the type both packages reach for to a package both depend on, or invert one dependency.
  Never duplicate a type.
- `packagesOutsideModuleRoot`: move the package under `cat.coeli.alerts.<module directory without dashes>`.
- `librariesWithoutRole`: add the shortest unambiguous prefix to `libraries`, with the layer the library plays
  for its callers.
- `unusedMembers`: delete the member and the tests that exercise only it. When only tests of other modules use
  it, give those tests what they need through the `testFixtures` of the member's module (e.g. a builder
  function) and delete the member if nothing else needs it.
- `membersTooVisible`: narrow the visibility to the one shown. A data class whose primary constructor becomes
  `internal` gets `@ConsistentCopyVisibility`. Tests of other modules that built it switch to a builder in the
  `testFixtures` of its module.
- `stringDependencies`: use the `libs.…` entry of `gradle/libs.versions.toml` (add it if missing) or the
  `projects.…` accessor.
- `forbiddenSuppressions`: remove the `@Suppress` and fix what the rule reports.

## Before moving anything

Grep the fully qualified names and the package across the repository, docs and build scripts included (an
app's `mainClass`, `CLAUDE.md`):

```bash
grep -rn "cat\.coeli\.alerts\.foo\.domain" . | grep -v "/build/\|/\.git/"
```

## Verify

1. `./gradlew ktfmtFormat build` passes, `architectureCheck` included.
2. `git diff --stat` and skim: only moves, `package` lines, imports and visibilities, plus the splits the fix
   required.

## Report

Per kind: findings before and after, and what moved where. Every finding not fixed, with why.
