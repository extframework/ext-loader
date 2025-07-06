package com.kaolinmc.extloader.uber

import com.kaolinmc.tooling.api.uber.UberDescriptor
import kotlin.test.Test

class RandomTest {
    @Test
    fun `Test random`() {
        repeat(100) {
            val desc = UberDescriptor("New description")

            println(desc.randomId)
        }
    }
}