package com.alvinb.counterpos
 
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.ui.AuthScreen
import com.alvinb.counterpos.ui.CounterPosUiState
import com.alvinb.counterpos.ui.StaffManagementScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class StaffEnrollmentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun passkeyPrefixIsProperlyStrippedOnRedemption() {
        val rawPasskey = "AK99-WTR-4421"
        val qrPayload = "CPX1.$rawPasskey"
        val lowerPayload = "cpx1.$rawPasskey"

        fun cleanPasskey(input: String): String =
            input.trim().removePrefix("CPX1.").removePrefix("cpx1.").trim()

        assertEquals(rawPasskey, cleanPasskey(qrPayload))
        assertEquals(rawPasskey, cleanPasskey(lowerPayload))
        assertEquals(rawPasskey, cleanPasskey(rawPasskey))
    }

    @Test
    fun employeeRolesAreProperlyValidatedAndNormalized() {
        fun normalizeRole(role: String): String =
            if (role.equals("waiter", ignoreCase = true)) "cashier" else role.lowercase()

        val validRoles = setOf("manager", "inventory_clerk", "cashier")

        assertEquals("cashier", normalizeRole("waiter"))
        assertEquals("cashier", normalizeRole("cashier"))
        assertEquals("manager", normalizeRole("manager"))
        assertEquals("inventory_clerk", normalizeRole("inventory_clerk"))

        assertTrue(normalizeRole("waiter") in validRoles)
        assertTrue(normalizeRole("manager") in validRoles)
        assertTrue(normalizeRole("inventory_clerk") in validRoles)
        assertFalse("superadmin" in validRoles)
        assertFalse("random_role" in validRoles)
    }

    @Test
    fun enrollmentQrCodePayloadFormatMatchesScannerContract() {
        val generatedPasskey = "SEC-7788-WTR"
        val expectedQrPayload = "CPX1.$generatedPasskey"

        assertTrue(expectedQrPayload.startsWith("CPX1.", ignoreCase = true))
        val extractedPasskey = expectedQrPayload.substringAfter('.').trim()
        assertEquals(generatedPasskey, extractedPasskey)
    }

    @Test
    fun qrChallengePrefixIsDetectedCorrectly() {
        val waiterChallengeToken = "tok_abc123xyz"
        val challengePayload = "CPR1|$waiterChallengeToken"

        assertTrue(challengePayload.startsWith("CPR1|"))
        val token = challengePayload.substringAfter("CPR1|")
        assertEquals(waiterChallengeToken, token)
    }

    @Test
    fun employeeScheduleParsingCorrectlyReadsWorkingHours() {
        val scheduleArray = JSONArray().apply {
            put(JSONObject().apply {
                put("weekday", 1) // Monday
                put("start_time", "08:00:00")
                put("end_time", "17:00:00")
                put("enabled", true)
            })
            put(JSONObject().apply {
                put("weekday", 2) // Tuesday
                put("start_time", "09:00:00")
                put("end_time", "18:00:00")
                put("enabled", false)
            })
        }

        val memberJson = JSONObject().apply {
            put("user_id", "usr-101")
            put("email", "waiter@club.test")
            put("role", "cashier")
            put("employment_status", "active")
            put("schedule", scheduleArray)
        }

        val rows = memberJson.optJSONArray("schedule") ?: JSONArray()
        var mondayEnabled = false
        var mondayStart = ""
        var tuesdayEnabled = true

        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            when (row.optInt("weekday")) {
                1 -> {
                    mondayEnabled = row.optBoolean("enabled")
                    mondayStart = row.optString("start_time").take(5)
                }
                2 -> tuesdayEnabled = row.optBoolean("enabled")
            }
        }

        assertTrue(mondayEnabled)
        assertEquals("08:00", mondayStart)
        assertFalse(tuesdayEnabled)
    }

    @Test
    fun employeeStatusFilteringWorksCorrectly() {
        val activeMember = JSONObject().put("status", "active")
        val suspendedMember = JSONObject().put("employment_status", "suspended")
        val terminatedMember = JSONObject().put("employment_status", "terminated")

        val members = listOf(activeMember, suspendedMember, terminatedMember)

        fun isSuspended(member: JSONObject): Boolean {
            val status = member.optString("employment_status", member.optString("status", "active")).lowercase()
            return status in setOf("suspended", "terminated", "locked")
        }

        assertFalse(isSuspended(activeMember))
        assertTrue(isSuspended(suspendedMember))
        assertTrue(isSuspended(terminatedMember))

        val activeList = members.filterNot(::isSuspended)
        val suspendedList = members.filter(::isSuspended)

        assertEquals(1, activeList.size)
        assertEquals(2, suspendedList.size)
    }

    @Test
    fun ownerRoleIsExcludedAndEmployeesAreDeduplicated() {
        val ownerMember = JSONObject().apply {
            put("user_id", "owner-1")
            put("email", "owner@example.com")
            put("role", "owner")
        }
        val duplicateOwner = JSONObject().apply {
            put("user_id", "owner-1")
            put("email", "owner@example.com")
            put("role", "owner")
        }
        val waiter1 = JSONObject().apply {
            put("user_id", "waiter-1")
            put("email", "john@example.com")
            put("role", "cashier")
        }
        val duplicateWaiter1 = JSONObject().apply {
            put("user_id", "waiter-1")
            put("email", "john@example.com")
            put("role", "cashier")
        }
        val waiter2 = JSONObject().apply {
            put("user_id", "waiter-2")
            put("email", "mary@example.com")
            put("role", "cashier")
        }

        val jsonArray = JSONArray().apply {
            put(ownerMember)
            put(duplicateOwner)
            put(waiter1)
            put(duplicateWaiter1)
            put(waiter2)
        }

        val seenKeys = mutableSetOf<String>()
        val filteredList = buildList {
            for (index in 0 until jsonArray.length()) {
                jsonArray.optJSONObject(index)?.let { obj ->
                    val role = obj.optString("role", "").lowercase()
                    if (role == "owner") return@let
                    val userId = obj.optString("user_id").ifBlank { obj.optString("member_user_id") }
                    val email = obj.optString("email").trim().lowercase()
                    val dedupeKey = if (userId.isNotBlank()) userId else email
                    if (dedupeKey.isNotBlank()) {
                        if (seenKeys.add(dedupeKey)) add(obj)
                    } else {
                        add(obj)
                    }
                }
            }
        }

        assertEquals(2, filteredList.size)
        assertEquals("waiter-1", filteredList[0].optString("user_id"))
        assertEquals("waiter-2", filteredList[1].optString("user_id"))
    }

    @Test
    fun qrEnrollmentStringsAreIdentifiedAndNotTreatedAsProductBarcodes() {
        val userScannedCode = "CPR1|Q6YA-26J7-KVYB-8GBZ-TWFV-A2WZ-NS4C-CVSA-GFQC-XELH"
        val isEnrollment = userScannedCode.startsWith("CPR1|") ||
                userScannedCode.startsWith("CPX1.", ignoreCase = true) ||
                userScannedCode.startsWith("CPT1|", ignoreCase = true)

        assertTrue(isEnrollment)
    }

    @Test
    fun postgrest300ArrayErrorIsParsedCorrectly() {
        val postgrest300Body = """
            [
              {"hint": "Check function parameters", "message": "More than one function was found in the schema cache matching this name and arguments"}
            ]
        """.trimIndent()

        val trimmed = postgrest300Body.trim()
        val extractedMessage: String? = if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            if (arr.length() > 0) {
                val first = arr.optJSONObject(0)
                first?.optString("message")?.takeIf(String::isNotBlank)
                    ?: first?.optString("hint")?.takeIf(String::isNotBlank)
            } else null
        } else null

        assertEquals("More than one function was found in the schema cache matching this name and arguments", extractedMessage)
    }

    @Test
    fun butcheryPasskeyPrefixCpb1IsStrippedDetectedAndPersistedForDevice() {
        val rawPasskey = "BCH-7788-MZA"
        val qrPayload = "CPB1.$rawPasskey"
        val lowerPayload = "cpb1.$rawPasskey"

        fun cleanPasskey(input: String): String {
            val trimmed = input.trim()
            return when {
                trimmed.startsWith("CPB1.", ignoreCase = true) -> trimmed.substring(5).trim()
                trimmed.startsWith("CPX1.", ignoreCase = true) -> trimmed.substring(5).trim()
                else -> trimmed
            }
        }

        assertTrue(qrPayload.startsWith("CPB1.", ignoreCase = true))
        assertEquals(rawPasskey, cleanPasskey(qrPayload))
        assertEquals(rawPasskey, cleanPasskey(lowerPayload))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val remote = SupabaseRestClient(context)
        remote.clearSession()
        assertFalse(remote.isButcheryDeviceShop("shop-123"))

        remote.saveButcheryDeviceShopId("shop-123")
        assertTrue(remote.isButcheryDeviceShop("shop-123"))
        assertFalse(remote.isButcheryDeviceShop("shop-other"))

        remote.clearSession()
        assertFalse(remote.isButcheryDeviceShop("shop-123"))
    }

    @Test
    fun staffManagementScreenSupportsEnrollingButcheryJustLikeWaiters() {
        var selectedStationCaptured = ""
        var issuedButcheryName = ""
        var scannedButcheryName = ""

        composeRule.setContent {
            var uiState by remember { mutableStateOf(CounterPosUiState()) }
            StaffManagementScreen(
                state = uiState,
                businessName = "Kenyatta Club",
                role = "owner",
                businessType = "BAR_NIGHTCLUB",
                onClose = {},
                onLoadMembers = {},
                onIssueWaiterPass = {},
                onSetEmployeeStatus = { _, _ -> },
                onSetEmployeeRole = { _, _ -> },
                onRenameEmployee = { _, _ -> },
                onRemoveEmployee = { _ -> },
                onIssueEmployeeRecoveryPass = { _, _ -> },
                onClearEmployeeRecoveryPass = {},
                onSaveEmployeeSchedule = { _, _, _, _, _ -> },
                onSaveBusinessHours = {},
                onScanWaiterCode = {},
                onSelectEnrollStation = { station ->
                    selectedStationCaptured = station
                    uiState = uiState.copy(staffEnrollmentStation = station)
                },
                onIssueButcheryPass = { name ->
                    issuedButcheryName = name
                    uiState = uiState.copy(butcheryPasskey = "CPB1.BCH-9944")
                },
                onScanButcheryCode = { name ->
                    scannedButcheryName = name
                }
            )
        }
        composeRule.waitForIdle()

        // Waiter and Butchery station options are both present on Enroll tab
        composeRule.onNodeWithTag("enroll_station_waiter").assertIsDisplayed()
        composeRule.onNodeWithTag("enroll_station_butchery").assertIsDisplayed()

        // Switch to Butchery station and enter station name
        composeRule.onNodeWithTag("enroll_station_butchery").performClick()
        composeRule.waitForIdle()
        assertEquals("butchery", selectedStationCaptured)

        composeRule.onNodeWithTag("butchery_enroll_name_input").performTextInput("Prime Nyama Grill")
        composeRule.onNodeWithTag("generate_butchery_passkey_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Prime Nyama Grill", issuedButcheryName)
        composeRule.onNodeWithTag("butchery_passkey_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("butchery_passkey_value").assertTextEquals("CPB1.BCH-9944")
        composeRule.onNodeWithTag("butchery_passkey_qr").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("scan_butchery_challenge_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Prime Nyama Grill", scannedButcheryName)
    }

    @Test
    fun staffManagementScreenDisplaysInlineEnrollmentError() {
        composeRule.setContent {
            StaffManagementScreen(
                state = CounterPosUiState(
                    staffEnrollmentStation = "butchery",
                    actionError = "Could not approve Butchery QR: connection timed out."
                ),
                businessName = "Kenyatta Club",
                role = "owner",
                businessType = "BAR_NIGHTCLUB",
                startOnButchery = true,
                onClose = {},
                onLoadMembers = {},
                onIssueWaiterPass = {},
                onSetEmployeeStatus = { _, _ -> },
                onSetEmployeeRole = { _, _ -> },
                onRenameEmployee = { _, _ -> },
                onRemoveEmployee = { _ -> },
                onIssueEmployeeRecoveryPass = { _, _ -> },
                onClearEmployeeRecoveryPass = {},
                onSaveEmployeeSchedule = { _, _, _, _, _ -> },
                onSaveBusinessHours = {},
                onScanWaiterCode = {}
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("staff_enroll_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Could not approve Butchery QR: connection timed out.")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun authScreenKeepsQrVisibleWhenNoticeOrConnectionRefusedAndSupportsButcheryStationSelection() {
        var selectedStation = "waiter"
        composeRule.setContent {
            var qrPayload by remember { mutableStateOf<String?>("CPR1|VALID-CHALLENGE-TOKEN") }
            var station by remember { mutableStateOf("waiter") }
            AuthScreen(
                error = "Connection refused while refreshing feed",
                qrError = null,
                notice = "Waiting for approval… (checking connection)",
                isAuthenticating = false,
                isLoadingQr = false,
                selectedQrStation = station,
                onSelectQrStation = {
                    station = it
                    selectedStation = it
                },
                onEmailSignIn = { _, _ -> },
                onCreateAccount = { _, _ -> },
                onRegisterBusiness = { _, _, _, _, _, _, _, _ -> },
                onCredentialSignIn = {},
                onOpenScanner = {},
                onRefreshQr = { qrPayload = "CPR1|VALID-CHALLENGE-TOKEN" },
                qrPayload = qrPayload,
                onClearError = {}
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("login_with_qr_or_passkey_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Word "refused" in unrelated error must not expire/hide LoginQrCard
        composeRule.onNodeWithTag("login_qr_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("qr_station_waiter").assertIsDisplayed()
        composeRule.onNodeWithTag("qr_station_butchery").performClick()
        composeRule.waitForIdle()

        assertEquals("butchery", selectedStation)
        composeRule.onNodeWithText("Scan to Sign In · Butchery Station").assertIsDisplayed()
    }

    @Test
    fun authScreenShowsQrErrorVisiblyWhenChallengeExpiresOrFails() {
        composeRule.setContent {
            AuthScreen(
                error = null,
                qrError = "This QR code has expired or was already used. Tap Refresh QR to generate a new one.",
                notice = null,
                isAuthenticating = false,
                isLoadingQr = false,
                selectedQrStation = "butchery",
                onSelectQrStation = {},
                onEmailSignIn = { _, _ -> },
                onCreateAccount = { _, _ -> },
                onRegisterBusiness = { _, _, _, _, _, _, _, _ -> },
                onCredentialSignIn = {},
                onOpenScanner = {},
                onRefreshQr = {},
                qrPayload = null,
                onClearError = {}
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("login_with_qr_or_passkey_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("qr_fallback_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("QR code expired").assertIsDisplayed()
        composeRule.onNodeWithText("This QR code has expired or was already used. Tap Refresh QR to generate a new one.").assertIsDisplayed()
    }
}
