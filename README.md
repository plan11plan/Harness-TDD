# Harness-TDD — AI 자율주행 TDD 하네스

LLM 에이전트로 기능을 **Red → Green → Refactor**로 자율 구현하되, TDD의 두 약점을
**구조적으로 차단**하는 에이전트 하네스(harness).

- **RED 스킵**: LLM은 테스트가 실패함을 확인하기 전에 구현을 먼저 쓰는 경향이 있다.
- **컨벤션·DoD 드리프트**: 레이어드 규칙(트랜잭션 경계·DTO 분리·soft delete 등)이 기능을 거듭할수록 희석된다.

이 레포는 그 해법인 **`tdd-autopilot` 하네스 본체**와, 그것으로 실제 기능 1개를 무인 구현한
**실행 증거(`_workspace/`)** 를 담은 포트폴리오 증빙 자료다.

> 설계 프레임: Karpathy의 agent loop — *"자율의 한계 = 검증의 한계"*. 검증을 결정적 게이트로
> 끌어올린 만큼만 자율을 허용한다.

---

## 무엇이 들어있나

```
Harness-TDD/
├── tdd-autopilot/      # 하네스 본체 — 보편 엔진 + 프로젝트별 슬롯
│   ├── .claude/
│   │   ├── agents/     # 역할 에이전트 7 (strategist·designer·red-author·
│   │   │               #   green-implementer·refactorer·auditor·coherence-auditor)
│   │   ├── skills/     # 오케스트레이터 + locked-evaluator(심장)·target-behavior
│   │   │               #   + project-conventions·test-design-patterns·mocking·refactoring
│   │   ├── slots/      # ★ 프로젝트별 교체 지점 (conventions·stack.manifest·risk-profile)
│   │   └── templates/  # feature-input·target-behavior·trust-ledger·pr-body
│   └── orchestrator/   # 비-LLM 운전자 실제 코드 (Orchestrator.java, Java 21 single-file)
└── _workspace/         # 실행 증거 — 하네스가 한 기능을 돌려 남긴 실제 산출물
    └── POST_questions/ #   게이트 로그·신뢰 원장·감사·비용 원장·PR 본문
```

---

## 1. `tdd-autopilot/` — 하네스 본체

**단일 기능을 `(API × 계층)` 단위로 자율 주행하며 TDD로 구현**하는 게이트된 팀 파이프라인.
RED을 **결정적 게이트**로 강제하고, **잠긴 목표 동작(locked target behavior)** 으로 의미까지
검증하며, *계약 안에서의 실패는 자동 복구 · 계약 변경은 사람*으로 라우팅한다.

### 2레벨 구조 (보편 엔진 / 프로젝트 슬롯)

| 레벨 | 위치 | 내용 | 누가 바꾸나 |
|------|------|------|-------------|
| **엔진** | `agents/`·`skills/` | 오케스트레이터·게이트·루프·역할 에이전트·목표 동작 규약·신뢰 원장 | 고정 (건드리지 않음) |
| **슬롯** | `slots/` | 컨벤션(SKILL.md)·스택 매니페스트·리스크 프로필 | 프로젝트마다 교체 |

> 엔진은 스택을 **추론하지 않는다.** 빌드/테스트/커버리지 명령·레벨 매핑·git/PR 정책을
> 전부 `slots/stack.manifest.yaml`에서 *그대로 읽어* 실행한다. 슬롯이 비거나 모순이면
> Strategist가 pre-flight에서 사람에게 올린다.

### 무엇이 코드이고, 무엇이 LLM인가 (`orchestrator/`)

`skill.md`는 오케스트레이터의 동작을 LLM이 읽는 *계약(프롬프트)* 으로 적은 반자동 버전이고,
`Orchestrator.java`는 그 운전대를 **결정적 코드**로 가져온 것이다.

