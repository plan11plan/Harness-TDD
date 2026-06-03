# PR — POST /api/v1/questions (질문 생성)  ·  브랜치 `harness/question-create` → `exp/results`

> 자율 TDD 하네스(tdd-autopilot) 산출. 사람은 이 PR 하나로 **진행·결과물·증거**를 확인하고 머지를 승인한다. (자동 머지 없음 — `git.auto_merge: false`)
> base = `exp/results` (main엔 의존 도메인 user/category/tag가 없어 base로 사용).

## 1. 요약
- 기능/계약: `POST /api/v1/questions` — 질문/꼬리질문 생성(내용 1~2000, 카테고리 필수, 태그 find-or-create, 기본 PRIVATE)
- 목표 동작 승인·동결: Phase1 커밋 `1b85741` (수용 기준에서 도출, 구현 비참조)
- 주행 결과: **무인 완주** · 사람 개입 0 · 에스컬레이션 0 · 자동복구 0
- 레벨: 단위 7 · 통합 4 · E2E 4 (3레벨 필수 충족) · 잠긴 목표 동작 9

## 2. 감사 판정 (tdd-auditor, 독립 검증)
- **판정: PASS (NITS)**
- 정합성: 전략↔테스트 ✓ · 3레벨 존재 ✓ · RED 정직성 ✓(3/3 assertion) · 테스트 동결 ✓ · 리팩터 품질 ✓ · 전체 빌드 ✓
- NITS(🟢 참고): ArchUnit/뮤테이션 게이트 미설정(사람 리뷰 의존) · domain 분기 83.3%(null 가드 미단언) · E2E·목표동작 일부 중복

## 3. 게이트 통과표 (locked-evaluator · 결정적)
| 게이트 | 결과 | 근거 |
|--------|:---:|------|
| 1 RED (assertion) | ✅ | `_workspace/POST_questions/{domain,application,interface}/gate_red.txt` |
| 2 테스트-먼저·동결 | ✅ | test SHA < impl SHA (3증분) · 테스트 파일 RED 커밋 이후 무수정 |
| 3 GREEN·유지 | ✅ | 각 레벨 GREEN 후 구현 커밋, 리팩터 후 유지 확인 |
| 4 객관 룰(ArchUnit) | — | `arch_cmd` 미설정 → 생략(컨벤션 Auditor 주관 검증) |
| 5 커버리지 바닥 | ✅ | 질문 패키지 line 98.0% / branch 81.8%(수기) · 고위험 파일 QuestionService·Facade 100/100 |
| 6 고위험 동시성 | ✅ | 태그 find-or-create 8스레드 동시 생성 → 활성 태그 1개(활성 유니크 불변식) |
| 7 잠긴 목표 동작 | ✅ | QuestionTargetBehaviorE2ETest 9/9 |
| 8 심층(뮤테이션) | — | `mutation_cmd` 미설정 → 생략 |

## 4. 커밋 = 증거 (R/G/R 별도, 작업 브랜치)
| 증분 (api/layer) | RED | GREEN | REFACTOR |
|------------------|-----|-------|----------|
| POST /questions · 단위 | `607ab6e` | `4f6b03e` | `31eaf8a` |
| POST /questions · 통합 | `a77a681` | `498375d` | `08b5fd6` |
| POST /questions · E2E  | `3d9745b` | `3fcf152` | `60c527e` |

(+ Phase1 계약 동결 `1b85741`, 원장 확정 `28f8637`)

## 5. 신뢰 원장 요약
- 증분 3 · 자동복구 0 · 에스컬레이션 0 · 사람 개입 0 · RED 정직성 3/3(100%)
- 전체 원장: `_workspace/POST_questions/trust-ledger.md` (이 브랜치 동봉)

## 6. 변경 (diff)
- 신규: `question/{domain,application,infrastructure,interfaces}` + 3계층 테스트 + 목표 동작 + `_workspace/POST_questions/*`
- `git diff exp/results...harness/question-create`

## 7. 미해결 · 후속
- 에스컬레이션 미해결: 없음
- 후속(NITS): ArchUnit 객관 룰 도입 검토 · `content==null` 분기 단위 보강 · 질문 목록/상세/수정/삭제 유스케이스(본 증분 범위 외)

---
### 머지 가이드 (사람)
- 승인 → 사람이 머지. 변경요청 → 같은 브랜치 재실행 → 추가 커밋 → 이 PR 자동 갱신 → 재리뷰.
