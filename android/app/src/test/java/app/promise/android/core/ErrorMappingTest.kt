package app.promise.android.core

import app.promise.android.data.network.ApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMappingTest {
    @Test
    fun goalCodes_mapToKinds() {
        assertEquals(
            ErrorKind.InvalidCheckIn,
            ApiException(status = 400, code = "GOAL_INVALID_CHECKIN").toErrorKind(),
        )
        assertEquals(
            ErrorKind.ScheduleLocked,
            ApiException(status = 409, code = "GOAL_SCHEDULE_LOCKED").toErrorKind(),
        )
        assertEquals(
            ErrorKind.TimezoneLocked,
            ApiException(status = 409, code = "GOAL_TIMEZONE_LOCKED").toErrorKind(),
        )
        assertEquals(
            ErrorKind.NotFound,
            ApiException(status = 404, code = "GOAL_NOT_FOUND").toErrorKind(),
        )
        assertEquals(
            ErrorKind.Conflict,
            ApiException(status = 409, code = "GOAL_INVALID_TRANSITION").toErrorKind(),
        )
    }

    @Test
    fun goalMessages_areHuman() {
        assertEquals(
            "That check-in isn’t valid for this goal.",
            ErrorKind.InvalidCheckIn.toUserMessage(),
        )
        assertEquals(
            "Schedule can’t change after the first check-in.",
            ErrorKind.ScheduleLocked.toUserMessage(),
        )
        assertEquals(
            "Allow local network access so Promise can reach your computer.",
            ErrorKind.LocalNetworkDenied.toUserMessage(),
        )
    }

    @Test
    fun emailAlreadyExists_mapsBeforeGenericConflict() {
        assertEquals(
            ErrorKind.EmailAlreadyExists,
            ApiException(status = 409, code = "EMAIL_ALREADY_EXISTS").toErrorKind(),
        )
        assertEquals(
            "An account with this email already exists.",
            ErrorKind.EmailAlreadyExists.toUserMessage(),
        )
    }

    @Test
    fun validationError_carriesFieldErrors() {
        val kind = ApiException(
            status = 400,
            code = "VALIDATION_ERROR",
            fieldErrors = mapOf("email" to "Enter a valid email address."),
        ).toErrorKind() as ErrorKind.Validation
        assertEquals("Enter a valid email address.", kind.fields["email"])
    }

    @Test
    fun sharedGoalCodes_mapToKinds() {
        assertEquals(
            ErrorKind.AlreadyParticipant,
            ApiException(status = 409, code = "GOAL_ALREADY_PARTICIPANT").toErrorKind(),
        )
        assertEquals(
            ErrorKind.InviteExpired,
            ApiException(status = 400, code = "GOAL_INVITE_EXPIRED").toErrorKind(),
        )
        assertEquals(
            ErrorKind.InviteRevoked,
            ApiException(status = 400, code = "GOAL_INVITE_REVOKED").toErrorKind(),
        )
        assertEquals(
            ErrorKind.OwnerCannotLeave,
            ApiException(status = 400, code = "GOAL_OWNER_CANNOT_LEAVE").toErrorKind(),
        )
        assertEquals(
            ErrorKind.NotFound,
            ApiException(status = 404, code = "USER_NOT_FOUND").toErrorKind(),
        )
    }
}
