package com.rasmi.purevon.presentation.screen.addcontact

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddContactViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AddContactViewModel(context)

    @Test
    fun `initialize populates initial values`() {
        val vm = createViewModel()
        vm.initialize(
            initialPhoneNumber = "+966501234567",
            initialName = "Alice",
            initialEmail = "",
            company = "Acme",
            contactId = null
        )
        assertThat(vm.uiState.value.firstName).isEqualTo("Alice")
        assertThat(vm.uiState.value.company).isEqualTo("Acme")
        assertThat(vm.uiState.value.phoneEntries).hasSize(1)
        assertThat(vm.uiState.value.phoneEntries[0].number).isEqualTo("+966501234567")
        assertThat(vm.uiState.value.isLoaded).isTrue()
    }

    @Test
    fun `initialize with no phone keeps single empty entry`() {
        val vm = createViewModel()
        vm.initialize(null, null, null, null, null)
        assertThat(vm.uiState.value.phoneEntries).hasSize(1)
        assertThat(vm.uiState.value.phoneEntries[0].number).isEmpty()
    }

    @Test
    fun `initialize is idempotent once loaded`() {
        val vm = createViewModel()
        vm.initialize("+966501234567", "Alice", null, null, null)
        vm.initialize("+966509999999", "Bob", null, null, null)
        assertThat(vm.uiState.value.firstName).isEqualTo("Alice")
    }

    @Test
    fun `updateFirstName sets name and clears suggestion`() {
        val vm = createViewModel()
        vm.updateFirstName("Alice")
        assertThat(vm.uiState.value.firstName).isEqualTo("Alice")
        vm.updateFirstName("Bob")
        assertThat(vm.uiState.value.firstName).isEqualTo("Bob")
    }

    @Test
    fun `addPhoneEntry adds an empty entry`() {
        val vm = createViewModel()
        vm.addPhoneEntry()
        assertThat(vm.uiState.value.phoneEntries).hasSize(2)
        assertThat(vm.uiState.value.phoneEntries[1].number).isEmpty()
    }

    @Test
    fun `removePhoneEntry removes at index`() {
        val vm = createViewModel()
        vm.updatePhoneEntry(0, PhoneEntry("+966501234567"))
        vm.addPhoneEntry()
        vm.addPhoneEntry()
        assertThat(vm.uiState.value.phoneEntries).hasSize(3)
        vm.removePhoneEntry(1)
        assertThat(vm.uiState.value.phoneEntries).hasSize(2)
        assertThat(vm.uiState.value.phoneEntries[0].number).isEqualTo("+966501234567")
    }

    @Test
    fun `updatePhoneEntry with valid number clears error`() {
        val vm = createViewModel()
        vm.updateFirstName("Alice")
        vm.updatePhoneEntry(0, PhoneEntry("+9665555555"))
        assertThat(vm.uiState.value.phoneEntries[0].number).isEqualTo("+9665555555")
        assertThat(vm.uiState.value.phoneErrors).doesNotContain("invalid")
    }

    @Test
    fun `updatePhoneEntry with invalid number sets invalid error`() {
        val vm = createViewModel()
        vm.updatePhoneEntry(0, PhoneEntry("/"))
        assertThat(vm.uiState.value.phoneErrors).contains("invalid")
    }

    @Test
    fun `isValid is false without a name`() {
        val vm = createViewModel()
        vm.updateFirstName("")
        vm.updatePhoneEntry(0, PhoneEntry("+9665555555"))
        assertThat(vm.isValid).isFalse()
    }

    @Test
    fun `isValid is true with name and valid phone`() {
        val vm = createViewModel()
        vm.updateFirstName("Alice")
        vm.updatePhoneEntry(0, PhoneEntry("+9665555555"))
        assertThat(vm.isValid).isTrue()
    }

    @Test
    fun `updateEmail with blank leaves no error`() {
        val vm = createViewModel()
        vm.updateEmail("")
        assertThat(vm.uiState.value.emailError).isNull()
    }

    @Test
    fun `updateGroup sets group selection`() {
        val vm = createViewModel()
        vm.updateGroup(3L, "Family")
        assertThat(vm.uiState.value.selectedGroupId).isEqualTo(3L)
        assertThat(vm.uiState.value.selectedGroupTitle).isEqualTo("Family")
    }

    @Test
    fun `setSelectedPhotoUri updates photo state`() {
        val vm = createViewModel()
        val uri = Uri.parse("content://test/photo")
        vm.setSelectedPhotoUri(uri)
        assertThat(vm.uiState.value.selectedPhotoUri).isEqualTo(uri)
        assertThat(vm.uiState.value.removeExistingPhoto).isFalse()
    }

    @Test
    fun `clearPhoto resets photo and flags removal`() {
        val vm = createViewModel()
        vm.setSelectedPhotoUri(Uri.parse("content://test/photo"))
        vm.clearPhoto()
        assertThat(vm.uiState.value.selectedPhotoUri).isNull()
        assertThat(vm.uiState.value.selectedPhotoBitmap).isNull()
        assertThat(vm.uiState.value.removeExistingPhoto).isTrue()
    }

    @Test
    fun `updateFavorite toggles favorite`() {
        val vm = createViewModel()
        vm.updateFavorite(true)
        assertThat(vm.uiState.value.isFavorite).isTrue()
    }

    @Test
    fun `updateCompany sets company`() {
        val vm = createViewModel()
        vm.updateCompany("Acme")
        assertThat(vm.uiState.value.company).isEqualTo("Acme")
    }

    @Test
    fun `updateBirthday sets birthday`() {
        val vm = createViewModel()
        vm.updateBirthday("1990-01-01")
        assertThat(vm.uiState.value.birthday).isEqualTo("1990-01-01")
    }
}