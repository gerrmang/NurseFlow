# NurseFlow Git Branch and PR Policy

This adapts the current branch runbook used in [TRI-BE](https://github.com/wingwogus/TRI-BE/blob/dev/docs/runbooks/git-flow.md) and the policy update in [TRI-BE PR #14](https://github.com/wingwogus/TRI-BE/pull/14). NurseFlow's `origin` is [gerrmang/NurseFlow](https://github.com/gerrmang/NurseFlow); GitHub PR enforcement remains a setup step until rules and checks are verified.

## Branch Roles

| Branch | Purpose | Start point | Merge target |
| --- | --- | --- | --- |
| `main` | Production-ready/release baseline | Release or hotfix only | — |
| `dev` | Integration branch for the next release | Current `main` baseline; then reviewed work PRs | `main` for release |
| `feat/{issue}-{name}` | New feature | Latest `dev` | `dev` |
| `fix/{issue}-{name}` | Ordinary bug fix | Latest `dev` | `dev` |
| `chore/{name}` | Tooling, dependency, documentation, or maintenance | Latest `dev` | `dev` |
| `hotfix/{issue}-{name}` | Urgent production fix | Latest `main` | `main`, then sync the same fix to `dev` |

If there is no issue number, use a concise descriptive suffix, such as `feat/roster-rule-validator`.

## Normal Work

1. Before writing, check the current branch and worktree status; preserve unrelated changes.
2. Start each normal work branch from the latest reviewed `dev` state. Do not develop directly on `main` or `dev`.
3. Keep branches short-lived and changes reviewable. Open a PR to `dev`; do not push directly to `main` or `dev`.
4. Merge only after the required checks pass, review comments are resolved, and the PR checklist is complete. Use Squash Merge for ordinary feature, fix, and chore PRs; delete the merged work branch.
5. Release through a reviewed `dev` → `main` PR. Record included work, checks, deployment impact, and rollback target.
6. For production hotfixes, branch from `main`, merge the reviewed fix to `main`, then sync the same fix to `dev` through a PR.

## Commit and PR Titles

Use Conventional Commit subjects, e.g. `feat(roster): 휴가 시간대 배치 검증`. Allowed types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.

For decision-heavy commits, follow the repository's Lore trailer format: intent line, context, then applicable `Constraint:`, `Rejected:`, `Confidence:`, `Scope-risk:`, `Reversibility:`, `Directive:`, `Tested:`, and `Not-tested:` trailers.

## Review and Verification

- A shared team repository should require at least one approval for normal changes and two for high-risk changes, plus passing CI and resolved conversations.
- High-risk changes include authentication/authorization, schedule rules, personal data, API/DB contracts, secrets, deployment, and production configuration.
- For a solo repository, do not claim independent approval. Use a documented self-review and retain CI/verification requirements.
- PRs should include a summary, related issue, verification and skipped checks, API/DB/environment changes, deployment impact, rollback, risks, and a completion checklist.

## GitHub Enforcement Status

The initial documentation baseline is committed, and `dev` and `main` exist locally and on `origin`. Configure `dev` and `main` protection, PR requirements, required CI checks, and approval rules in GitHub. Do not describe these server-side controls as active until verified.
