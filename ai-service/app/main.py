"""AI 등록 어시스턴트 서비스 진입점.

React → Spring → ai-service 구조에서 Spring이 내부 호출하는 서비스. 외부에 직접 노출하지 않는다.
"""

from fastapi import FastAPI

from app.routers import health, listing_drafts

app = FastAPI(title="ai-service", version="0.1.0")

app.include_router(health.router)
app.include_router(listing_drafts.router)
