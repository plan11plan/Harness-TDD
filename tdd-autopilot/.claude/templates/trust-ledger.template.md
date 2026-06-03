# 신뢰 원장 템플릿 (Trust Ledger)

> 증분(API×계층)당 1행. RGR 전 단계 + 운영·신뢰 지표. git 커밋(R/G/R 별도)이 증거. 신뢰도를 *사후 증명·측정* 가능하게(설계 §4.8).

## 행 스키마 (TSV/표)

| 필드군 | 필드 | 예시 |
|--------|------|------|
| 식별 | api, layer, mode | `POST /questions`, domain, standard |
| RED | first_fail_type, red_rejects, test_commit_sha | assertion, 0, `a1b2c3` |
| GREEN | green_retries, target_behavior_result, coverage_line, coverage_branch, coverage_floor_met | 1, PASS, 86%, 79%, true |
| REFACTOR | refactor_items, hold_gate, reverts, complexity_delta, dup_delta | 2, pass, 0, -3, -1 |
| 운영·신뢰 | escalations, auto_recoveries, human_intervened, unattended_complete, arch_pass, commit_shas | [], 1, false, true, pass, `a1b2c3,d4e5f6` |

## 집계(완료 보고용)
- 무인 완주율 = unattended_complete=true 비율
- 자동 복구 수 / 사람 개입 수 / 평균 red_rejects
- RED 정직성 = first_fail_type=assertion 비율
- 목표 동작 통과율 · 커버리지 바닥 충족률

## 예시 행
```
POST /questions | interface | standard | assertion | 1 | <sha> | 0 | PASS | 88% | 81% | true | 1(중복추출) | pass | 0 | -4 | -2 | [] | 1(권한자동보강) | false | true | pass | <shas>
```