| 부분 | 누가 | 결정적? |
|------|------|:---:|
| 제어 흐름 (Phase1 → 레벨 루프 → R/G/R → 게이트 → 커밋 → 원장 → PR) | `Orchestrator` (코드) | ✅ |
| 게이트 판정 (RED·GREEN·유지·커버리지·목표동작) | `LockedEvaluator` (코드, 빌드 신호만) | ✅ |
| '사고' (테스트·구현·리팩터 작성) | 역할 에이전트 → headless `claude -p` | ❌ (LLM) |

게이트·임계값은 에이전트에게 인자로 넘기지 않는다 — **에이전트가 보지도 고치지도 못한다.**
같은 입력 → 같은 판정.

```bash
# 빌드 불필요 (Java 21 source-launch). 계획만 점검 — 에이전트·gradle 미실행:
java Orchestrator.java --feature "POST /api/v1/questions" \
    --worktree /path/to/worktree --api POST_questions --dry-run

# 게이트 1개만 실측 — '잠긴 평가자가 진짜 코드'임을 증명 (에이전트 호출 X):
java Orchestrator.java --worktree /path/to/worktree --feature x --api POST_questions \
    --check-gate red --pattern "*QuestionServiceTest" --layer domain
```

---

## 2. `_workspace/` — 실행 증거

하네스가 신규 도메인의 한 기능(`POST /api/v1/questions`)을 **무인으로 완주**하며 남긴
실제 산출물이다. 손대지 않은 원본 — 폴더명 `_workspace/`는 하네스가 런타임에 실제로 쓰는
출력 경로 그대로다.

| 파일 | 무엇 |
|------|------|
| `00.strategy.md` | 사전 전략 (기능 분해·계약·리스크) |
| `domain·application·interface/gate_red.txt` | **RED 게이트 증거** — 각 레벨 최초 실패가 assertion이었음을 기록 |
| `trust-ledger.md` | 증분(API×계층)당 R/G/R 커밋 SHA·게이트 판정·자동복구·에스컬레이션 |
| `audit.md` | 독립 감사자(tdd-auditor) 판정 |
| `cost-measurement-ledger.md` | 시간·gradle·토큰·도구호출 원시 데이터 + 계산식 |
| `PR_BODY.md` | 사람이 머지 승인할 때 보는 PR 본문 (진행·결과·증거 요약) |

### 이 한 번의 주행이 보여준 것

- **무인 완주** — 3증분(단위/통합/E2E) 사람 개입 0 · 에스컬레이션 0 · 자동복구 0
- **RED 정직성 3/3 (100%)** — 모든 증분 최초 실패가 깨끗한 assertion RED (컴파일·런타임 예외 0)
- **잠긴 목표 동작 9/9** — 구현이 참조할 수 없는 숨겨진 E2E로 의미 검증
- **커버리지 게이트 통과** — 질문 패키지 line 98.0% / branch 81.8%, 고위험 파일 100/100
- **고위험 동시성 통과** — 태그 find-or-create 8스레드 동시 생성 → 활성 태그 1개(활성 유니크 불변식)
- **감사 판정 PASS(NITS)** — 전략↔테스트 정합·3레벨 존재·테스트 동결·전체 빌드 ✓
- 측정 시간: 첫 커밋→마지막 32분(커밋 델타). gradle 실행은 전체의 3–7%, 본체는 *추론·기존코드 파악·증거 작성*

> 토큰/비용 정확값은 세션 `/cost` 기준이며, 원장의 토큰 수치 일부는 추정임을 명시한다
> (`cost-measurement-ledger.md` §5 한계 참조). 증거는 **측정값과 추정을 구분**해 기록했다.

---

## 핵심 원칙

1. 자율 = 검증 경계.
2. 잠긴 평가자 — 에이전트가 게이트·임계값에 접근 불가.
3. 계약 안 = 자동복구 / 계약 변경 = 사람.
4. 품질 바닥 일정 (호라이즌 불변).
5. 사람은 전략 레벨 · 계약 관문 · 완료 PR 리뷰에서만 개입 (자동 머지 없음).
