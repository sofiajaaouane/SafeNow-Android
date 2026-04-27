package com.example.safefnow2.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.safefnow2.data.local.dao.AlertDao
import com.example.safefnow2.data.local.dao.AmitierDao
import com.example.safefnow2.data.local.dao.DeclarationAlertDao
import com.example.safefnow2.data.local.dao.DiseaseDao
import com.example.safefnow2.data.local.dao.EmergencyGroupDao
import com.example.safefnow2.data.local.dao.GroupMemberDao
import com.example.safefnow2.data.local.dao.ItemDao
import com.example.safefnow2.data.local.dao.UserDao
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.Amitier
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.data.local.entity.Item
import com.example.safefnow2.data.local.entity.User

@Database(
        entities =
                [
                        User::class,
                        Alert::class,
                        Amitier::class,
                        DeclarationAlert::class,
                        Disease::class,
                        EmergencyGroup::class,
                        GroupMember::class,
                        Item::class],
        version = 4,
        exportSchema = false
)
abstract class SafeNowDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun alertDao(): AlertDao
    abstract fun amitierDao(): AmitierDao
    abstract fun declarationAlertDao(): DeclarationAlertDao
    abstract fun diseaseDao(): DiseaseDao
    abstract fun emergencyGroupDao(): EmergencyGroupDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun itemDao(): ItemDao
}
