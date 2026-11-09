# NurseFlow Repository Instructions

Before changing application code, configuration, documentation, tests, or deployment files:

1. Read `docs/runbooks/git-flow.md` and inspect `git status --short --branch`.
2. Do not make feature changes directly on `main` or `dev`; use a work branch created from `dev`.
3. Preserve unrelated user changes. Do not overwrite, revert, stash, commit, push, or delete changes outside the current task.
4. Keep roster hard rules enforced on the server. The Android UI is not the authority for schedule validity.
5. Follow the PR template and record verification, skipped checks, API/DB changes, deployment impact, and rollback where applicable.
6. Do not include secrets, leave reasons, or health information in logs, examples, or fixtures.
7. Write commit intent/body, pull request title/body, and user-facing review reports in Korean where possible. Keep Git trailer keys, Conventional Commit types, code identifiers, API paths, and commands in their required original form.
8. Claude Code and Codex use the same active repository worktree and task branch by default. On handoff, continue that branch and its existing changes; do not create a second worktree or branch for the same task. Use separate worktrees only for genuinely independent parallel work, with distinct file ownership.
9. Before starting or resuming work, check the current feature row and its owned paths in `docs/feature-breakdown.md` (section 2), current progress and handoff notes in `docs/work-status.md`, and `git status --short --branch`. For review work, also read `docs/ai-development-review-workflow.md`.
10. After finishing a feature unit, PR, or review, update `docs/work-status.md` on the same task branch with factual progress, exact verification results, and the shared-worktree handoff point.

The repository's `origin` is `https://github.com/gerrmang/NurseFlow.git`. Do not claim GitHub PR review, branch protection, or CI enforcement until those settings and checks are verified.
