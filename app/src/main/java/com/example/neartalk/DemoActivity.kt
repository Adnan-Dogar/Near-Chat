package com.example.neartalk

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Local, interactive UX preview: nothing in this Activity is sent to other phones. */
class DemoActivity : Activity() {
    private data class DemoMessage(
        var text: String,
        val mine: Boolean,
        val time: Long,
        var reaction: String = "",
        val mediaUri: String = "",
        val mediaType: String = "",
        val id: String = UUID.randomUUID().toString(),
        var starred: Boolean = false,
        var edited: Boolean = false
    )
    private data class DemoChat(
        val id: String,
        var title: String,
        val group: Boolean,
        var pinned: Boolean = false,
        var unread: Int = 0,
        val messages: MutableList<DemoMessage> = mutableListOf(),
        var archived: Boolean = false,
        var muted: Boolean = false,
        var blocked: Boolean = false,
        var members: String = ""
    )
    private val chats = mutableListOf<DemoChat>()
    private var activeChat: String? = null
    private var tab = "Chats"
    private var displayName = "Alex Morgan"
    private var about = "Hey there! I'm using Near-Chat."
    private var dark = true
    private var notifications = true
    private var replyTo: String = ""
    private var chatFilter = "All"
    private var showArchived = false
    private var pendingAttachmentChat: String? = null
    private var recordingChat: String? = null
    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var player: MediaPlayer? = null
    private val statuses = mutableListOf<Pair<Long, String>>()
    private val prefs by lazy { getSharedPreferences("nearchat_demo_v4", MODE_PRIVATE) }

