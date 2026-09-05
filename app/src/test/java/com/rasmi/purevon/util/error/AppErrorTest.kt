package com.rasmi.purevon.util.error

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppErrorTest {

    @Test
    fun `AppError holds message type and action`() {
        val error = AppError("Permission denied", ErrorType.PERMISSION, ErrorAction.REQUEST_PERMISSION)
        assertThat(error.message).isEqualTo("Permission denied")
        assertThat(error.type).isEqualTo(ErrorType.PERMISSION)
        assertThat(error.action).isEqualTo(ErrorAction.REQUEST_PERMISSION)
    }

    @Test
    fun `AppError action defaults to null`() {
        val error = AppError("Network unreachable", ErrorType.NETWORK)
        assertThat(error.action).isNull()
    }

    @Test
    fun `AppError equality is field based`() {
        val a = AppError("msg", ErrorType.NETWORK, ErrorAction.RETRY)
        val b = AppError("msg", ErrorType.NETWORK, ErrorAction.RETRY)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `AppError distinguishes different fields`() {
        val a = AppError("msg", ErrorType.NETWORK, ErrorAction.RETRY)
        val b = AppError("msg", ErrorType.NETWORK, ErrorAction.DISMISS)
        assertThat(a).isNotEqualTo(b)
        assertThat(a).isNotEqualTo(AppError("other", ErrorType.NETWORK, ErrorAction.RETRY))
        assertThat(a).isNotEqualTo(AppError("msg", ErrorType.DATABASE, ErrorAction.RETRY))
    }

    @Test
    fun `AppError copy changes only specified field`() {
        val error = AppError("msg", ErrorType.VALIDATION, ErrorAction.GO_TO_SETTINGS)
        val copy = error.copy(action = null)
        assertThat(copy.message).isEqualTo("msg")
        assertThat(copy.type).isEqualTo(ErrorType.VALIDATION)
        assertThat(copy.action).isNull()
    }

    @Test
    fun `AppError with empty and whitespace messages`() {
        assertThat(AppError("", ErrorType.UNKNOWN).message).isEmpty()
        assertThat(AppError("   ", ErrorType.UNKNOWN).message).isEqualTo("   ")
    }

    @Test
    fun `ErrorType enum values`() {
        assertThat(ErrorType.values()).asList().containsExactly(
            ErrorType.PERMISSION,
            ErrorType.NETWORK,
            ErrorType.DATABASE,
            ErrorType.VALIDATION,
            ErrorType.STATE,
            ErrorType.BUSINESS_LOGIC,
            ErrorType.UNKNOWN
        ).inOrder()
    }

    @Test
    fun `ErrorAction enum values`() {
        assertThat(ErrorAction.values()).asList().containsExactly(
            ErrorAction.RETRY,
            ErrorAction.REQUEST_PERMISSION,
            ErrorAction.GO_TO_SETTINGS,
            ErrorAction.RESTART_APP,
            ErrorAction.DISMISS
        ).inOrder()
    }

    @Test
    fun `ErrorType valueOf roundtrip`() {
        assertThat(ErrorType.valueOf("NETWORK")).isEqualTo(ErrorType.NETWORK)
        assertThat(ErrorAction.valueOf("RESTART_APP")).isEqualTo(ErrorAction.RESTART_APP)
    }
}
