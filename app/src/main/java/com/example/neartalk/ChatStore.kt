package com.example.neartalk

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** App-private local history. NOT encrypted at rest. No network or cloud sync. */
internal class ChatStore(context: Context) : SQLiteOpenHelper(context, "neartalk_history.db", null, 1) {
    data class Peer(val id: String, val name: String)
    data class ChatMessage(val id: String, val peerId: String, val body: String,
                           val mine: Boolean, val timestamp: Long, val status: String)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE peers (id TEXT PRIMARY KEY, name TEXT NOT NULL, last_seen INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, peer_id TEXT NOT NULL, body TEXT NOT NULL, mine INTEGER NOT NULL, created INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE INDEX messages_by_peer ON messages(peer_id, created)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Database v1 has no upgrade path yet; future migrations must preserve user data.
    }

    fun savePeer(id: String, name: String) {
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("last_seen", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("peers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun peers(): List<Peer> {
        val result = mutableListOf<Peer>()
        readableDatabase.rawQuery("SELECT id, name FROM peers ORDER BY last_seen DESC", null).use {
            while (it.moveToNext()) result.add(Peer(it.getString(0), it.getString(1)))
        }
        return result
    }

    fun peerName(peerId: String): String = readableDatabase.rawQuery(
        "SELECT name FROM peers WHERE id=?", arrayOf(peerId)
    ).use { if (it.moveToFirst()) it.getString(0) else "Unknown device" }

    /** Returns false if a received message was already saved. */
    fun add(message: ChatMessage): Boolean {
        val values = ContentValues().apply {
            put("id", message.id)
            put("peer_id", message.peerId)
            put("body", message.body)
            put("mine", if (message.mine) 1 else 0)
            put("created", message.timestamp)
            put("status", message.status)
        }
        return writableDatabase.insertWithOnConflict(
            "messages", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun setStatus(messageId: String, peerId: String, status: String) {
        val values = ContentValues().apply { put("status", status) }
        writableDatabase.update("messages", values, "id=? AND peer_id=? AND mine=1", arrayOf(messageId, peerId))
    }

    fun setStatusIfCurrent(messageId: String, peerId: String, expected: String, status: String) {
        val values = ContentValues().apply { put("status", status) }
        writableDatabase.update("messages", values, "id=? AND peer_id=? AND status=? AND mine=1",
            arrayOf(messageId, peerId, expected))
    }

    fun messages(peerId: String): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT id, peer_id, body, mine, created, status FROM (SELECT id, peer_id, body, mine, created, status, rowid FROM messages WHERE peer_id=? ORDER BY created DESC, rowid DESC LIMIT 500) ORDER BY created ASC, rowid ASC",
            arrayOf(peerId)
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(ChatMessage(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getInt(3) == 1, cursor.getLong(4), cursor.getString(5)
            ))
        }
        return result
    }

    fun delete(peerId: String) {
        writableDatabase.delete("messages", "peer_id=?", arrayOf(peerId))
        writableDatabase.delete("peers", "id=?", arrayOf(peerId))
    }
}
