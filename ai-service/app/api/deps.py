import hmac
from typing import Annotated
from fastapi import Header, HTTPException, status, Depends

from app.config.settings import Settings, get_settings


def verify_service_key(
    x_service_key: Annotated[str | None, Header(alias="X-Service-Key")] = None,
    settings: Settings = Depends(get_settings),
) -> str:
    """
    Validates the internal X-Service-Key header using constant-time comparison.
    Rejects missing or invalid keys with 401 Unauthorized.
    """
    if not x_service_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing service key",
        )

    # Constant-time comparison prevents timing attacks
    if not hmac.compare_digest(
        x_service_key.strip().encode("utf-8"),
        settings.ai_service_key.strip().encode("utf-8"),
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing service key",
        )

    return x_service_key