    private val ink get() = if (dark) 0xFFECF5F7.toInt() else 0xFF112B35.toInt()
    private val muted get() = if (dark) 0xFF9BAEBB.toInt() else 0xFF67808D.toInt()
    private val bg get() = if (dark) 0xFF0A1822.toInt() else 0xFFF4F8F9.toInt()
    private val card get() = if (dark) 0xFF142A37.toInt() else Color.WHITE
    private val soft get() = if (dark) 0xFF1C3644.toInt() else 0xFFE6EFF1.toInt()
    private val teal = 0xFF3AE2BD.toInt()
    private val tealDark = 0xFF073B3B.toInt()
    private var content: LinearLayout? = null
    private var messageField: EditText? = null
    private var scroll: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0B2530.toInt()
        window.navigationBarColor = 0xFF091822.toInt()
        load()
        showHome("Chats")
    }

    private fun load() {
        dark = prefs.getBoolean("dark", true)
        notifications = prefs.getBoolean("notifications", true)
        displayName = prefs.getString("name", "Alex Morgan") ?: "Alex Morgan"
        about = prefs.getString("about", "Hey there! I'm using Near-Chat.") ?: ""
        val saved = prefs.getString("chats", null)
        if (saved != null) try {
            val root = JSONArray(saved)
            for (i in 0 until root.length()) {
                val obj = root.getJSONObject(i)
                val chat = DemoChat(obj.getString("id"), obj.getString("title"), obj.optBoolean("group"), obj.optBoolean("pinned"), obj.optInt("unread"), archived = obj.optBoolean("archived"), muted = obj.optBoolean("muted"), blocked = obj.optBoolean("blocked"), members = obj.optString("members"))
                val entries = obj.optJSONArray("messages") ?: JSONArray()
                for (m in 0 until entries.length()) {
                    val msg = entries.getJSONObject(m)
                    chat.messages.add(DemoMessage(msg.optString("text"), msg.optBoolean("mine"), msg.optLong("time"), msg.optString("reaction"), msg.optString("mediaUri"), msg.optString("mediaType"), msg.optString("id", UUID.randomUUID().toString()), msg.optBoolean("starred"), msg.optBoolean("edited")))
                }
                chats.add(chat)
            }
        } catch (_: Exception) { chats.clear() }
        if (chats.isEmpty()) seed()
        val savedStatuses = prefs.getString("statuses", "") ?: ""
        if (savedStatuses.isNotEmpty()) try {
            val root = JSONArray(savedStatuses)
            for (i in 0 until root.length()) {
                val item = root.getJSONObject(i)
                if (System.currentTimeMillis() - item.optLong("time") < 86_400_000L) {
                    statuses.add(item.optLong("time") to item.optString("text"))
                }
            }
        } catch (_: Exception) {}
    }

    private fun seed() {
        val now = System.currentTimeMillis()
        fun chat(name: String, group: Boolean, minutes: Int, first: String, last: String, unread: Int = 0) {
            chats.add(DemoChat(UUID.randomUUID().toString(), name, group, name == "Design Crew", unread, mutableListOf(
                DemoMessage(first, false, now - (minutes + 22) * 60_000L),
                DemoMessage("Sounds good!", true, now - (minutes + 7) * 60_000L),
                DemoMessage(last, false, now - minutes * 60_000L)
            )))
        }
        chat("Sarah Khan", false, 4, "Hey! Have you seen the new design?", "I sent the latest mockups ✨", 2)
        chat("Design Crew", true, 18, "Mia: The project looks amazing!", "Sam: Let's review it tomorrow.", 5)
        chat("Daniel", false, 77, "Want to grab coffee tomorrow?", "Perfect, see you there ☕")
        chat("Family Group", true, 236, "Mom: Dinner at 8!", "Dad: See you all soon 💚")
        chat("Aisha", false, 1258, "The photos turned out great!", "Thanks for your help yesterday 😊")
        save()
    }

    private fun save() {
        val arr = JSONArray()
        for (chat in chats) {
            val obj = JSONObject().put("id", chat.id).put("title", chat.title).put("group", chat.group).put("pinned", chat.pinned).put("unread", chat.unread).put("archived", chat.archived).put("muted", chat.muted).put("blocked", chat.blocked).put("members", chat.members)
            val messages = JSONArray()
            for (m in chat.messages) messages.put(JSONObject().put("text", m.text).put("mine", m.mine).put("time", m.time).put("reaction", m.reaction).put("mediaUri", m.mediaUri).put("mediaType", m.mediaType).put("id", m.id).put("starred", m.starred).put("edited", m.edited))
            obj.put("messages", messages)
            arr.put(obj)
        }
        val updateArray = JSONArray()
        for ((time, text) in statuses) updateArray.put(JSONObject().put("time", time).put("text", text))
        prefs.edit().putString("statuses", updateArray.toString()).putString("chats", arr.toString()).putString("name", displayName).putString("about", about).putBoolean("dark", dark).putBoolean("notifications", notifications).apply()
    }

    private fun shape(color: Int, radius: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun dp(v: Int) = (resources.displayMetrics.density * v + .5f).toInt()
    private fun label(text: String, size: Int = 14, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        includeFontPadding = true
    }
    private fun column(padding: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (padding != 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun button(text: String, emphasized: Boolean = false, tap: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
        setTextColor(if (emphasized) tealDark else ink)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = shape(if (emphasized) teal else soft, 13)
        setOnClickListener { tap() }
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun header(parent: LinearLayout, title: String, subtitle: String) {
        val bar = row().apply { setPadding(dp(20), dp(14), dp(20), dp(12)); setBackgroundColor(if (dark) 0xFF102734.toInt() else Color.WHITE) }
        val logo = ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); scaleType = ImageView.ScaleType.FIT_CENTER }
        bar.addView(logo, LinearLayout.LayoutParams(dp(46), dp(46)))
        val titles = column().apply { setPadding(dp(10), 0, 0, 0) }
        titles.addView(label(title, 21, ink, true))
        titles.addView(label(subtitle, 11, muted))
        bar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val pill = label("● DEMO", 11, teal, true).apply {
            setPadding(dp(10), dp(7), dp(10), dp(7)); background = shape(0xFF174838.toInt(), 20)
        }
        bar.addView(pill)
        parent.addView(bar)
    }
    private fun shell(title: String, sub: String): LinearLayout {
        val root = column().apply { setBackgroundColor(bg) }
        setContentView(root)
        header(root, title, sub)
        val scroller = ScrollView(this).apply { setFillViewport(true); clipToPadding = false }
        val inside = column(18)
        scroller.addView(inside)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        content = inside
        bottomNav(root)
        return inside
    }
    private fun bottomNav(root: LinearLayout) {
        val nav = row().apply {
            setPadding(dp(6), dp(7), dp(6), dp(10))
            setBackgroundColor(if (dark) 0xFF102734.toInt() else Color.WHITE)
        }
        for ((name, glyph) in listOf("Chats" to "◉", "Updates" to "◷", "Calls" to "◖", "You" to "☻")) {
            val item = column().apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(6), dp(4), dp(4)) }
            val selected = tab == name
            item.addView(label(glyph, 25, if (selected) teal else muted, selected).apply { gravity = Gravity.CENTER })
            item.addView(label(name, 11, if (selected) teal else muted, selected).apply { gravity = Gravity.CENTER })
            item.setOnClickListener { showHome(name) }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        root.addView(nav)
    }
    private fun blockTitle(parent: LinearLayout, title: String, desc: String = "") {
        parent.addView(label(title, 20, ink, true).apply { setPadding(dp(2), dp(14), 0, dp(5)) })
        if (desc.isNotBlank()) parent.addView(label(desc, 13, muted).apply { setPadding(dp(2), 0, 0, dp(10)) })
    }
    private fun panel(parent: LinearLayout): LinearLayout {
        val c = column(15).apply { background = shape(card, 18) }
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return c
    }
    private fun showHome(which: String) {
        activeChat = null; tab = which
        val inside = shell("Near-Chat", "Stay connected, beautifully")
        when (which) {
            "Chats" -> showChatList(inside)
            "Updates" -> showUpdates(inside)
            "Calls" -> showCalls(inside)
            else -> showProfile(inside)
        }
    }
    private fun showChatList(inside: LinearLayout) {
        val welcome = panel(inside).apply { background = shape(if (dark) 0xFF123B3A.toInt() else 0xFFDBF8F0.toInt(), 18) }
        welcome.addView(label("Near-Chat • your conversations", 19, ink, true))
        welcome.addView(label("Private on-device preview. Try messages, groups, voice notes, status, photos and documents.", 12, muted))
        val search = EditText(this).apply {
            hint = "⌕  Search chats and messages"; textSize = 14f; setSingleLine(true)
            setTextColor(ink); setHintTextColor(muted)
            setPadding(dp(15), 0, dp(15), 0); background = shape(card, 15)
        }
        inside.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(15) })
        val actions = row()
        actions.addView(button("＋ New chat", true) { createChat(false) }, LinearLayout.LayoutParams(0, dp(45), 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        actions.addView(button("♧ New group") { createChat(true) }, LinearLayout.LayoutParams(0, dp(45), 1f))
        inside.addView(actions)
        val filters = row().apply { setPadding(0, dp(12), 0, 0) }
        for (filter in listOf("All", "Unread", "Groups", "Starred")) {
            val item = label(filter, 12, if (chatFilter == filter) tealDark else ink, chatFilter == filter).apply {
                gravity = Gravity.CENTER
                background = shape(if(chatFilter == filter) teal else card, 13)
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            filters.addView(item, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(5) })
            item.setOnClickListener { chatFilter = filter; showHome("Chats") }
        }
        inside.addView(filters)
        inside.addView(button(if (showArchived) "← Show active conversations" else "▣ Archived conversations (${chats.count { it.archived }})") {
            showArchived = !showArchived
            showHome("Chats")
        }.apply { setPadding(dp(7), dp(10), dp(7), dp(10)) })
        blockTitle(inside, if (showArchived) "Archived messages" else "Messages", "Long press to manage a conversation")
        val list = column()
        inside.addView(list)
        fun refresh(query: String) {
            list.removeAllViews()
            val filtered = chats.filter { chat ->
                chat.archived == showArchived &&
                (chatFilter == "All" || (chatFilter == "Unread" && chat.unread > 0) ||
                (chatFilter == "Groups" && chat.group) ||
                (chatFilter == "Starred" && chat.messages.any { it.starred })) &&
                (query.isBlank() || chat.title.contains(query, true) || chat.messages.any { it.text.contains(query, true) })
            }.sortedWith(compareByDescending<DemoChat> { it.pinned }.thenByDescending { it.messages.lastOrNull()?.time ?: 0L })
            if (filtered.isEmpty()) list.addView(label("No chats found. Create a new conversation.", 13, muted))
            for (chat in filtered) renderChatRow(list, chat)
        }
        refresh("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    private fun avatar(text: String, color: Int): TextView = label(text.take(1).uppercase(Locale.ROOT), 21, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; background = shape(color, 50)
    }
    private fun colorFor(value: String): Int = listOf(0xFF4B7FD0, 0xFF7654C9, 0xFF168F88, 0xFFB9754A, 0xFFC05F83)[(value.hashCode() and Int.MAX_VALUE) % 5].toInt()
    private fun renderChatRow(parent: LinearLayout, chat: DemoChat) {
        val r = row().apply { setPadding(dp(12), dp(13), dp(12), dp(13)); background = shape(card, 16) }
        r.addView(avatar(if (chat.group) "♧" else chat.title, colorFor(chat.title)), LinearLayout.LayoutParams(dp(49), dp(49)))
        val center = column().apply { setPadding(dp(13), 0, 0, 0) }
        val nameRow = row()
        nameRow.addView(label(chat.title + (if (chat.pinned) "  📌" else "") + (if (chat.muted) "  🔕" else "") + (if (chat.blocked) "  ⛔" else ""), 15, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        nameRow.addView(label(formatTime(chat.messages.lastOrNull()?.time ?: System.currentTimeMillis()), 11, muted))
        center.addView(nameRow)
        val last = chat.messages.lastOrNull()?.text ?: "Start a conversation"
        val subtitle = row()
        subtitle.addView(label((if (chat.messages.lastOrNull()?.mine == true) "You: " else "") + last.take(45), 12, muted), LinearLayout.LayoutParams(0, -2, 1f))
        if (chat.unread > 0) subtitle.addView(label(chat.unread.toString(), 11, tealDark, true).apply {
            gravity = Gravity.CENTER; background = shape(teal, 20)
        }, LinearLayout.LayoutParams(dp(23), dp(23)))
        center.addView(subtitle)
        r.addView(center, LinearLayout.LayoutParams(0, -2, 1f))
        r.setOnClickListener { openChat(chat.id) }
        r.setOnLongClickListener {
            val options = arrayOf(if (chat.pinned) "Unpin" else "Pin", "Mark as read", if (chat.archived) "Unarchive" else "Archive", if (chat.muted) "Unmute" else "Mute", "Delete local conversation")
            AlertDialog.Builder(this).setTitle(chat.title).setItems(options) { _, index ->
                when(index) {
                    0 -> chat.pinned = !chat.pinned
                    1 -> chat.unread = 0
                    2 -> chat.archived = !chat.archived
                    3 -> chat.muted = !chat.muted
                    4 -> chats.remove(chat)
                }
                save(); showHome("Chats")
            }.show()
            true
        }
        parent.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }
    private fun formatTime(time: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))

    private fun openChat(id: String) {
        val chat = chats.firstOrNull { it.id == id } ?: return
        activeChat = id; replyTo = ""; chat.unread = 0; save()
        val root = column().apply { setBackgroundColor(bg) }
        setContentView(root)
        val head = row().apply { setPadding(dp(14), dp(15), dp(14), dp(13)); setBackgroundColor(card) }
        head.addView(button("‹") { showHome("Chats") })
        head.addView(avatar(if (chat.group) "♧" else chat.title, colorFor(chat.title)), LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(10) })
        val meta = column().apply { setPadding(dp(12), 0, 0, 0) }
        meta.addView(label(chat.title, 17, ink, true))
        meta.addView(label("LOCAL DEMO • ${if (chat.group) "Group" else "Private chat"}", 10, teal))
        head.addView(meta, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(button("⋮") { chatOptions(chat) })
        root.addView(head)
        root.addView(label("Only visible on this phone • Nothing is sent to another person", 11, muted).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
        })
        val messages = column(13)
        val scroller = ScrollView(this).apply { setFillViewport(true); addView(messages) }
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll = scroller
        for (msg in chat.messages) renderMessage(messages, chat, msg)
        scroller.post { scroller.fullScroll(View.FOCUS_DOWN) }
        val compose = row().apply { setPadding(dp(8), dp(9), dp(8), dp(12)); setBackgroundColor(card) }
        compose.addView(button("＋") { chooseAttachment(chat.id) })
        compose.addView(button("🎙") { toggleRecording(chat.id) })
        val field = EditText(this).apply {
            hint = if (chat.blocked) "Unblock to message" else "Message…"; textSize = 15f; setTextColor(ink); setHintTextColor(muted)
            isEnabled = !chat.blocked
            setText(prefs.getString("draft_${chat.id}", ""))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    prefs.edit().putString("draft_${chat.id}", s?.toString().orEmpty().take(4000)).apply()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            setPadding(dp(12), dp(9), dp(12), dp(9)); background = shape(soft, 20); maxLines = 4
        }
        messageField = field
        compose.addView(field, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        compose.addView(button("➤", true) {
            val body = field.text.toString().trim()
            if (chat.blocked) { toast("Unblock this demo contact to message"); return@button }
            if (body.isNotBlank()) {
                val text = if (replyTo.isBlank()) body else "↳ $replyTo\n$body"
                chat.messages.add(DemoMessage(text, true, System.currentTimeMillis()))
                replyTo = ""; save(); field.text.clear()
                prefs.edit().remove("draft_${chat.id}").apply()
                openChat(chat.id)
            }
        })
        root.addView(compose)
    }
    private fun renderMessage(parent: LinearLayout, chat: DemoChat, message: DemoMessage) {
        val wrap = row().apply { gravity = if (message.mine) Gravity.RIGHT else Gravity.LEFT }
        val bubble = column(11).apply {
            background = shape(if (message.mine) teal else card, 15)
        }
        if (message.mediaUri.isNotBlank() && message.mediaType == "image") {
            try {
                val preview = ImageView(this).apply { setImageURI(Uri.parse(message.mediaUri)); scaleType = ImageView.ScaleType.CENTER_CROP }
                bubble.addView(preview, LinearLayout.LayoutParams(dp(175), dp(140)))
            } catch (_: Exception) { bubble.addView(label("Image preview unavailable", 12, if(message.mine) tealDark else muted)) }
        }
        if (message.mediaUri.isNotBlank() && message.mediaType == "voice") {
            bubble.addView(button("▶ Play voice note", false) { playVoice(message.mediaUri) })
        }
        if (message.mediaUri.isNotBlank() && message.mediaType == "document") {
            bubble.addView(button("📄 Open document", false) { openDocument(message.mediaUri) })
        }
        if (message.text.isNotBlank()) bubble.addView(label(message.text + (if (message.edited) "  (edited)" else ""), 14, if (message.mine) tealDark else ink))
        val foot = row()
        foot.addView(label(formatTime(message.time) + if (message.mine) "  ✓" else "", 10, if (message.mine) 0xFF245F53.toInt() else muted))
        if (message.starred) foot.addView(label("  ★", 15, if (message.mine) tealDark else teal))
        if (message.reaction.isNotBlank()) foot.addView(label("  ${message.reaction}", 15))
        bubble.addView(foot)
        wrap.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = if(message.mine) 0 else dp(58); leftMargin = if(message.mine) dp(58) else 0 })
        bubble.setOnLongClickListener {
            val actions = arrayOf("❤️ React", "😂 React", "👍 React", "↩ Reply", "☆ Star / unstar", "↪ Forward in demo", "Copy text", "Edit own message", "Delete for me", "Remove reaction")
            AlertDialog.Builder(this).setTitle("Message actions")
                .setItems(actions) { _, index ->
                    when (index) {
                        0, 1, 2 -> { message.reaction = listOf("❤️", "😂", "👍")[index]; save(); openChat(chat.id) }
                        3 -> { replyTo = message.text.take(55); messageField?.hint = "Replying to: ${replyTo.take(24)}"; messageField?.requestFocus() }
                        4 -> { message.starred = !message.starred; save(); openChat(chat.id) }
                        5 -> forwardMessage(chat, message)
                        6 -> { val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text)); toast("Copied") }
                        7 -> if (message.mine) editMessage(chat, message) else toast("You can only edit your own messages")
                        8 -> AlertDialog.Builder(this).setTitle("Delete from this phone?")
                            .setMessage("This does not delete a message on anyone else's phone.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> chat.messages.remove(message); save(); openChat(chat.id) }.show()
                        9 -> { message.reaction = ""; save(); openChat(chat.id) }
                    }
                }.show()
            true
        }
        parent.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun editMessage(chat: DemoChat, message: DemoMessage) {
        val field = EditText(this).apply { setText(message.text); setSelection(text.length); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        AlertDialog.Builder(this).setTitle("Edit message").setView(field)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val result = field.text.toString().trim()
                if (result.isNotEmpty()) { message.text = result.take(4000); message.edited = true; save(); openChat(chat.id) }
            }.show()
    }
    private fun forwardMessage(source: DemoChat, message: DemoMessage) {
        val targets = chats.filter { it.id != source.id && !it.blocked }
        if (targets.isEmpty()) { toast("Create another chat first"); return }
        AlertDialog.Builder(this).setTitle("Forward to demo chat")
            .setItems(targets.map { it.title }.toTypedArray()) { _, which ->
                val target = targets[which]
                target.messages.add(DemoMessage("↪ Forwarded\n${message.text}", true, System.currentTimeMillis()))
                save(); toast("Forwarded locally to ${target.title}")
            }.show()
    }
    private fun chooseAttachment(chatId: String) {
        pendingAttachmentChat = chatId
        AlertDialog.Builder(this).setTitle("Attach locally to this conversation")
            .setItems(arrayOf("🖼 Choose photo", "📄 Choose document", "🎙 Record voice note", "📍 Add sample location")) { _, which ->
                when(which) {
                    0, 1 -> try {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = if (which == 0) "image/*" else "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            if (which == 1) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain", "application/zip"))
                        }
                        startActivityForResult(intent, if (which == 0) PICK_IMAGE else PICK_DOCUMENT)
                    } catch (_: Exception) { toast("No file picker available") }
                    2 -> toggleRecording(chatId)
                    3 -> { chats.firstOrNull { it.id == chatId }?.messages?.add(DemoMessage("📍 Sample location · Local preview", true, System.currentTimeMillis())); save(); openChat(chatId) }
                }
            }.show()
    }
    private fun toggleRecording(chatId: String) {
        if (recordingChat != null) { stopRecording(true); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAttachmentChat = chatId
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        startRecording(chatId)
    }
    @Suppress("DEPRECATION")
    private fun startRecording(chatId: String) {
        try {
            val file = File(filesDir, "voice_${UUID.randomUUID()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare(); rec.start()
            voiceFile = file; recorder = rec; recordingChat = chatId
            AlertDialog.Builder(this).setTitle("Recording voice note")
                .setMessage("Recording from this phone's microphone. Audio stays on this device.")
                .setPositiveButton("Stop and save") { _, _ -> stopRecording(true) }
                .setNegativeButton("Discard") { _, _ -> stopRecording(false) }
                .setOnCancelListener { stopRecording(false) }
                .show()
        } catch (e: Exception) { stopRecording(false); toast("Microphone unavailable: ${e.message}") }
    }
    private fun stopRecording(saveNote: Boolean) {
        val chatId = recordingChat
        val file = voiceFile
        try { recorder?.stop() } catch (_: Exception) { /* Too short or no input */ }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null; recordingChat = null; voiceFile = null
        if (saveNote && chatId != null && file != null && file.exists() && file.length() > 300) {
            chats.firstOrNull { it.id == chatId }?.messages?.add(DemoMessage("Voice note", true, System.currentTimeMillis(), mediaUri = Uri.fromFile(file).toString(), mediaType = "voice"))
            save(); openChat(chatId)
        } else { file?.delete(); if (saveNote) toast("Recording was too short") }
    }
    private fun playVoice(uri: String) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@DemoActivity, Uri.parse(uri))
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare(); start()
            }
        } catch (_: Exception) { toast("Voice note unavailable") }
    }
    private fun openDocument(uri: String) {
        try {
            val parsed = Uri.parse(uri)
            val mime = contentResolver.getType(parsed) ?: "*/*"
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(parsed, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) { toast("No compatible document viewer installed") }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingAttachmentChat?.let { startRecording(it) }
        } else if (requestCode == REQUEST_MIC) toast("Microphone permission is needed to record voice notes")
    }
    @Deprecated("Activity result is sufficient for this small Android preview")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == PICK_IMAGE || requestCode == PICK_DOCUMENT) && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val chat = chats.firstOrNull { it.id == pendingAttachmentChat } ?: return
            val isImage = requestCode == PICK_IMAGE
            val type = if (isImage) "image" else "document"
            val caption = if (isImage) "Photo" else "Document"
            chat.messages.add(DemoMessage(caption, true, System.currentTimeMillis(), mediaUri = uri.toString(), mediaType = type))
            save(); openChat(chat.id)
        }
    }
    private fun chatOptions(chat: DemoChat) {
        val options = arrayOf("Contact / group info", "Edit chat title", if(chat.pinned) "Unpin" else "Pin", if(chat.archived) "Unarchive" else "Archive", if(chat.muted) "Unmute" else "Mute", if (chat.blocked) "Unblock" else "Block locally", "★ Starred messages", "↥ Export chat text", "Clear conversation", "Voice & video calls (not connected)")
        AlertDialog.Builder(this).setTitle(chat.title).setItems(options) { _, index ->
            when(index) {
                0 -> showChatInfo(chat)
                1 -> editChatTitle(chat)
                2 -> { chat.pinned = !chat.pinned; save(); openChat(chat.id) }
                3 -> { chat.archived = !chat.archived; save(); showHome("Chats") }
                4 -> { chat.muted = !chat.muted; save(); openChat(chat.id) }
                5 -> { chat.blocked = !chat.blocked; save(); openChat(chat.id) }
                6 -> AlertDialog.Builder(this).setTitle("Starred messages")
                    .setMessage(chat.messages.filter { it.starred }.joinToString("\n\n") { it.text }.ifBlank { "No starred messages yet. Long press any message to star it." })
                    .setPositiveButton("Close", null).show()
                7 -> shareChat(chat)
                8 -> AlertDialog.Builder(this).setTitle("Clear locally?")
                    .setMessage("Delete every message in this conversation on this phone?")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ -> chat.messages.clear(); save(); openChat(chat.id) }.show()
                else -> noticePlanned("Voice/video calls")
            }
        }.show()
    }
    private fun showChatInfo(chat: DemoChat) {
        AlertDialog.Builder(this).setTitle("${chat.title} · details")
            .setMessage("${if(chat.group) "Local group" else "Local contact"}\nMessages: ${chat.messages.size}\n${if(chat.group) "Members: " + chat.members.ifEmpty { "You (demo)" } else "No online account connected"}\n\nMuted: ${chat.muted}\nArchived: ${chat.archived}\nBlocked: ${chat.blocked}")
            .setNeutralButton(if (chat.group) "Edit members" else "Close") { _, _ -> if (chat.group) {
                val entry = EditText(this).apply { setText(chat.members); hint = "Names separated by commas" }
                AlertDialog.Builder(this).setTitle("Demo group members").setView(entry)
                    .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> chat.members = entry.text.toString().take(400); save() }.show()
            } }.setPositiveButton("Done", null).show()
    }
    private fun editChatTitle(chat: DemoChat) {
        val field = EditText(this).apply { setText(chat.title); setSelection(text.length) }
        AlertDialog.Builder(this).setTitle("Rename local conversation").setView(field)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val name = field.text.toString().trim()
                if(name.length >= 2) { chat.title = name.take(40); save(); openChat(chat.id) }
            }.show()
    }
    private fun shareChat(chat: DemoChat) {
        val text = buildString {
            appendLine("Near-Chat local conversation with ${chat.title}")
            for (message in chat.messages) appendLine("[${formatTime(message.time)}] ${if(message.mine) displayName else chat.title}: ${message.text}")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "Export conversation text"))
    }
    private fun createChat(group: Boolean) {
        val field = EditText(this).apply { hint = if(group) "New group name" else "Friend's display name"; setSingleLine(true); setPadding(dp(18), dp(18), dp(18), dp(18)) }
        AlertDialog.Builder(this).setTitle(if (group) "Create local demo group" else "Create local demo chat")
            .setView(field).setNegativeButton("Cancel", null).setPositiveButton("Create") { _, _ ->
                val title = field.text.toString().trim()
                if (title.length < 2) { toast("Enter a name with at least 2 characters"); return@setPositiveButton }
                val chat = DemoChat(UUID.randomUUID().toString(), title.take(40), group)
                chat.messages.add(DemoMessage(if(group) "Group created in local demo. Invite features need a backend." else "Demo contact added. Messages will stay on this device.", false, System.currentTimeMillis()))
                chats.add(0, chat); save(); openChat(chat.id)
            }.show()
    }

    private fun showUpdates(parent: LinearLayout) {
        blockTitle(parent, "Status updates", "A visual preview of WhatsApp-style stories")
        val own = panel(parent)
        val r = row()
        r.addView(avatar("+", tealDark), LinearLayout.LayoutParams(dp(51), dp(51)))
        val c = column().apply { setPadding(dp(13), 0, 0, 0) }
        c.addView(label("My status", 17, ink, true)); c.addView(label(about.take(65), 12, muted))
        r.addView(c, LinearLayout.LayoutParams(0, -2, 1f)); own.addView(r)
        own.addView(button("＋ Post local 24-hour status", true) { createLocalStatus() })
        for ((time, text) in statuses.sortedByDescending { it.first }) {
            own.addView(label("● ${formatTime(time)} · $text", 13, ink).apply { setPadding(0, dp(9), 0, 0) })
        }
        own.addView(label("Only this phone can see these posts. They expire after 24 hours.", 11, muted))
        blockTitle(parent, "Recent updates")
        for ((name, caption) in listOf("Sarah Khan" to "New week, new possibilities ✨", "Daniel" to "Coffee and good conversations ☕", "Aisha" to "Weekend adventures 🌿")) {
            val item = panel(parent)
            val line = row()
            line.addView(avatar(name, colorFor(name)), LinearLayout.LayoutParams(dp(47), dp(47)))
            val texts = column().apply { setPadding(dp(12), 0, 0, 0) }
            texts.addView(label(name, 16, ink, true)); texts.addView(label("Tap to preview", 12, muted))
            line.addView(texts)
            item.addView(line)
            item.setOnClickListener {
                AlertDialog.Builder(this).setTitle("✦ $name's sample status").setMessage(caption + "\n\nPreview only • Not a real shared status")
                    .setPositiveButton("Close", null).show()
            }
        }
    }
    private fun createLocalStatus() {
        val field = EditText(this).apply { hint = "What's new?"; minLines = 2; maxLines = 4 }
        AlertDialog.Builder(this).setTitle("Post status on this phone").setView(field)
            .setNegativeButton("Cancel", null).setPositiveButton("Post") { _, _ ->
                val text = field.text.toString().trim()
                if (text.isNotEmpty()) { statuses.add(System.currentTimeMillis() to text.take(280)); save(); showHome("Updates") }
            }.show()
    }
    private fun showCalls(parent: LinearLayout) {
        blockTitle(parent, "Calls", "Explore the interface. Calling needs real-time online services.")
        val top = panel(parent)
        top.addView(label("☎  Voice & video", 18, ink, true))
        top.addView(label("Call buttons are preview-only here. They cannot reach a phone without signaling infrastructure and permissions.", 13, muted))
        top.addView(button("How real calls will work →", true) { noticePlanned("WebRTC voice/video calls and signaling") })
        blockTitle(parent, "Sample call history")
        for ((name, time, symbol) in listOf(Triple("Sarah Khan", "Today, 10:42 AM", "↗"), Triple("Daniel", "Yesterday, 8:20 PM", "↙"), Triple("Family Group", "Thursday, 7:15 PM", "↗"))) {
            val item = panel(parent)
            val line = row()
            line.addView(avatar(name, colorFor(name)), LinearLayout.LayoutParams(dp(48), dp(48)))
            val details = column().apply { setPadding(dp(13), 0, 0, 0) }
            details.addView(label(name, 16, ink, true)); details.addView(label("$symbol  $time", 12, muted))
            line.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label("☎", 23, teal))
            item.addView(line)
            item.setOnClickListener { noticePlanned("Voice/video calls") }
        }
    }
    private fun showProfile(parent: LinearLayout) {
        blockTitle(parent, "Your profile", "Local preview settings")
        val p = panel(parent)
        val r = row()
        r.addView(avatar(displayName, colorFor(displayName)), LinearLayout.LayoutParams(dp(60), dp(60)))
        val text = column().apply { setPadding(dp(14), 0, 0, 0) }
        text.addView(label(displayName, 19, ink, true)); text.addView(label(about, 12, muted))
        r.addView(text, LinearLayout.LayoutParams(0, -2, 1f)); p.addView(r)
        p.addView(button("Edit profile", true) { editProfile() })
        blockTitle(parent, "App settings")
        val settings = panel(parent)
        settingRow(settings, "☾", "Appearance", if(dark) "Dark mode" else "Light mode") { dark = !dark; save(); showHome("You") }
        settingRow(settings, "♧", "Notifications", if(notifications) "On (preview setting)" else "Off") { notifications = !notifications; save(); showHome("You") }
        settingRow(settings, "♢", "Privacy & security", "Learn what this demo stores") { AlertDialog.Builder(this).setTitle("Privacy in demo mode").setMessage("Everything shown here is stored locally on this phone. No friend receives your messages. Sample profiles and call history are fictional. This demo has no end-to-end encrypted network messaging.").setPositiveButton("OK", null).show() }
        settingRow(settings, "⚙", "Accounts & online mode", "Connect Firebase for actual users") { showDemoInfo() }
        settingRow(settings, "★", "Starred messages", "Saved across your local chats") {
            val starred = chats.flatMap { c -> c.messages.filter { it.starred }.map { "${c.title}: ${it.text}" } }
            AlertDialog.Builder(this).setTitle("Starred messages")
                .setMessage(starred.joinToString("\n\n").ifBlank { "Long press a message and tap Star." })
                .setPositiveButton("Close", null).show()
        }
        blockTitle(parent, "More")
        val more = panel(parent)
        settingRow(more, "✦", "Plans & features", "What's included now and what's planned") { showPlans() }
        settingRow(more, "?", "About Near-Chat", "Version 0.6 · Local feature preview") { showDemoInfo() }
        settingRow(more, "↻", "Reset sample conversations", "Restore the original demo data") {
            AlertDialog.Builder(this).setTitle("Reset demo?").setMessage("This deletes your locally added demo messages and chats.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset") { _, _ -> chats.clear(); seed(); showHome("Chats") }.show()
        }
    }
    private fun settingRow(parent: LinearLayout, icon: String, title: String, detail: String, action: () -> Unit) {
        val item = row().apply { setPadding(0, dp(11), 0, dp(11)) }
        item.addView(label(icon, 24, teal).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(37), dp(42)))
        val text = column().apply { setPadding(dp(8), 0, 0, 0) }
        text.addView(label(title, 15, ink, true)); text.addView(label(detail, 12, muted))
        item.addView(text, LinearLayout.LayoutParams(0, -2, 1f)); item.addView(label("›", 25, muted))
        parent.addView(item); item.setOnClickListener { action() }
    }
    private fun editAbout() {
        val field = EditText(this).apply { setText(about); setSelection(text.length); setPadding(dp(15), dp(12), dp(15), dp(12)) }
        AlertDialog.Builder(this).setTitle("Edit your status").setView(field)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> about = field.text.toString().take(100); save(); showHome(tab) }.show()
    }
    private fun editProfile() {
        val fields = column(12)
        val name = EditText(this).apply { setText(displayName); hint="Display name"; setSingleLine(true) }
        val status = EditText(this).apply { setText(about); hint="Your status"; setSingleLine(true) }
        fields.addView(label("Name", 13, muted)); fields.addView(name)
        fields.addView(label("About", 13, muted)); fields.addView(status)
        AlertDialog.Builder(this).setTitle("Edit demo profile").setView(fields)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val proposed = name.text.toString().trim()
                if (proposed.length >= 2) { displayName = proposed.take(32); about = status.text.toString().take(100); save(); showHome("You") }
                else toast("Name must be at least 2 characters")
            }.show()
    }
    private fun showPlans() {
        AlertDialog.Builder(this).setTitle("Near-Chat feature plan")
            .setMessage("AVAILABLE LOCALLY\n• Conversations, search, drafts\n• Replies, edit, forward, star, delete, reactions\n• Photo and document attachments\n• Record and play voice notes\n• Group names and members\n• Block, mute, pin, archive, export\n• 24-hour local text status\n• Profile, dark/light, settings\n\nNEEDS ONLINE SERVICES\n• Cross-device chat and sync\n• Phone/email login, contact search\n• Shared group/status updates\n• Push notifications and real voice/video calls\n• End-to-end encryption/security audit\n\nNo paid plan or subscription is active.")
            .setPositiveButton("Got it", null).show()
    }
    private fun showDemoInfo() {
        AlertDialog.Builder(this).setTitle("Preview works offline")
            .setMessage("You can try all preview screens without Firebase. Messages, reactions, and settings remain on this device only.\n\nFor real messaging between people, configure your Firebase project and rebuild the online APK. See SETUP_FIREBASE.md in the GitHub repository.\n\nVoice/video calls and full social features still need development.")
            .setPositiveButton("OK", null).show()
    }
    private fun noticePlanned(feature: String) { AlertDialog.Builder(this).setTitle("$feature — coming later").setMessage("This feature is planned for Near-Chat Online. It is not active in the local demo, and the app won't pretend to connect or call other people.").setPositiveButton("OK", null).show() }
    override fun onDestroy() {
        if (recorder != null) stopRecording(false)
        player?.release(); player = null
        super.onDestroy()
    }
    override fun onBackPressed() {
        if (activeChat != null) showHome("Chats") else if(tab != "Chats") showHome("Chats") else super.onBackPressed()
    }
    companion object {
        private const val PICK_IMAGE = 921
        private const val PICK_DOCUMENT = 922
        private const val REQUEST_MIC = 923
    }
}
