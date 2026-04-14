package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.activity.ContactUiItem
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.util.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContactsOnlineFirstRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)
    private val rtdb = RtdbClient()
    private val isOnline = ConnectivityObserver(appContext).isOnlineFlow()

    fun acceptedContacts(currentUserId: String, search: Flow<String>): Flow<List<ContactUiItem>> {
        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                // Observe both directions and merge accepted ids.
                val outRef = rtdb.ref(RtdbPaths.friendshipOut(currentUserId))
                val inRef = rtdb.ref(RtdbPaths.friendshipIn(currentUserId))

                // Simple approach: whenever out changes, re-read in once too.
                RtdbObserve.observe(outRef).transformLatest { outSnap ->
                    val inSnap = rtdb.get(RtdbPaths.friendshipIn(currentUserId))
                    val accepted = linkedSetOf<String>()

                    fun collectAccepted(snap: com.google.firebase.database.DataSnapshot, takeKey: (com.google.firebase.database.DataSnapshot) -> String?) {
                        snap.children.forEach { child ->
                            val status = child.child("status").getValue(String::class.java) ?: "PENDING"
                            if (status == "ACCEPTED") {
                                takeKey(child)?.let { accepted.add(it) }
                            }
                        }
                    }

                    collectAccepted(outSnap) { it.key }
                    collectAccepted(inSnap) { it.key }

                    val users = accepted.mapNotNull { id ->
                        val uSnap = rtdb.get(RtdbPaths.user(id))
                        uSnap.toUser()
                    }
                    users.forEach { db.userDao().insert(it) }

                    val items = users.map { u ->
                        ContactUiItem(
                            amitierIdUser1 = currentUserId,
                            amitierIdUser2 = u.idUser,
                            contactUserId = u.idUser,
                            prenom = u.prenom,
                            nom = u.nom,
                            fullName = "${u.prenom} ${u.nom}".trim(),
                            phoneNumber = u.numTel
                        )
                    }
                    emit(items)
                }
            },
            offline = {
                // Keep existing Room-driven behavior for offline.
                search.flatMapLatest { q ->
                    combine(db.amitierDao().getAllFlow(), db.userDao().getAllFlow()) { amitiers, users ->
                        val acceptedIds = amitiers.filter { it.status == "ACCEPTED" }
                            .flatMap { listOf(it.idUser1, it.idUser2) }
                            .filter { it != currentUserId }
                            .toSet()
                        val list = users.filter { it.idUser in acceptedIds }
                            .map { u ->
                                ContactUiItem(
                                    amitierIdUser1 = currentUserId,
                                    amitierIdUser2 = u.idUser,
                                    contactUserId = u.idUser,
                                    prenom = u.prenom,
                                    nom = u.nom,
                                    fullName = "${u.prenom} ${u.nom}".trim(),
                                    phoneNumber = u.numTel
                                )
                            }
                        if (q.isBlank()) list else list.filter { it.fullName.contains(q, true) || it.phoneNumber.contains(q) }
                    }
                }
            }
        )
    }
}

