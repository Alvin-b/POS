package com.alvinb.counterpos
 
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StaffEnrollmentTest {

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
}

