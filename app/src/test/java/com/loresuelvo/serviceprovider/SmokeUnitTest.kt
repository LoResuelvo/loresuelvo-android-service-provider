package com.loresuelvo.serviceprovider

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test for the JVM unit-test pipeline.
 *
 * Two scenarios on purpose:
 *  - Pure JUnit sanity check.
 *  - MockK sanity check to prove the mock library is on the classpath.
 *
 * Both must stay green across every flavor / CI job.
 */
class SmokeUnitTest {

    @Test
    fun should_add_two_numbers_with_junit() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun should_stub_a_mockk_collaborator() {
        val calculator = mockk<Calculator>()
        every { calculator.add(2, 3) } returns 5

        assertEquals(5, calculator.add(2, 3))
    }

    private interface Calculator {
        fun add(a: Int, b: Int): Int
    }
}
