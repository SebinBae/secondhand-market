"""환경 설정.

LLM(OpenAI) 접속 정보는 환경변수로 주입한다. 스캐폴드 단계에서는 LLM을 호출하지 않으므로
값이 없어도 서비스는 정상 기동한다(실제 사용은 task-07/08).
"""

import os


class Settings:
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    AI_MODEL: str = os.getenv("AI_MODEL", "gpt-4o")


settings = Settings()
