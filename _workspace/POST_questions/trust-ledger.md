# 신뢰 원장 — POST /api/v1/questions (질문 생성)

> 증분(API×계층)당 1행. R/G/R 별도 커밋(SHA)이 증거. 컨텍스트 리셋 후 복원·사후 감사의 근거.
> 브랜치: `harness/question-create` (base `exp/results`).

## 증분 요약 (블로그 §8용)

| 증분 | 레벨 | RED | GREEN | Refactor | 게이트 |
|------|------|-----|-------|----------|--------|
| #1 question-create | 단위 | `607ab6e` | `4f6b03e` | `31eaf8a` | RED✓ GREEN✓ 유지✓ |
| #2 question-create | 통합 | `a77a681` | `498375d` | `08b5fd6` | RED✓ GREEN✓ 동시성✓ |
| #3 question-create | E2E | `3d9745b` | `3fcf152` | `60c527e` | RED✓ GREEN✓ 유지✓ |

## API 완료 게이트 (게이트5·7)
- **게이트5 커버리지(질문 패키지, 수기 코드):** line **98.0%** (100/102) · branch **81.8%** (18/22). raw(생성 QQuestion 포함) 82.6%/81.8%.
  - 고위험 파일: QuestionService 100/100 · QuestionFacade 100/100 · Question 95.0/83.3. → 리스크 바닥(base 80/70, 고위험 90/85) 충족. **PASS**
- **게이트7 잠긴 목표 동작:** QuestionTargetBehaviorE2ETest **9/9 통과**. **PASS**
- **게이트6 고위험 동시성:** 태그 find-or-create 동시 생성 테스트 통과(활성 유니크 → 중복 활성 태그 0). **PASS**
- **게이트4(객관 룰/ArchUnit)·8(뮤테이션):** 생략(`arch_cmd`/`mutation_cmd` 비어있음 — 컨벤션은 Auditor 주관).
- **DoD:** 전체 73 테스트 0 실패 · BUILD SUCCESSFUL.

## 전체 스키마 행

| api | layer | mode | first_fail | red_rejects | test_sha | green_retries | target_behavior | cov_line | cov_branch | floor_met | refactor_items | hold | reverts | escalations | auto_recoveries | human | unattended | arch | commits |
|-----|-------|------|-----------|-------------|----------|---------------|-----------------|----------|------------|-----------|----------------|------|---------|-------------|-----------------|-------|-----------|------|---------|
| POST /questions | domain | standard | assertion | 0 | `607ab6e` | 0 | 9/9 | 95.0% | 83.3% | true | 2 | pass | 0 | [] | 0 | false | true | n/a | `607ab6e→4f6b03e→31eaf8a` |
| POST /questions | application | standard | assertion | 0 | `a77a681` | 0 | 9/9 | 100% | 100% | true | 1 | pass | 0 | [] | 0 | false | true | n/a | `a77a681→498375d→08b5fd6` |
| POST /questions | interface | standard | assertion | 0 | `3d9745b` | 0 | 9/9 | 100% | (DTO 제외) | true | 1 | pass | 0 | [] | 0 | false | true | n/a | `3d9745b→3fcf152→60c527e` |

## 집계 (완료 보고)
- 무인 완주율 100% (3/3 증분 사람 개입 0) · 평균 red_rejects 0 · 자동복구 0 · 에스컬레이션 0.
- RED 정직성 = first_fail=assertion 비율 **100%** (3/3 증분 모두 깨끗한 RED).
- 목표 동작 통과율 9/9 · 커버리지 바닥 충족률 100%.

## 비고
- 각 증분 RED는 골격이 안전한 기본값만 반환 → 의도한 assertion에서 빨개짐(컴파일·런타임 예외 0).
- 단위 getOwned(QUESTION_NOT_FOUND) 1건은 표준 조회라 선구현(증분 #1 RED에서 통과).
- cov_branch domain 83.3%: 미커버 분기는 `content == null` 가드(테스트는 ""/공백/초과로 검증). 기본 invariant라 base 바닥(70) 충족.
