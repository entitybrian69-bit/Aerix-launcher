package net.kdt.pojavlaunch

import net.kdt.pojavlaunch.refreshstub.FakeScreen

object Logger {
    @JvmStatic fun appendToLog(text: String) { FakeScreen.logs.add(text) }
}
