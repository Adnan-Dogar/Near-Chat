package com.example.neartalk

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
        val text: String,
        val mine: Boolean,
        val time: Long,
        var reaction: String = "",
        val mediaUri: String = "",
        val mediaType: String = ""
    )
    private data class DemoChat(
        val id: String,
        var title: String,
        val group: Boolean,
        var pinned: Boolean = false,
        var unread: Int = 0,
        val messages: MutableList<DemoMessage> = mutableListOf()
    )
    private val chats = mutableListOf<DemoChat>()
    private var activeChat: String? = null
    private var tab = "Chats"
    private var displayName = "Alex Morgan"
    private var about = "Hey there! I'm using Near-Chat."
    private var dark = true
    private var notifications = true
    private var replyTo: String = ""
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
                val chat = DemoChat(obj.getString("id"), obj.getString("title"), obj.optBoolean("group"), obj.optBoolean("pinned"), obj.optInt("unread"))
                val entries = obj.optJSONArray("messages") ?: JSONArray()
                for (m in 0 until entries.length()) {
                    val msg = entries.getJSONObject(m)
                    chat.messages.add(DemoMessage(msg.optString("text"), msg.optBoolean("mine"), msg.optLong("time"), msg.optString("reaction"), msg.optString("mediaUri"), msg.optString("mediaType")))
                }
                chats.add(chat)
            }
        } catch (_: Exception) { chats.clear() }
        if (chats.isEmpty()) seed()
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
            val obj = JSONObject().put("id", chat.id).put("title", chat.title).put("group", chat.group).put("pinned", chat.pinned).put("unread", chat.unread)
            val messages = JSONArray()
            for (m in chat.messages) messages.put(JSONObject().put("text", m.text).put("mine", m.mine).put("time", m.time).put("reaction", m.reaction).put("mediaUri", m.mediaUri).put("mediaType", m.mediaType))
            obj.put("messages", messages)
            arr.put(obj)
        }
        prefs.edit().putString("chats", arr.toString()).putString("name", displayName).putString("about", about).putBoolean("dark", dark).putBoolean("notifications", notifications).apply()
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
        welcome.addView(label("Your space to connect", 21, ink, true))
        welcome.addView(label("Explore chats, reactions, status and settings. This preview works without internet or Firebase.", 13, muted))
        welcome.addView(button("See online setup →", false) { showDemoInfo() }.apply { setPadding(dp(8), dp(11), dp(8), dp(11)) })
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
        blockTitle(inside, "Messages", "Tap a conversation to open the interactive preview")
        val list = column()
        inside.addView(list)
        fun refresh(query: String) {
            list.removeAllViews()
            val filtered = chats.filter { chat ->
                query.isBlank() || chat.title.contains(query, true) || chat.messages.any { it.text.contains(query, true) }
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
        nameRow.addView(label(chat.title + (if (chat.pinned) "  📌" else ""), 15, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
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
            val options = arrayOf(if (chat.pinned) "Unpin" else "Pin", "Mark as read", "Delete demo conversation")
            AlertDialog.Builder(this).setTitle(chat.title).setItems(options) { _, index ->
                when(index) {
                    0 -> chat.pinned = !chat.pinned
                    1 -> chat.unread = 0
                    2 -> chats.remove(chat)
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
        val field = EditText(this).apply {
            hint = "Message…"; textSize = 15f; setTextColor(ink); setHintTextColor(muted)
            setPadding(dp(12), dp(9), dp(12), dp(9)); background = shape(soft, 20); maxLines = 4
        }
        messageField = field
        compose.addView(field, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        compose.addView(button("➤", true) {
            val body = field.text.toString().trim()
            if (body.isNotBlank()) {
                val text = if (replyTo.isBlank()) body else "↳ $replyTo\n$body"
                chat.messages.add(DemoMessage(text, true, System.currentTimeMillis()))
                replyTo = ""; save(); field.text.clear()
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
        if (message.text.isNotBlank()) bubble.addView(label(message.text, 14, if (message.mine) tealDark else ink))
        val foot = row()
        foot.addView(label(formatTime(message.time) + if (message.mine) "  ✓" else "", 10, if (message.mine) 0xFF245F53.toInt() else muted))
        if (message.reaction.isNotBlank()) foot.addView(label("  ${message.reaction}", 15))
        bubble.addView(foot)
        wrap.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = if(message.mine) 0 else dp(58); leftMargin = if(message.mine) dp(58) else 0 })
        bubble.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle("Message actions")
                .setItems(arrayOf("❤️ React", "😂 React", "👍 React", "↩ Reply", "Copy text", "Remove reaction")) { _, index ->
                    when(index) {
                        0, 1, 2 -> { message.reaction = listOf("❤️", "😂", "👍")[index]; save(); openChat(chat.id) }
                        3 -> { replyTo = message.text.take(55); messageField?.hint = "Replying to: ${replyTo.take(28)}"; messageField?.requestFocus() }
                        4 -> { val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("demo message", message.text)); toast("Copied") }
                        5 -> { message.reaction = ""; save(); openChat(chat.id) }
                    }
                }.show(); true
        }
        parent.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun chooseAttachment(chatId: String) {
        AlertDialog.Builder(this).setTitle("Attach to this demo chat")
            .setItems(arrayOf("Pick an image from this phone", "Add a sample location card", "Voice note (planned)", "Document sharing (planned)")) { _, which ->
                when(which) {
                    0 -> try {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                        startActivityForResult(intent, PICK_IMAGE)
                    } catch (_: Exception) { toast("No image picker available") }
                    1 -> { chats.firstOrNull { it.id == chatId }?.messages?.add(DemoMessage("📍 Sample location (preview only)", true, System.currentTimeMillis())); save(); openChat(chatId) }
                    else -> noticePlanned("Media sharing and voice notes")
                }
            }.show()
    }
    @Deprecated("Activity result is sufficient for this small Android preview")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val chat = chats.firstOrNull { it.id == activeChat } ?: return
            chat.messages.add(DemoMessage("Photo preview", true, System.currentTimeMillis(), mediaUri = uri.toString(), mediaType = "image"))
            save(); openChat(chat.id)
        }
    }
    private fun chatOptions(chat: DemoChat) {
        AlertDialog.Builder(this).setTitle(chat.title).setItems(arrayOf("View contact/group", if(chat.pinned) "Unpin" else "Pin", "Clear this demo chat", "Voice call (planned)", "Video call (planned)")) { _, index ->
            when(index) {
                0 -> AlertDialog.Builder(this).setTitle(chat.title).setMessage(if(chat.group) "Preview group • ${chat.messages.size} local messages" else "Preview contact • No real account connected").setPositiveButton("OK", null).show()
                1 -> { chat.pinned = !chat.pinned; save(); toast("Updated") }
                2 -> AlertDialog.Builder(this).setMessage("Delete the messages saved for this demo conversation?").setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ -> chat.messages.clear(); save(); openChat(chat.id) }.show()
                else -> noticePlanned("Voice/video calling")
            }
        }.show()
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
        own.addView(button("Update my text status", true) { editAbout() })
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
        blockTitle(parent, "More")
        val more = panel(parent)
        settingRow(more, "✦", "Plans & features", "What's included now and what's planned") { showPlans() }
        settingRow(more, "?", "About Near-Chat", "Version 0.4 · Interactive preview") { showDemoInfo() }
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
            .setMessage("AVAILABLE IN THIS FREE DEMO\n• Chat list & search\n• Local messages & reactions\n• Local groups & photo preview\n• Status updates preview\n• Profile & appearance settings\n\nREQUIRES AN ONLINE BACKEND\n• Real accounts & messaging\n• Friends across countries\n• Shared groups & status\n• Push notifications\n• Live voice/video calls\n\nNo paid plan, purchases, or subscriptions are active.")
            .setPositiveButton("Got it", null).show()
    }
    private fun showDemoInfo() {
        AlertDialog.Builder(this).setTitle("Preview works offline")
            .setMessage("You can try all preview screens without Firebase. Messages, reactions, and settings remain on this device only.\n\nFor real messaging between people, configure your Firebase project and rebuild the online APK. See SETUP_FIREBASE.md in the GitHub repository.\n\nVoice/video calls and full social features still need development.")
            .setPositiveButton("OK", null).show()
    }
    private fun noticePlanned(feature: String) { AlertDialog.Builder(this).setTitle("$feature — coming later").setMessage("This feature is planned for Near-Chat Online. It is not active in the local demo, and the app won't pretend to connect or call other people.").setPositiveButton("OK", null).show() }
    override fun onBackPressed() {
        if (activeChat != null) showHome("Chats") else if(tab != "Chats") showHome("Chats") else super.onBackPressed()
    }
    companion object { private const val PICK_IMAGE = 921 }
}
