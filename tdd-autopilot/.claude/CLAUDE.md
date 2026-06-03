# TDD Autopilot Harness — 자율주행 TDD 루프 엔진

프로젝트별 SKILL.md(아키텍처·컨벤션·스택)를 슬롯으로 받아, **단일 기능을 (API×계층) 단위로 자율 주행하며 TDD 구현**하는 게이트된 팀 파이프라인. RED을 결정적 게이트로 강제하고, **잠긴 목표 동작**로 의미까지 검증하며, **계약 안에서의 실패는 자동 복구·계약 변경은 사람**으로 라우팅한다.

> 설계 근거:프레임: Karpathy agent loop("자율 = 검증의 한계").

## 2레벨 구조

- **엔진(이 패키지):** 보편 — 오케스트레이터·게이트·루프·역할 에이전트·목표 동작 규약·신뢰 원장. 모든 프로젝트 공통.
- **슬롯(`slots/`):** 프로젝트별 — 컨벤션(SKILL.md)·스택 매니페스트·리스크 프로필. 갈아끼우는 부분.
- 면훈소는 이 엔진의 **첫 레퍼런스 인스턴스**(`slots/*.example.*`).

## 디렉토리

```
.claude/
├── CLAUDE.md
├── agents/      strategist · designer · red-author · green-implementer · refactorer · auditor · coherence-auditor
├── skills/      tdd-autopilot-harness(오케스트레이터) · locked-evaluator(심장) · target-behavior
│                + project-conventions(★프로젝트별 컨벤션 슬롯) · test-design-patterns · mocking-strategy · refactoring-catalog
├── slots/       프로젝트 커스터마이즈 예시(conventions / stack.manifest / risk-profile)
└── templates/   feature-input · target-behavior · trust-ledger · pr-body
```

## 사용법

1. **슬롯 채우기:** `slots/`의 3개를 프로젝트 것으로 교체(또는 질문지/가져오기로 생성 — 제품 후속).
2. **트리거:** `tdd-autopilot-harness` 스킬을 호출하거나 "이 기능 TDD로 자율 구현" 요청 + `templates/feature-input.template.md`로 기능 입력.
3. 엔진이 사전 전략·잠금 → 작업 브랜치 생성 → (API×계층) 루프(R/G/R 커밋, 게이트 통과 후) → 감사 → **PR**. 멈출 일은 에스컬레이션 인박스로.

## 한 기능을 돌리면 나오는 산출물 (예: `POST /api/v1/questions`)

루프는 **테스트 레벨(단위/통합/E2E)별로** 돌며 각 레벨 테스트를 만든다. 레벨↔파일 위치·도구는 매니페스트 `levels:`가 정의(프로젝트마다 다름). **아래는 면훈소 슬롯을 끼웠을 때의 산출 예** — 다른 프로젝트면 경로·프레임워크가 통째로 바뀐다:

    src/test/.../question/
    ├── domain/QuestionTest.java                      # 단위 — 불변식(깊이 제한·소유권), fake/mock repo
    ├── application/QuestionFacadeIntegrationTest.java # 통합 — Testcontainers MySQL, 트랜잭션·조합·동시성
    └── interfaces/QuestionE2ETest.java               # E2E — RANDOM_PORT, 201/401/400 + 남의 리소스 404
    _target_behavior/QuestionTargetBehaviorE2ETest.java                # 목표 동작 — 독립·숨김(에이전트 비공개), 평가자만 실행
    src/main/.../question/...                          # 구현(domain/application/interface)
    _workspace/POST_questions/                         # 단계 보고 · gate_*.txt · 신뢰 원장

→ **한 기능 = src/test 3계층 파일(필수, DoD) + 숨겨진 목표 동작 1 + 구현 + 워크스페이스 로그.** 3계층 누락 시 품질·정합성 감사자가 FAIL.
→ **git: 작업 브랜치 `harness/{기능}`에 R/G/R 별도 커밋(게이트 통과 후 = 정책 b) + 원장 동봉, 완료 시 PR(본문 = 원장 요약 + 감사 + diff) → 사람이 리뷰·머지(자동 머지 X).**

## 3계층 스킬 시스템 (harness-100 규약)

| Layer | This harness |
|-------|--------------|
| Orchestrator | `tdd-autopilot-harness/skill.md` |
| Agent-Extending | `locked-evaluator` · `target-behavior` · `project-conventions`(★프로젝트별 슬롯=조립 산출물) · `test-design-patterns` · `mocking-strategy` · `refactoring-catalog` |
| External | 스택 매니페스트가 가리키는 빌드/테스트/커버리지/아키텍처검사 도구(`{test_cmd}` 등) |

## 핵심 원칙

1. 자율 = 검증 경계. 2. 잠긴 평가자(에이전트 비접근). 3. 계약 안=자동복구 / 계약 변경=사람. 4. 품질 바닥 일정(호라이즌 불변). 5. 사람은 전략 레벨 + 계약 관문 + 완료 PR 리뷰에서만.
