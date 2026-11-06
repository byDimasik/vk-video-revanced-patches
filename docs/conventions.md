# Engineering conventions

The standing rules every plan, change and PR is checked against. The repo's and workspace's
CLAUDE.md win where they disagree with this file.

## Tests

- Small unit tests: Go `testing` + testify (`require` for preconditions, `assert` otherwise).
- Large e2e or integration suites, or a repo that already uses them: Ginkgo + Gomega. Match the repo.
- Test-first for product changes: every behaviour change has a test that failed before the change.
- Real fakes over mocks: `httptest.Server`, in-memory stores, temp dirs. Mock only what cannot be faked.
- Test names state the behaviour, not the function.

## Build and tooling

- All build, test, lint and deploy commands go through the repo's Makefile. A missing or broken
  target is fixed in the Makefile, not worked around.
- CI lints before anything that rewrites files (`fmt`), so formatting errors fail the build.
- Tool installs are keyed on their version, so a version bump re-installs.
- Scratch files live in `.tmp-<task>/` in the workspace, never `/tmp`; removed when the task ends.

## Code and docs

- Occam's razor on comments: self-explanatory code first; a comment only for a non-obvious
  dependency between parts of the code. No comment beats a wrong or soon-stale one.
- Document the current state only, never the history of how it got there.
- No ticket IDs in code or docs, except a TODO with the full ticket link.
- Know the audience of the docs. Code contributors can't be treated as repo admins. Customers
  can't be treated as product devs.
- A newly introduced tool uses its current major version. Staying on an older major needs a
  stated reason.
- Check a thing once. Don't repeat a check that an earlier step in the same workflow or call
  path already guarantees. The exception is a long or expensive chain: validating early, before
  a costly step, is fine even if a later step would catch the same error, because by then it's
  too late or too expensive. Repeated checks right next to each other are never fine.
- An output or flag must be true to its name, even when nothing reads it yet.
- YAGNI: nothing beyond what the spec asks for; no speculative options or abstractions.
  Groundwork for something the epic already plans is fine. Its comment says what the
  groundwork is for, not when it will be used: no "today", "for now" or "later", which go stale
  when the plan lands.
- Name functions by what the caller gets:
  - Returns a value and has no side effects: a noun for that value. `podSpec(md) PodSpec`, `graphName(md) string`.
  - Returns a bool: starts with `is` or `has`. `isPodReady(pod) bool`.
  - Constructors start with `New`. `NewReconciler(c) *Reconciler`.
  - Acts (creates, mutates, waits, calls out): a verb. `createPod(spec) error`, `waitForPodReady(pod) error`.
- If conditions read positive: `if now.Before(deadline)`, not `if !now.After(deadline)`.
- Don't reinvent the wheel: use what the language, framework or an established library already
  provides before writing your own. Override a built-in only for a concrete need it can't meet.
- Validation reports every error in one pass, not just the first, so a user fixes them all in
  one run.
- Generate anything that mirrors the code (examples, templates, lists of supported IDs) from
  the code itself; a hand-written copy drifts.
- Pass each component only what it uses, not the whole config or an unrelated bundle of settings.
- Markdown tables are aligned in source: pad every cell with spaces to its column's widest cell,
  so all rows of a column are the same width, and fill the separator row with `-` to that width.
  Re-pad the whole table when a cell changes.

## Git and PRs

- Branches and worktrees start from freshly fetched `upstream/main`, never `origin/main`.
- Plans, specs and other agent artifacts live in the workspace `docs/` folder and are never committed.
- A change over ~1000 added lines (excluding vendored and generated files) is a stack of PRs, one
  concern per layer, each layer building and passing tests on its own. Slightly over is fine when
  it is all one concern.
- Vendored dependencies and generated files are their own top layer.
- PR titles `<area> N/M: <what>` in a stack. Bodies: what and why, how it was tested, what a
  reviewer should look at. No ticket link; the user adds it.

## Stops: ask first

- Releases, publishing to any registry other than the user's personal one, upstream repo settings,
  secrets, variables and Actions.
- History rewrites or force-pushes of anything already under review.
- Merging.
