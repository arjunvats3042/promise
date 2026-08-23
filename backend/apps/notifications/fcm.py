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


def _initialize_firebase_app():
    """Initializes firebase_admin default app if not already initialized."""
    import base64
    import json
    import os
    import firebase_admin
    from firebase_admin import credentials

    if firebase_admin._apps:
        return firebase_admin.get_app()

    creds_json = getattr(settings, "FIREBASE_CREDENTIALS_JSON", "") or os.environ.get("FIREBASE_CREDENTIALS_JSON", "")
    project_id = getattr(settings, "FIREBASE_PROJECT_ID", "") or os.environ.get("FIREBASE_PROJECT_ID", "promise-640c8")

    cred = None
    if creds_json:
        creds_json = creds_json.strip()
        # Check if it's base64 encoded
        if not creds_json.startswith("{") and not creds_json.endswith("}"):
            try:
                decoded = base64.b64decode(creds_json).decode("utf-8")
                cred_dict = json.loads(decoded)
                cred = credentials.Certificate(cred_dict)
            except Exception as e:
                logger.warning("Failed to decode base64 FIREBASE_CREDENTIALS_JSON: %s", e)
        else:
            try:
                cred_dict = json.loads(creds_json)
                cred = credentials.Certificate(cred_dict)
            except Exception as e:
                logger.warning("Failed to parse FIREBASE_CREDENTIALS_JSON as JSON: %s", e)

    if cred is None and os.path.exists(creds_json):
        try:
            cred = credentials.Certificate(creds_json)
        except Exception as e:
            logger.warning("Failed to load certificate from path %s: %s", creds_json, e)

    options = {"projectId": project_id} if project_id else {}
    if cred:
        return firebase_admin.initialize_app(cred, options=options)
    else:
        try:
            return firebase_admin.initialize_app(options=options)
        except Exception as e:
            logger.info("Firebase default credentials initialization: %s", e)
            return None


def get_fcm_client() -> FcmClientProtocol:
    """Returns the configured FCM client.

    Uses MockFcmClient if FCM is disabled or credentials are not configured.
    """
    fcm_enabled = getattr(settings, "FCM_ENABLED", False)
    if not fcm_enabled:
        return _mock_client

    try:
        import firebase_admin
        from firebase_admin import messaging

        app = _initialize_firebase_app()
        if app is None:
            logger.warning("Firebase Admin app could not be initialized; falling back to MockFcmClient")
            return _mock_client

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
                # Ensure all data values are strings
                string_data = {k: str(v) for k, v in data.items()} if data else {}
                string_data["title"] = title
                string_data["body"] = body

                android_config = messaging.AndroidConfig(
                    priority=android_priority,
                    data=string_data,
                )
                msg = messaging.Message(
                    token=token,
                    data=string_data,
                    android=android_config,
                )
                try:
                    messaging.send(msg, app=app)
                    return FcmSendResult(token=token, success=True)
                except messaging.UnregisteredError:
                    return FcmSendResult(token=token, success=False, is_unregistered=True, error="UnregisteredError")
                except messaging.SenderIdMismatchError:
                    return FcmSendResult(token=token, success=False, is_unregistered=True, error="SenderIdMismatchError")
                except Exception as exc:
                    return FcmSendResult(token=token, success=False, error=str(exc))

        return FirebaseAdminClient()
    except (ImportError, Exception) as exc:
        logger.warning("Failed to initialize FirebaseAdminClient: %s. Using MockFcmClient.", exc)
        return _mock_client
