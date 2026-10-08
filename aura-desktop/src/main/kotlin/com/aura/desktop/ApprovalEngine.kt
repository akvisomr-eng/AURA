package com.aura.desktop

enum class ApprovalRisk { READ_ONLY, LOW, SENSITIVE, DESTRUCTIVE }

data class ApprovalRequest(
    val action: String,
    val description: String,
    val risk: ApprovalRisk
)

class ApprovalEngine {
    fun classify(action: String): ApprovalRequest {
        val value = action.lowercase()
        val destructive = listOf("hapus", "delete", "remove", "deploy production", "push --force", "shutdown")
        val sensitive = listOf("kirim", "send", "publish", "install", "execute", "jalankan", "ubah", "edit", "commit", "push")
        val risk = when {
            destructive.any(value::contains) -> ApprovalRisk.DESTRUCTIVE
            sensitive.any(value::contains) -> ApprovalRisk.SENSITIVE
            else -> ApprovalRisk.READ_ONLY
        }
        return ApprovalRequest(action, if (risk == ApprovalRisk.READ_ONLY) "Operasi baca/analisis dapat berjalan otomatis." else "Operasi ini mengubah sistem atau data dan memerlukan persetujuan pengguna.", risk)
    }
}
