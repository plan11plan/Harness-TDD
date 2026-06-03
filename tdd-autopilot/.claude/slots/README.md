# 프로젝트 슬롯 작성 가이드

> 이 폴더의 슬롯만 채우면(또는 교체하면) 보편 엔진이 당신 프로젝트에 맞춰 동작한다. **엔진(`agents/`·`skills/`)은 절대 건드리지 않는다.** `*.example.*`를 복사해 프로젝트 값으로 채운 뒤 `.example`을 떼라.

## 슬롯 3종 — 무엇을 / 누가 쓰나 / 필수 여부

| 슬롯 | 무엇을 담나 | 소비 주체 | 필수 |
|------|------------|----------|:---:|
| `conventions.md` | 아키텍처·레이어·DTO/DB·네이밍 규약 | Designer·Green·Auditor (via `project-conventions`) | ✅ |
| `stack.manifest.yaml` | 빌드/테스트/커버리지 명령·레벨 매핑·판정 신호·**git/PR 설정** | Orchestrator·Locked Evaluator | ✅ |
| `risk-profile.md` | 고위험 연산·커버리지 바닥 | Strategist | ✅ |

---

## ① `conventions.md` — 아키텍처·코딩 규약
**넣어야 할 것**
- (필수) **테스트 레벨 ↔ 코드 위치** 매핑 · **네이밍 패턴**(테스트 이름 규칙) · **에러 계약**(상태코드·형식).
- (필수) DB·API 규약: 삭제·유니크·시간·페이징 등.
- (있으면) **아키텍처 의존 방향·금지 패턴** → *기계로 강제 가능한 룰*은 **★**로 표기.

> ⚠️ **★(기계 강제 룰)이 하나도 없으면 `arch_tool`/ArchUnit 게이트는 생성되지 않는다.** 그땐 컨벤션을 Auditor가 *주관*으로만 검사한다. ★ 룰이 있을 때만 게이트가 만들어지고, 나중에 룰을 빼면 게이트·테스트도 삭제된다.

## ② `stack.manifest.yaml` — 실행·판정 (엔진이 *그대로 실행*, 추론 안 함)
**넣어야 할 것**
- (필수) `test_cmd` · `test_select` · `coverage_cmd` · `coverage_parse` · `levels:`(레벨→위치·격리) · `assertion_signals` · `compile_error_signals`.
- (필수) **`git:` 블록** — 자율 주행이 *작업 브랜치에서 일하고 PR로 합치는* 데 쓰는 명령·정책. 엔진은 특정 VCS/호스트를 모르므로 *전부 여기서* 온다:
    - `base_branch`(예: `main`) · `branch_prefix`(예: `tdd` → `tdd/{기능}`)
    - `branch_cmd` · `commit_cmd`(R/G/R 별도 커밋용) · `pr_cmd`(예: `gh pr create …` / `glab mr create …`)
    - `auto_merge: false` (메인 직접 커밋·자동 머지 금지 — 머지는 PR로 사람) · (선택) `draft_pr: true`(시작 시 Draft PR → 실시간 진행 노출)
- (선택) `arch_cmd`/`arch_tool` — **아키텍처 ★룰이 있을 때만**. 없으면 비워둠 → 객관 룰 게이트 생략.
- (선택) `mutation_cmd`/`mutation_tool` — 심층 검사용(risk-profile 고위험 경로 자동). 고위험 없으면 비움.

## ③ `risk-profile.md` — 리스크·임계값
**넣어야 할 것**
- (필수) **커버리지 바닥**(기본 + 고위험 가중).
- (있으면) **고위험 연산 목록**(동시성 테스트 필수 대상) · 심층 검사 자동 적용 대상.

---

## 채우는 법
1. `cp conventions.example.md conventions.md` (3개 모두) → 프로젝트 값으로 수정, `.example` 제거.
2. 또는 질문지 마법사 / 가져오기로 자동 생성(제품 후속).
3. 엔진 실행 시 슬롯이 비거나 모순이면 Strategist가 **pre-flight**에서 사람에게 올린다(추론 진행 안 함).
