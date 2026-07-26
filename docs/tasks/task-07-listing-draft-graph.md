# task-07: LangGraph 등록 초안 그래프 구현

- 브랜치: `feat/listing-draft-graph`
- 커밋 타입: `feat:`
- 선행 조건: task-06 머지

## 배경

스텁이던 listing-drafts를 실제 LangGraph 그래프로 교체한다. 노드 구조는 기획서의 L1(등록 대리) 범위.

## 그래프 설계 (이 구조로 고정)

```
Intake → Vision → Category → PriceEstimation → Description → END
                     ↓ (분류 실패/저신뢰)
                  Retry(1회, 힌트 보강) → Category
```

- 상태(State): imageUrls, userHint, visionSummary, category, categoryConfidence, priceSuggestion, title, description, retryCount
- 노드당 단일 책임. 재시도 분기는 Category 1개만 (다른 노드에 추가 금지)
- Vision: 이미지 URL을 OpenAI API 멀티모달로 분석 → 물건 종류/브랜드/상태 요약
- Category: 백엔드 ProductCategory enum 중 택1 + confidence. 구조화 출력(스키마) 강제
- PriceEstimation: 현 단계는 모델 일반 지식 기반 + 근거 문장 필수. **유사 매물 검색 없음** (L2에서 도입 — 데이터 콜드스타트)
- Description: 제목 40자 이내, 설명 500자 이내, 과장 표현 금지 규칙을 프롬프트가 아닌 후처리 검증으로도 강제

## 작업 단계

1. 상태 정의 + 노드 5개 + Retry 분기 구현
2. 모든 LLM 호출은 구조화 출력 사용. 파싱 실패 시 해당 노드만 1회 재시도 후 오류 응답
3. LLM 호출부를 인터페이스로 분리 — pytest에서 목으로 대체 가능하게
4. pytest: 그래프 흐름 테스트 (목 LLM으로 정상 경로, 분류 저신뢰→재시도 경로, 재시도 실패→오류 응답)
5. 실 LLM 스모크 1회 실행 후 응답 예시를 PR 본문에 첨부

## 하지 말 것

- 재시도 분기 추가 금지 (Category 1개만)
- 임베딩/유사 매물 검색 구현 금지 (L2 범위)
- 응답이 API 계약(task-06)과 달라지는 변경 금지

## 완료 조건 (DoD)

- [ ] 목 LLM 기준 3개 경로(정상/재시도 성공/재시도 실패)가 pytest로 증명됨
- [ ] 실 이미지 1장 스모크에서 계약 스키마에 맞는 응답 확인 (PR에 예시 첨부)
- [ ] 제목/설명 길이·형식 제약이 후처리 검증 코드로 존재
- [ ] LLM 호출 실패가 서비스 500이 아닌 계약된 오류 응답으로 변환됨
