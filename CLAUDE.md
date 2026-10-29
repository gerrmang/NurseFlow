@AGENTS.md

# NurseFlow project context for Claude Code

- Start with `docs/mvp-scope.md` for the agreed demo MVP, then read `docs/requirements.md`, `docs/openapi.yaml`, `docs/project-plan.md`, and `docs/test-plan.md` as needed. `docs/mvp-scope.md` wins when older planning text differs.
- Follow `docs/ai-development-review-workflow.md`: use Opus for design decisions and Sonnet for bounded implementation; Codex independently reviews new changes every 30 minutes.
- The imported `AGENTS.md` contains OMX runtime workflows. Use those only when that runtime is available; its repository rules and Lore commit protocol still apply in Claude Code.
- The repo has `backend/` (Kotlin Spring Boot + local H2) and `android/` (Kotlin Compose Figma prototype). The responsive web client and Kakao login/invitation flow are not implemented yet.
- Inspect `git status --short --branch` before edits. The backend, Android, and planning checkpoint is on `feat/roster-rule-validator` (commit `bbadc97`); `main` and `dev` do not yet contain those projects. Base new worktrees on the intended branch and preserve any later uncommitted work.
- The server is authoritative for roster constraints and publication. Approved leave and half-day intervals are entered by the ward administrator. Nurses read the published ward/personal calendar; self-entry and preferences are outside this demo MVP.
- Use fictional demo staff only. Never place Kakao credentials, real nurse information, or leave reasons in code, fixtures, logs, prompts, or commits.
- Work in small feature branches from `dev`. Submit feature PRs to `dev`; release with a reviewed `dev` to `main` PR. Do not merge or push to `main` directly.
