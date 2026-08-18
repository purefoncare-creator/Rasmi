package com.rasmi.purevon.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class AppExceptionTest {

    // ════════════════════════════════════════════════════════════
    // Exception hierarchy instantiation
    // ════════════════════════════════════════════════════════════

    @Test
    fun `NetworkException has default message`() {
        val ex = AppException.NetworkException()
        assertThat(ex.message).contains("Network")
    }

    @Test
    fun `DatabaseException has default message`() {
        val ex = AppException.DatabaseException()
        assertThat(ex.message).contains("Database")
    }

    @Test
    fun `PermissionException stores permission name`() {
        val ex = AppException.PermissionException(permission = "SEND_SMS")
        assertThat(ex.permission).isEqualTo("SEND_SMS")
    }

    @Test
    fun `NotFoundException stores itemType`() {
        val ex = AppException.NotFoundException(itemType = "Contact")
        assertThat(ex.itemType).isEqualTo("Contact")
        assertThat(ex.message).contains("Contact")
    }

    @Test
    fun `ValidationException stores custom message`() {
        val ex = AppException.ValidationException(message = "Invalid input")
        assertThat(ex.message).isEqualTo("Invalid input")
    }

    @Test
    fun `SystemOperationException stores operation`() {
        val ex = AppException.SystemOperationException(operation = "send SMS")
        assertThat(ex.operation).isEqualTo("send SMS")
        assertThat(ex.message).contains("send SMS")
    }

    @Test
    fun `FileException has default message`() {
        val ex = AppException.FileException()
        assertThat(ex.message).isNotEmpty()
    }

    @Test
    fun `UnknownException has default message`() {
        val ex = AppException.UnknownException()
        assertThat(ex.message).contains("unknown")
    }

    @Test
    fun `exceptions carry cause`() {
        val rootCause = RuntimeException("root")
        val ex = AppException.NetworkException(cause = rootCause)
        assertThat(ex.cause).isEqualTo(rootCause)
    }

    // ════════════════════════════════════════════════════════════
    // toAppException
    // ════════════════════════════════════════════════════════════

    @Test
    fun `toAppException returns same AppException unchanged`() {
        val original = AppException.NetworkException()
        assertThat(original.toAppException()).isSameInstanceAs(original)
    }

    @Test
    fun `toAppException maps IOException to NetworkException`() {
        val ioEx = SocketTimeoutException("timeout")
        val result = ioEx.toAppException()
        assertThat(result).isInstanceOf(AppException.NetworkException::class.java)
    }

    @Test
    fun `toAppException maps SecurityException to PermissionException`() {
        val secEx = SecurityException("not allowed")
        val result = secEx.toAppException()
        assertThat(result).isInstanceOf(AppException.PermissionException::class.java)
    }

    @Test
    fun `toAppException maps IllegalArgumentException to ValidationException`() {
        val argEx = IllegalArgumentException("bad arg")
        val result = argEx.toAppException()
        assertThat(result).isInstanceOf(AppException.ValidationException::class.java)
    }

    @Test
    fun `toAppException maps unknown exception to UnknownException`() {
        val unknownEx = RuntimeException("something")
        val result = unknownEx.toAppException()
        assertThat(result).isInstanceOf(AppException.UnknownException::class.java)
    }

    @Test
    fun `toAppException preserves original message`() {
        val ioEx = IOException("custom network msg")
        val result = ioEx.toAppException()
        assertThat(result.message).contains("custom network msg")
    }

    // ════════════════════════════════════════════════════════════
    // getUserMessage
    // ════════════════════════════════════════════════════════════

    @Test
    fun `getUserMessage for NetworkException mentions internet`() {
        assertThat(AppException.NetworkException().getUserMessage()).contains("internet")
    }

    @Test
    fun `getUserMessage for DatabaseException mentions storage`() {
        assertThat(AppException.DatabaseException().getUserMessage()).contains("Storage")
    }

    @Test
    fun `getUserMessage for PermissionException mentions permission`() {
        val msg = AppException.PermissionException(permission = "SMS").getUserMessage()
        assertThat(msg).contains("SMS")
    }

    @Test
    fun `getUserMessage for NotFoundException mentions item type`() {
        val msg = AppException.NotFoundException(itemType = "Contact").getUserMessage()
        assertThat(msg).contains("Contact")
    }

    @Test
    fun `getUserMessage for ValidationException returns own message`() {
        val msg = AppException.ValidationException(message = "Invalid").getUserMessage()
        assertThat(msg).isEqualTo("Invalid")
    }

    @Test
    fun `getUserMessage for SystemOperationException mentions operation`() {
        val msg = AppException.SystemOperationException(operation = "send SMS").getUserMessage()
        assertThat(msg).contains("send SMS")
    }

    @Test
    fun `getUserMessage for FileException mentions storage`() {
        assertThat(AppException.FileException().getUserMessage()).contains("storage")
    }

    @Test
    fun `getUserMessage for UnknownException mentions something went wrong`() {
        assertThat(AppException.UnknownException().getUserMessage()).contains("Something went wrong")
    }

    // ════════════════════════════════════════════════════════════
    // Exception is also an Exception
    // ════════════════════════════════════════════════════════════

    @Test
    fun `AppException is a subtype of Exception`() {
        val ex: Exception = AppException.NetworkException()
        assertThat(ex).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `AppException can be caught as Exception`() {
        try {
            throw AppException.DatabaseException()
        } catch (e: Exception) {
            assertThat(e).isInstanceOf(AppException.DatabaseException::class.java)
        }
    }
}
