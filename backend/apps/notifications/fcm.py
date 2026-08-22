from dataclasses import dataclass
import logging
from typing import Any, Dict, List, Optional, Protocol

from django.conf import settings

logger = logging.getLogger("promise")


@dataclass(frozen=True)
class FcmSendResult:
    token: str
    success: bool
    is_unregistered: bool = False
    error: str = ""


class FcmClientProtocol(Protocol):
    def send(
        self,
        *,
        token: str,
        title: str,
        body: str,
        data: Dict[str, str],
        priority: str = "normal",
    ) -> FcmSendResult:
        ...


class MockFcmClient:
    """In-memory FCM client for test environments and local development."""

    def __init__(self):
        self.sent_messages: List[Dict[str, Any]] = []
        self.unregistered_tokens: set[str] = set()
        self.failing_tokens: set[str] = set()

    def send(
        self,
        *,
        token: str,
        title: str,
        body: str,
        data: Dict[str, str],
        priority: str = "normal",
    ) -> FcmSendResult:
        if token in self.unregistered_tokens:
            return FcmSendResult(
                token=token,
                success=False,
                is_unregistered=True,
                error="registration-token-not-registered",
            )
        if token in self.failing_tokens:
            return FcmSendResult(
                token=token,
                success=False,
                is_unregistered=False,
                error="Transient network timeout to FCM",
            )

        message = {
            "token": token,
            "title": title,
            "body": body,
            "data": data,
            "priority": priority,
        }
        self.sent_messages.append(message)
        return FcmSendResult(token=token, success=True)

    def reset(self):
        self.sent_messages.clear()
        self.unregistered_tokens.clear()
        self.failing_tokens.clear()


_mock_client = MockFcmClient()


def get_fcm_client() -> FcmClientProtocol:
    """Returns the configured FCM client.

    Uses MockFcmClient if FCM is disabled or credentials are not configured.
    """
    fcm_enabled = getattr(settings, "FCM_ENABLED", False)
    if not fcm_enabled:
        return _mock_client

    # When live Firebase Admin is configured, initialize here
    try:
        import firebase_admin
        from firebase_admin import messaging

        class FirebaseAdminClient:
            def send(
                self,
                *,
                token: str,
                title: str,
                body: str,
                data: Dict[str, str],
                priority: str = "normal",
            ) -> FcmSendResult:
                android_priority = "high" if priority == "high" else "normal"
                android_config = messaging.AndroidConfig(
                    priority=android_priority,
                    data=data,
                )
                msg = messaging.Message(
                    token=token,
                    data=data,
                    android=android_config,
                )
                try:
                    messaging.send(msg)
                    return FcmSendResult(token=token, success=True)
                except messaging.UnregisteredError:
                    return FcmSendResult(token=token, success=False, is_unregistered=True, error="UnregisteredError")
                except messaging.SenderIdMismatchError:
                    return FcmSendResult(token=token, success=False, is_unregistered=True, error="SenderIdMismatchError")
                except Exception as exc:
                    return FcmSendResult(token=token, success=False, error=str(exc))

        return FirebaseAdminClient()
    except ImportError:
        return _mock_client
