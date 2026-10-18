package com.dauxiliary.core.telegram

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignLogicTest {
    @Test fun parsesTimeAndCrossMidnightWindow() {
        assertEquals(23 * 60 + 30, SignLogic.parseHm("23:30"))
        assertEquals(-1, SignLogic.parseHm("24:00"))
        val range = SignLogic.window("23:30-01:00")
        assertTrue(SignLogic.inWindow(30, range))
        assertTrue(SignLogic.inWindow(23 * 60 + 45, range))
        assertFalse(SignLogic.inWindow(12 * 60, range))
    }

    @Test fun classifiesResultsBeforeUnknown() {
        assertEquals(SignLogic.SIGNED, SignLogic.verdict("签到成功"))
        assertEquals(SignLogic.EXHAUSTED, SignLogic.verdict("今日上限"))
        assertEquals(SignLogic.FAILED, SignLogic.verdict("请先关注"))
        assertEquals(SignLogic.UNKNOWN, SignLogic.verdict("处理中，请稍候"))
    }

    @Test fun skipDecisionProtectsDuplicateExecution() {
        assertEquals(SignLogic.SKIP_IN_FLIGHT, SignLogic.shouldSend(false, true, false, false, 0, 10))
        assertEquals(SignLogic.SKIP_SIGNED, SignLogic.shouldSend(true, false, false, false, 0, 10))
        assertEquals(SignLogic.SKIP_PENDING, SignLogic.shouldSend(false, false, true, false, 0, 10))
        assertEquals(SignLogic.SKIP_DISABLED, SignLogic.shouldSend(false, false, false, true, 0, 10))
        assertEquals(SignLogic.SKIP_BACKOFF, SignLogic.shouldSend(false, false, false, false, 20, 10))
        assertEquals(SignLogic.SKIP_NONE, SignLogic.shouldSend(false, false, false, false, 10, 10))
    }

    @Test fun onlyExplicitSignButtonsAreEligibleForAutomation() {
        assertTrue(SignLogic.looksLikeSignButton("✅ 每日签到"))
        assertTrue(SignLogic.looksLikeSignButton("Check in"))
        assertFalse(SignLogic.looksLikeSignButton("返回主菜单"))
        assertFalse(SignLogic.looksLikeSignButton("账户信息"))
        assertFalse(SignLogic.looksLikeSignButton("", "sign_daily"))
    }

    @Test fun normalizesUnicodeAndPeerIds() {
        assertEquals("-12345", SignLogic.normalizeId("群：－12345"))
        assertEquals("12345", SignLogic.normalizeId("@bot 12-345"))
        assertEquals("", SignLogic.normalizeId("not an id"))
    }
}