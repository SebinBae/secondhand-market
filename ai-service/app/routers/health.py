from fastapi import APIRouter

router = APIRouter()


@router.get("/internal/v1/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
