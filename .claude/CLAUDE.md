# Claude 작업 규칙

## 커밋 · PR 작성자

- 커밋의 작성자(author)와 커미터(committer)는 Claude가 아니라 **세션을 쓰는 사람 본인의 GitHub 계정**이다.
  커밋하기 전에 이 저장소의 `git config user.name` · `user.email`을 그 사람의 계정으로 맞춘다
  (`Claude <noreply@anthropic.com>` 그대로 커밋하지 않는다). 이메일은 GitHub 계정에 등록된 것이어야 컨트리뷰터로 잡힌다.
- 커밋 메시지와 PR 본문에 `Co-Authored-By: Claude ...`, `Claude-Session: ...`, "Generated with Claude Code" 같은 attribution을 넣지 않는다.
  컨트리뷰터 목록에 Claude가 올라가면 안 된다.
- 이미 Claude 이름으로 올라간 커밋은 작성자를 바꾸고 attribution 줄을 지워 다시 올린다(자기 작업 브랜치에서만 `--force-with-lease`).
