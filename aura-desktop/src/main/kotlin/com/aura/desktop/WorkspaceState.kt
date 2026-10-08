package com.aura.desktop

import java.util.prefs.Preferences

class WorkspaceState {
    private val prefs = Preferences.userRoot().node("com.aura.desktop.workspace")

    fun load(): String? = prefs.get("workspace", null)?.takeIf { it.isNotBlank() }

    fun save(path: String) {
        prefs.put("workspace", path)
    }

    fun clear() {
        prefs.remove("workspace")
    }
}
