from django.core.validators import validate_email
from rest_framework import serializers

from apps.authentication.services import canonicalize_email
from apps.users.models import User


class UserLookupQuerySerializer(serializers.Serializer):
    email = serializers.CharField(
        max_length=User._meta.get_field("email").max_length
    )

    def validate_email(self, value):
        normalized = canonicalize_email(value)
        validate_email(normalized)
        return normalized


class UserLookupSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ("id", "name", "email")
        read_only_fields = fields
