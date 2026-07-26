"""제목·설명 후처리 검증.

프롬프트로만 제약하면 지켜지지 않는 경우가 있어, 길이와 과장 표현 금지를 코드로도 강제한다.
오류로 떨어뜨리는 대신 규칙에 맞게 정제해 반환한다 — 문구 하나 때문에 등록 자체가
실패하는 편이 사용자에게 더 나쁘기 때문이다.
"""

import re

TITLE_MAX = 40
DESCRIPTION_MAX = 500

# 중고 거래 문구에서 흔한 과장 표현. 사실 확인이 불가능하거나 광고성인 것만 넣는다.
BANNED_EXPRESSIONS = (
    "역대급",
    "최저가",
    "초특가",
    "대박",
    "강추",
    "무조건",
    "완벽",
    "최고급",
    "미친 가격",
    "레전드",
)


def _strip_banned(text: str) -> str:
    for expression in BANNED_EXPRESSIONS:
        text = text.replace(expression, "")
    return re.sub(r"\s+", " ", text).strip()


def _truncate(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[:limit].rstrip()


def enforce_copy_rules(title: str, description: str) -> tuple[str, str]:
    """길이·과장 표현 규칙을 만족하는 (제목, 설명)을 반환한다."""
    clean_title = _truncate(_strip_banned(title), TITLE_MAX)
    clean_description = _truncate(_strip_banned(description), DESCRIPTION_MAX)
    return clean_title, clean_description
