import pytest
from django.contrib.auth import get_user_model
from django.db import IntegrityError

User = get_user_model()


@pytest.mark.django_db
def test_create_user_with_email():
    user = User.objects.create_user(
        email="alex@example.com",
        name="Alex",
        password="a-secure-password",
    )

    assert user.email == "alex@example.com"
    assert user.name == "Alex"
    assert user.timezone == "UTC"
    assert user.is_active is True
    assert user.is_staff is False
    assert user.check_password("a-secure-password")


@pytest.mark.django_db
def test_email_uniqueness():
    User.objects.create_user(
        email="alex@example.com",
        name="Alex",
        password="a-secure-password",
    )

    with pytest.raises(IntegrityError):
        User.objects.create_user(
            email="alex@example.com",
            name="Other",
            password="another-password",
        )


@pytest.mark.django_db
def test_create_superuser():
    admin = User.objects.create_superuser(
        email="admin@example.com",
        name="Admin",
        password="a-secure-password",
    )

    assert admin.email == "admin@example.com"
    assert admin.is_staff is True
    assert admin.is_superuser is True
    assert admin.is_active is True


def test_username_field_is_email():
    assert User.USERNAME_FIELD == "email"
