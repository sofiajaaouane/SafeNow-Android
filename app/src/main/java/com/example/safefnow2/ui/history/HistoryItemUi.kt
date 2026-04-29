package com.example.safefnow2.ui.history

import com.example.safefnow2.data.repository.AlertWithUser

data class HistoryItemUi(
    val alertId: String,
    val name: String,
    val type: String,
    val dateText: String,
) {
    companion object {
        fun from(item: AlertWithUser, currentUserId: String): HistoryItemUi {
            val meId = currentUserId.trim()
            val isSender = item.alert?.senderId != null && item.alert.senderId == meId

            val name =
                if (isSender) (item.alert?.targetName ?: "Recipients")
                else item.sender?.let { "${it.prenom} ${it.nom}".trim() }
                    ?: (item.alert?.senderName ?: "Unknown user")

            val type = when (item.alert?.targetType) {
                "GROUP" -> if (isSender) "GROUP SOS (sent)" else "GROUP SOS (received)"
                "GLOBAL" -> if (isSender) "GLOBAL SOS (sent)" else "GLOBAL SOS (received)"
                "CONTACT" -> if (isSender) "CONTACT SOS (sent)" else "CONTACT SOS (received)"
                else -> item.alert?.typeAlert ?: "SOS"
            }

            val coords = if (item.declaration.latitude != null && item.declaration.longitude != null) {
                " (${String.format("%.4f", item.declaration.latitude)}, ${String.format("%.4f", item.declaration.longitude)})"
            } else ""
            val loc = item.declaration.localisation?.trim().orEmpty()
            val locStr = if (loc.isNotEmpty()) " - $loc$coords" else if (coords.isNotEmpty()) " -$coords" else ""
            val dateText = (item.declaration.createdAt ?: "Unknown date") + locStr

            return HistoryItemUi(
                alertId = item.declaration.idAlert,
                name = name,
                type = type,
                dateText = dateText,
            )
        }
    }
}

