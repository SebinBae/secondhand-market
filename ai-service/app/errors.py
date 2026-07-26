"""서비스 오류.

LLM 호출 실패가 500(서비스 장애)으로 새어 나가지 않도록, 계약된 `{code, message}` 응답으로
변환할 수 있는 예외로 감싼다. 실제 변환은 `app.main`의 예외 핸들러가 담당한다.
"""


class AiServiceError(Exception):
    """계약된 오류 응답으로 변환되는 예외의 베이스."""

    code = "AI_SERVICE_ERROR"
    status_code = 500

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class LLMCallError(AiServiceError):
    """LLM 호출 실패 또는 구조화 출력 파싱 실패(재시도 후에도 실패)."""

    code = "AI_UPSTREAM_ERROR"
    status_code = 502


class LowConfidenceError(AiServiceError):
    """힌트를 보강해 1회 재분류했는데도 카테고리 신뢰도가 낮은 경우."""

    code = "CATEGORY_LOW_CONFIDENCE"
    status_code = 422
