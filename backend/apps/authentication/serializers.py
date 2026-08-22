from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from rest_framework import serializers

from apps.authentication.services import canonicalize_email

User = get_user_model()


class RegisterSerializer(serializers.Serializer):
    email = serializers.EmailField(max_length=User._meta.get_field("email").max_length)
    password = serializers.CharField(write_only=True, trim_whitespace=False)
    name = serializers.CharField(max_length=255, trim_whitespace=True)

    def validate_email(self, value):
        return canonicalize_email(value)

    def validate(self, attrs):
        candidate = User(email=attrs["email"], name=attrs["name"])
        try:
            validate_password(attrs["password"], user=candidate)
        except DjangoValidationError as exc:
            raise serializers.ValidationError({"password": list(exc.messages)}) from None
        return attrs


class LoginSerializer(serializers.Serializer):
    email = serializers.EmailField(max_length=User._meta.get_field("email").max_length)
    password = serializers.CharField(write_only=True, trim_whitespace=False)

    def validate_email(self, value):
        return canonicalize_email(value)


class RefreshSerializer(serializers.Serializer):
    refresh_token = serializers.CharField(write_only=True, trim_whitespace=False)


class GoogleAuthSerializer(serializers.Serializer):
    id_token = serializers.CharField(write_only=True, trim_whitespace=True)
    device_name = serializers.CharField(max_length=128, required=False, default="", allow_blank=True)
    platform = serializers.CharField(max_length=16, required=False, default="android", allow_blank=True)


class VerifyEmailConfirmSerializer(serializers.Serializer):
    token = serializers.CharField(trim_whitespace=True)


class PasswordResetRequestSerializer(serializers.Serializer):
    email = serializers.EmailField()

    def validate_email(self, value):
        return canonicalize_email(value)


class PasswordResetConfirmSerializer(serializers.Serializer):
    token = serializers.CharField(trim_whitespace=True)
    password = serializers.CharField(write_only=True, trim_whitespace=False)

    def validate(self, attrs):
        try:
            validate_password(attrs["password"])
        except DjangoValidationError as exc:
            raise serializers.ValidationError({"password": list(exc.messages)}) from None
        return attrs


class SetPasswordSerializer(serializers.Serializer):
    password = serializers.CharField(write_only=True, trim_whitespace=False)

    def validate(self, attrs):
        try:
            validate_password(attrs["password"])
        except DjangoValidationError as exc:
            raise serializers.ValidationError({"password": list(exc.messages)}) from None
        return attrs


class ChangePasswordSerializer(serializers.Serializer):
    old_password = serializers.CharField(write_only=True, trim_whitespace=False)
    new_password = serializers.CharField(write_only=True, trim_whitespace=False)

    def validate(self, attrs):
        try:
            validate_password(attrs["new_password"])
        except DjangoValidationError as exc:
            raise serializers.ValidationError({"new_password": list(exc.messages)}) from None
        return attrs


class UserResponseSerializer(serializers.ModelSerializer):
    has_password = serializers.SerializerMethodField()
    google_linked = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = (
            "id",
            "email",
            "name",
            "timezone",
            "email_verified",
            "has_password",
            "google_linked",
            "created_at",
        )
        read_only_fields = fields

    def get_has_password(self, obj) -> bool:
        return obj.has_usable_password()

    def get_google_linked(self, obj) -> bool:
        return bool(obj.google_sub)
