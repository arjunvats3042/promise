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


class UserResponseSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ("id", "email", "name", "timezone", "created_at")
        read_only_fields = fields
