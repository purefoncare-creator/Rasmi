package com.rasmi.purevon.util.mms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MmsUtilsErrorMessageTest {

    @Test
    fun `getMmsErrorMessage code 1 is UNSPECIFIED`() {
        assertThat(MmsUtils.getMmsErrorMessage(1)).contains("UNSPECIFIED")
    }

    @Test
    fun `getMmsErrorMessage code 2 is INVALID_APN`() {
        assertThat(MmsUtils.getMmsErrorMessage(2)).contains("INVALID_APN")
    }

    @Test
    fun `getMmsErrorMessage code 3 is UNABLE_CONNECT`() {
        assertThat(MmsUtils.getMmsErrorMessage(3)).contains("UNABLE_CONNECT_MMS")
    }

    @Test
    fun `getMmsErrorMessage code 4 is HTTP_FAILURE`() {
        assertThat(MmsUtils.getMmsErrorMessage(4)).contains("HTTP_FAILURE")
    }

    @Test
    fun `getMmsErrorMessage code 5 is IO_ERROR`() {
        assertThat(MmsUtils.getMmsErrorMessage(5)).contains("IO_ERROR")
    }

    @Test
    fun `getMmsErrorMessage code 6 is RETRY`() {
        assertThat(MmsUtils.getMmsErrorMessage(6)).contains("RETRY")
    }

    @Test
    fun `getMmsErrorMessage code 7 is CONFIGURATION_ERROR`() {
        assertThat(MmsUtils.getMmsErrorMessage(7)).contains("CONFIGURATION_ERROR")
    }

    @Test
    fun `getMmsErrorMessage code 8 is NO_DATA_NETWORK`() {
        assertThat(MmsUtils.getMmsErrorMessage(8)).contains("NO_DATA_NETWORK")
    }

    @Test
    fun `getMmsErrorMessage code 10 is DATA_DISABLED`() {
        assertThat(MmsUtils.getMmsErrorMessage(10)).contains("DATA_DISABLED")
    }

    @Test
    fun `getMmsErrorMessage unknown code 999`() {
        assertThat(MmsUtils.getMmsErrorMessage(999)).contains("999")
    }

    @Test
    fun `getMmsErrorMessage code 0 is unknown`() {
        assertThat(MmsUtils.getMmsErrorMessage(0)).contains("Unknown")
    }

    @Test
    fun `getMmsErrorMessage returns non-blank string for all known codes`() {
        val knownCodes = listOf(1, 2, 3, 4, 5, 6, 7, 8, 10)
        for (code in knownCodes) {
            assertThat(MmsUtils.getMmsErrorMessage(code)).isNotEmpty()
        }
    }

    @Test
    fun `getMmsErrorMessage negative code is unknown`() {
        assertThat(MmsUtils.getMmsErrorMessage(-1)).contains("Unknown")
    }
}
