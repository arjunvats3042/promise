from django.core.validators import validate_email
from rest_framework import serializers

from apps.authentication.services import canonicalize_email
from apps.users.models import User


class UserLookupQuerySerializer(serializers.Serializer):
    email = serializers.CharField(
        max_length=User._meta.get_field("email").max_length,
        required=False,
    )
    q = serializers.CharField(
        max_length=100,
        required=False,
    )

    def validate(self, attrs):
        email = attrs.get("email")
        q = attrs.get("q")
        if not email and not q:
            raise serializers.ValidationError("Either 'email' or 'q' parameter is required.")
        if email:
            normalized = canonicalize_email(email)
            validate_email(normalized)
            attrs["email"] = normalized
        if q:
            trimmed = " ".join(q.strip().split())
            if len(trimmed) < 2:
                raise serializers.ValidationError("Search query must be at least 2 characters.")
            attrs["q"] = trimmed
        return attrs


class UserLookupSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ("id", "name", "email")
        read_only_fields = fields
