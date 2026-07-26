"""AI 등록 어시스턴트 서비스 진입점.

React → Spring → ai-service 구조에서 Spring이 내부 호출하는 서비스. 외부에 직접 노출하지 않는다.
"""

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.errors import AiServiceError
from app.routers import health, listing_drafts
from app.schemas import ErrorResponse

app = FastAPI(title="ai-service", version="0.1.0")

app.include_router(health.router)
app.include_router(listing_drafts.router)


@app.exception_handler(AiServiceError)
def handle_ai_service_error(request: Request, error: AiServiceError) -> JSONResponse:
    """LLM 실패 등을 500이 아닌 계약된 `{code, message}` 응답으로 내보낸다."""
    return JSONResponse(
        status_code=error.status_code,
        content=ErrorResponse(code=error.code, message=error.message).model_dump(),
    )
