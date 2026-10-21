// Общий файл проекта. Шаблон проверки пакета приложения на устройстве; не относится к отдельной лабораторной.
package com.example.drivenext

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Инструментальная проверка, выполняемая на устройстве Android.
 *
 * См. [документацию по проверкам](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Контекст проверяемого приложения.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.drivenext", appContext.packageName)
    }
}