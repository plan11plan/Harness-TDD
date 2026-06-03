# orchestrator — '비-LLM 운전자'의 실제 코드 (Java)

`skill.md`는 오케스트레이터가 *이렇게 동작한다*는 **계약(프롬프트)**이다 — LLM이 읽고 실행한다.
즉 그 버전은 운전대를 LLM이 잡은 **반자동**이었다(블로그 §9-4 🚩).
`Orchestrator.java`는 그 운전대를 **코드**로 가져온다.

> 언어 선택: 오케스트레이터는 gradle·git·에이전트를 호출하는 *컨트롤 플레인*이라 대상 언어와 무관하다.
> 팀이 한 언어로 유지하도록 **자바 단일 파일**로 구현했다(파이썬/TS도 가능). Java 21+ source-launch라 빌드가 필요 없다.

## 무엇이 코드이고, 무엇이 LLM인가
| 부분 | 누가 | 결정적? |
|------|------|:---:|
| 제어 흐름 (Phase1 → 레벨 루프 → R/G/R → 게이트 → 커밋 → 원장 → PR) | `Orchestrator` (코드) | ✅ |
| 게이트 판정 (RED·GREEN·유지·커버리지·목표동작) | `LockedEvaluator` (코드) — 빌드 결과 코드/로그 신호로만 | ✅ |
| git 커밋(정책 b)·원장·PR | 코드 | ✅ |
| '사고' (테스트·구현·리팩터 작성) | `AgentRunner` → headless `claude -p` (역할 .md = 시스템 프롬프트) | ❌(LLM) |

게이트·임계값은 에이전트가 보지도 고치지도 못한다(인자로 안 넘김). 같은 입력 → 같은 판정.
**오케스트레이터는 에이전트가 아니다** — LLM으로 사고하지 않고 역할 에이전트를 *호출*만 한다.

## 실행 (빌드 불필요 — `java <File>.java`)
```bash
# orchestrator/ 에서 실행하거나 --harness 로 .claude 경로 지정
# 1) 계획만 — 에이전트·gradle 미실행 (매니페스트/흐름 점검). 의존성 0(내장 YAML 파서).
java Orchestrator.java --feature "POST /api/v1/questions" \
    --worktree /path/to/worktree --api POST_questions --dry-run

# 2) 게이트 1개만 실측 — '잠긴 평가자가 진짜 코드'임을 증명 (에이전트 호출 X)
java Orchestrator.java --worktree /path/to/worktree --feature x --api POST_questions \
    --check-gate red --pattern "*QuestionServiceTest" --layer domain
#   → RED 커밋에선 PASS(assertion), GREEN 상태에선 BLOCK(no-failure). 결정적.

# 3) 전체 자율 주행 (claude CLI 필요 — 역할 에이전트를 headless로 호출)
java Orchestrator.java --feature "POST /api/v1/questions" \
    --worktree /path/to/worktree --api POST_questions
```

## 여러 기능 병렬
**기능 = 워크트리 = 작업 브랜치 = 오케스트레이터 인스턴스 (1:1:1:1).**
`new Orchestrator(feature, worktree, …)`가 인스턴스 단위라, 기능마다 하나씩 띄워 각자 브랜치에서 병렬 주행한다(skill.md 인터페이스 C). 진짜 병목은 오케스트레이터가 아니라 **공유 자원**(동시 gradle·Testcontainers·DB 경합) — 워크트리별 `build/`는 분리되지만 컨테이너·DB는 동시성 상한·격리가 필요.

## 적용·교체 지점
- **슬롯에서 전부 읽음**: `--harness <…>/.claude` 의 `slots/stack.manifest.yaml`(명령·레벨·신호·git)·`agents/tdd-*.md`(역할 시스템 프롬프트). 엔진은 스택을 추론하지 않는다. (실 매니페스트 없으면 `.example.yaml`로 폴백)
- **에이전트 러너 교체**: 기본은 `claude -p`. `AgentRunner.run`을 [Claude Agent SDK](https://docs.claude.com)/API 호출로 바꾸면 그대로 동작.
- **risk-profile 연동**: `gateCoverage`의 바닥(현재 80/70 하드코딩)을 `slots/risk-profile.md`에서 읽도록 연결하는 지점이 `pkgPrefix()`/`run()`에 표시돼 있다.

## 한계(정직)
- 이건 **실동작 골격**이다. 게이트·흐름·git·원장·YAML 파서는 실측 검증됨(dry-run·check-gate). 역할 에이전트 품질·에스컬레이션 인박스·동시성(게이트6)·뮤테이션(게이트8)·재실행 캐시는 후속.
- `--check-gate`는 워크트리에 `gate_*.raw.txt`를 쓴다(증거). 전체 주행은 작업 브랜치에 R/G/R을 커밋한다(`auto_merge:false` — 머지는 사람).
