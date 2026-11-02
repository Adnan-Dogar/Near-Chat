package com.nearchat.selfhost

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import java.net.URLEncoder
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real self-hosted REST + WebSocket chat alpha v0.8. No Firebase or unencrypted HTTP. */
class MainActivity : Activity() {
    private val navy = Color.rgb(12, 22, 37)
    private val cardColor = Color.rgb(28, 44, 63)
    private val mint = Color.rgb(77, 227, 180)
    private val soft = Color.rgb(168, 189, 207)
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    private lateinit var session: SecureSession
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private var rootScroll: ScrollView? = null
    private var base = ""
    private var token = ""
    private var selfId = ""
    private var username = ""
    private var chatId: String? = null
    private var chatTitle: String = ""
    private var screenName = "login"
    private var messagesList: LinearLayout? = null
    private var chatsList: LinearLayout? = null
    private var socket: WebSocket? = null
    private var closed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        session = SecureSession(this)
        base = session.url(); token = session.token() ?: ""; username = session.username()
        if (base.isNotEmpty() && token.isNotEmpty()) {
            api("GET", "/v1/me", null, { user ->
                selfId = user.getString("id")
                username = user.getString("username")
                showChats(); openSocket()
            }, { showLogin() })
        } else showLogin()
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density + .5f).toInt()
    private fun bg(color: Int, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun txt(value: String, size: Float = 15f, color: Int = Color.WHITE): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(6), dp(10), dp(6), dp(10))
    }
    private fun screen(title: String): LinearLayout {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(navy) }
        val scroll = ScrollView(this).apply { setFillViewport(true); addView(layout) }
        rootScroll = scroll
        setContentView(scroll)
        content = layout
        val heading = txt(title, 25f, mint).apply { setPadding(dp(22), dp(24), dp(18), dp(12)) }
        layout.addView(heading)
        screenName = title
        return layout
    }
    private fun label(parent: LinearLayout, text: String, size: Float = 14f, color: Int = soft) {
        parent.addView(txt(text,size,color).apply { setPadding(dp(18),dp(9),dp(18),dp(9)) })
    }
    private fun input(parent: LinearLayout, hint: String, password: Boolean = false): EditText {
        val field = EditText(this).apply {
            this.hint = hint; setHintTextColor(soft); setTextColor(Color.WHITE)
            textSize = 16f; setSingleLine(true)
            inputType = if(password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            background = bg(cardColor)
            setPadding(dp(15),dp(12),dp(15),dp(12))
        }
        parent.addView(field, LinearLayout.LayoutParams(-1, dp(53)).apply { setMargins(dp(16),dp(5),dp(16),dp(8)) })
        return field
    }
    private fun button(parent: LinearLayout, title: String, secondary: Boolean = false, action: () -> Unit): Button {
        val btn = Button(this).apply {
            text = title; isAllCaps = false; textSize = 15f
            setTextColor(if(secondary) Color.WHITE else navy)
            background = bg(if(secondary) cardColor else mint)
            setOnClickListener { action() }
        }
        parent.addView(btn, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(16),dp(5),dp(16),dp(6)) })
        return btn
    }
    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun api(method: String, path: String, body: JSONObject?, success: (JSONObject) -> Unit, error: (String) -> Unit = { notice(it) }) {
        if (base.isEmpty()) { error("Enter your server address first"); return }
        val builder = Request.Builder().url(base + path)
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        val req = when (method) {
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(jsonType)).build()
            "PATCH" -> builder.patch((body ?: JSONObject()).toString().toRequestBody(jsonType)).build()
            "PUT" -> builder.put((body ?: JSONObject()).toString().toRequestBody(jsonType)).build()
            "DELETE" -> builder.delete().build()
            else -> builder.get().build()
        }
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { error("Connection failed: ${e.localizedMessage}") } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = try { JSONObject(raw).optString("detail", "Server error") } catch (_: Exception) { "Server error" }
                        runOnUiThread { error("${response.code}: $message") }
                    } else try {
                        val data = JSONObject(raw)
                        runOnUiThread { success(data) }
                    } catch (_: Exception) { runOnUiThread { error("Unexpected server response") } }
                }
            }
        })
    }
    private fun getArray(path: String, success: (JSONArray) -> Unit) {
        val req = Request.Builder().url(base + path).header("Authorization", "Bearer $token").get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { notice("Network error: ${e.localizedMessage}") } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = response.body?.string().orEmpty()
                    try {
                        if (!response.isSuccessful) { runOnUiThread { notice("Server returned ${response.code}") }; return }
                        val arr = JSONArray(raw)
                        runOnUiThread { success(arr) }
                    } catch (_: Exception) { runOnUiThread { notice("Invalid chat data received") } }
                }
            }
        })
    }

    private fun validServer(raw: String): String? {
        val uri = try { URI(raw.trim().trimEnd('/')) } catch (_: Exception) { return null }
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (uri.path != null && uri.path != "" && uri.path != "/") return null
        return "https://${uri.authority}"
    }
    private fun showLogin() {
        socket?.close(1000, "sign out"); socket = null; chatId = null
        val box = screen("Near-Chat · Online")
        label(box,"Connect to your own Near-Chat server. No Firebase account required.")
        val url = input(box,"https://chat.your-hostname.example").apply { setText(base) }
        val user = input(box,"Username (3–24 characters)").apply { setText(username) }
        val display = input(box,"Display name (for new accounts)")
        val pass = input(box,"Password (12+ characters)", true)
        label(box, "Your VPS must have a valid HTTPS certificate. Do not enter root or VPS passwords here.", 12f)
        fun submit(signup: Boolean) {
            val server = validServer(url.text.toString())
            if (server == null) { url.error = "Enter a valid HTTPS server root URL"; return }
            val name = user.text.toString().trim().lowercase()
            if (!Regex("^[a-z0-9_]{3,24}$").matches(name)) { user.error = "Use 3–24 letters, digits or _"; return }
            if (pass.text.length < 12) { pass.error = "Use at least 12 characters"; return }
            if (signup && display.text.toString().trim().length !in 2..40) { display.error = "Use 2–40 characters"; return }
            base = server
            val payload = JSONObject().put("username",name).put("password",pass.text.toString())
            if (signup) payload.put("display_name",display.text.toString().trim())
            api("POST", if(signup) "/v1/auth/register" else "/v1/auth/login", payload, { result ->
                token = result.getString("token")
                val profile = result.getJSONObject("user")
                selfId = profile.getString("id"); username = profile.getString("username")
                session.save(base,username,token)
                pass.text.clear()
                showChats(); openSocket()
            })
        }
        button(box,"Log in") { submit(false) }
        button(box,"Create new account", true) { submit(true) }
    }

    private fun showChats() {
        chatId=null
        val box=screen("Near-Chat")
        label(box, "Signed in as @$username", 15f, mint)
        button(box,"Start chat by username") { promptDirect() }
        button(box,"Create group",true) { promptGroup() }
        button(box,"Refresh chats",true) { refreshChats() }
        button(box,"Edit my profile",true) { editProfile() }
        button(box,"Safety: block / unblock / report",true) { safetyOptions() }
        button(box,"Copy my username",true) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Near-Chat username",username))
            notice("Username copied")
        }
        button(box,"Sign out",true) { signOut() }
        label(box,"CONVERSATIONS",13f,mint)
        chatsList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        box.addView(chatsList)
        refreshChats()
    }
    private fun refreshChats() {
        if (chatId != null || screenName != "Near-Chat") return
        getArray("/v1/chats") { chats ->
            val view=chatsList ?: return@getArray
            view.removeAllViews()
            if(chats.length()==0) label(view,"No chats yet. Start one using your friend's username.")
            for(i in 0 until chats.length()) {
                val c=chats.getJSONObject(i)
                val id=c.getString("id"); val title=c.optString("title","Chat")
                val last=c.optString("last_message","")
                button(view, (if(c.optBoolean("pinned")) "📌 " else "")+(if(c.optBoolean("muted")) "🔕 " else "")+
                    (if(c.optString("kind")=="group") "👥 " else "💬 ")+title+(if(last.isNotEmpty()) "\n"+last.take(40) else ""),true) {
                    openChat(id,title)
                }
            }
        }
    }
    private fun promptDirect() {
        val field=EditText(this).apply { hint="Friend's username"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("New chat").setView(field)
            .setPositiveButton("Start") { _, _ ->
                val name=field.text.toString().trim().lowercase()
                api("POST","/v1/chats/direct",JSONObject().put("username",name),{ data ->
                    openChat(data.getString("id"),name)
                })
            }.setNegativeButton("Cancel",null).show()
    }
    private fun promptGroup() {
        val wrapper=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }
        val title=EditText(this).apply { hint="Group name" }
        val usernames=EditText(this).apply { hint="Friends' usernames separated by commas" }
        wrapper.addView(title); wrapper.addView(usernames)
        AlertDialog.Builder(this).setTitle("Create group").setView(wrapper)
            .setPositiveButton("Create") { _, _ ->
                val array=JSONArray()
                usernames.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { array.put(it) }
                api("POST","/v1/chats/group",JSONObject().put("title",title.text.toString().trim()).put("usernames",array),{
                    openChat(it.getString("id"),title.text.toString().trim())
                })
            }.setNegativeButton("Cancel",null).show()
    }
    private fun openChat(id: String,title: String) {
        chatId=id;chatTitle=title
        val box=screen(title)
        button(box,"← All conversations",true) { showChats() }
        button(box,"Chat options: mute / pin",true) { chatOptions(id) }
        button(box,"Search conversation",true) { promptSearch(id) }
        val scroll=ScrollView(this)
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list)
        box.addView(scroll,LinearLayout.LayoutParams(-1,dp(430)))
        messagesList=list
        val composer=input(box,"Write a message...")
        button(box,"Send message") {
            val body=composer.text.toString().trim()
            if(body.isEmpty()) return@button
            api("POST","/v1/chats/$id/messages",JSONObject().put("body",body).put("client_id",UUID.randomUUID().toString()),{
                composer.text.clear(); refreshMessages()
            })
        }
        button(box,"Refresh messages",true) { refreshMessages() }
        refreshMessages()
    }
    private fun refreshMessages() {
        val id=chatId ?: return
        getArray("/v1/chats/$id/messages") { items ->
            if(chatId!=id) return@getArray
            val list=messagesList ?: return@getArray
            list.removeAllViews()
            for(i in 0 until items.length()) {
                val msg=items.getJSONObject(i)
                val mine=msg.getString("sender_id")==selfId
                val reactions = msg.optJSONArray("reactions")
                val emojis = (0 until (reactions?.length() ?: 0)).joinToString(" ") { index ->
                    reactions?.optJSONObject(index)?.optString("emoji") ?: ""
                }
                val edited = if(msg.optString("edited_at") !in listOf("", "null")) " · edited" else ""
                val label=txt((if(mine) "You" else msg.getString("sender_id").take(8))+edited+"\n"+
                    msg.optString("body")+(if(emojis.isNotBlank()) "\n$emojis" else ""),14f,
                    if(mine) navy else Color.WHITE).apply {
                        background=bg(if(mine) mint else cardColor)
                        setPadding(dp(14),dp(12),dp(14),dp(12))
                        maxWidth=(resources.displayMetrics.widthPixels*0.80f).toInt()
                    }
                val wrap=LinearLayout(this).apply { gravity=if(mine) Gravity.RIGHT else Gravity.LEFT }
                label.setOnLongClickListener {
                    messageActions(id, msg, mine)
                    true
                }
                wrap.addView(label)
                list.addView(wrap, LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(14),dp(6),dp(14),dp(6)) })
            }
            rootScroll?.post { rootScroll?.fullScroll(View.FOCUS_DOWN) }
        }
    }
    private fun editProfile() {
        api("GET", "/v1/me", null, { current ->
            val frame=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),0,dp(18),0) }
            val display=EditText(this).apply { hint="Display name"; setText(current.optString("display_name")); filters=arrayOf(android.text.InputFilter.LengthFilter(40)) }
            val bio=EditText(this).apply { hint="Status / bio (160 characters max)"; setText(current.optString("bio")); filters=arrayOf(android.text.InputFilter.LengthFilter(160)) }
            frame.addView(display); frame.addView(bio)
            AlertDialog.Builder(this).setTitle("Edit profile").setView(frame)
                .setPositiveButton("Save") { _, _ ->
                    val name=display.text.toString().trim()
                    if(name.length !in 2..40) { notice("Display name must be 2–40 characters"); return@setPositiveButton }
                    api("PATCH","/v1/me",JSONObject().put("display_name",name).put("bio",bio.text.toString().trim()),{
                        notice("Profile updated"); showChats()
                    })
                }.setNegativeButton("Cancel",null).show()
        })
    }

    private fun safetyOptions() {
        val field=EditText(this).apply { hint="Enter exact username"; setSingleLine(true); setPadding(dp(20),dp(6),dp(20),dp(6)) }
        AlertDialog.Builder(this).setTitle("Safety tools").setMessage("Choose a user. Blocking prevents direct messages. Reporting sends your reason to the server administrator for review.")
            .setView(field).setPositiveButton("Next") { _, _ ->
                val name=field.text.toString().trim().lowercase()
                if(!Regex("^[a-z0-9_]{3,24}$").matches(name) || name==username) { notice("Enter a different valid username"); return@setPositiveButton }
                AlertDialog.Builder(this).setTitle("@$name").setItems(arrayOf("Block user","Unblock user","Report user")) { _,choice ->
                    when(choice) {
                        0 -> api("POST","/v1/blocks/$name",JSONObject(),{ notice("User blocked") })
                        1 -> api("DELETE","/v1/blocks/$name",null,{ notice("User unblocked") })
                        2 -> {
                            val reason=EditText(this).apply { hint="Reason (at least 10 characters)"; setPadding(dp(16),dp(4),dp(16),dp(4)) }
                            AlertDialog.Builder(this).setTitle("Report @$name").setView(reason)
                                .setPositiveButton("Submit") { _, _ ->
                                    if(reason.text.toString().trim().length<10) { notice("Please give a reason of 10+ characters"); return@setPositiveButton }
                                    api("POST","/v1/reports",JSONObject().put("username",name).put("reason",reason.text.toString().trim()),{ notice("Report received") })
                                }.setNegativeButton("Cancel",null).show()
                        }
                    }
                }.show()
            }.setNegativeButton("Cancel",null).show()
    }

    private fun chatOptions(id: String) {
        // Server stores the flags for each authenticated member independently.
        getArray("/v1/chats") { chats ->
            val chat=(0 until chats.length()).map { chats.getJSONObject(it) }.firstOrNull { it.optString("id")==id }
            if(chat==null) { notice("Chat not available"); return@getArray }
            var muted=chat.optBoolean("muted")
            var pinned=chat.optBoolean("pinned")
            AlertDialog.Builder(this).setTitle("Conversation options")
                .setMultiChoiceItems(arrayOf("Mute this chat","Pin to top"),booleanArrayOf(muted,pinned)) { _,pos,enabled ->
                    if(pos==0) muted=enabled else pinned=enabled
                }.setPositiveButton("Save") { _, _ ->
                    api("PUT","/v1/chats/$id/options",JSONObject().put("muted",muted).put("pinned",pinned),{ notice("Chat preferences saved") })
                }.setNegativeButton("Cancel",null).show()
        }
    }

    private fun promptSearch(id: String) {
        val term=EditText(this).apply { hint="Search messages"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Search conversation").setView(term)
            .setPositiveButton("Search") { _, _ ->
                val value=term.text.toString().trim()
                if(value.length !in 2..80) { notice("Enter 2–80 characters"); return@setPositiveButton }
                val q=URLEncoder.encode(value,"UTF-8")
                getArray("/v1/chats/$id/search?term=$q") { found ->
                    val results=(0 until found.length()).map { found.optJSONObject(it)?.optString("body") ?: "" }
                    AlertDialog.Builder(this).setTitle("${results.size} search results")
                        .setItems(results.take(50).toTypedArray()) { _, _ -> refreshMessages() }
                        .setPositiveButton("Close",null).show()
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun messageActions(chat: String, message: JSONObject, mine: Boolean) {
        val id=message.optString("id")
        if(id.isEmpty() || !message.isNull("deleted_at")) return
        val options=if(mine) arrayOf("React 👍","React ❤️","React 😂","Remove reaction","Edit message","Delete message")
                    else arrayOf("React 👍","React ❤️","React 😂","Remove reaction")
        AlertDialog.Builder(this).setTitle("Message options")
            .setItems(options) { _,selected ->
                if(selected<=3) {
                    val emoji=when(selected) { 0->"👍";1->"❤️";2->"😂";else->"" }
                    api("PUT","/v1/chats/$chat/messages/$id/reaction",JSONObject().put("emoji",emoji),{ refreshMessages() })
                } else if(selected==4 && mine) {
                    val edit=EditText(this).apply { setText(message.optString("body")); filters=arrayOf(android.text.InputFilter.LengthFilter(4000)) }
                    AlertDialog.Builder(this).setTitle("Edit your message (within 15 minutes)").setView(edit)
                        .setPositiveButton("Save") { _, _ ->
                            val body=edit.text.toString().trim()
                            if(body.isBlank()) { notice("Message cannot be empty"); return@setPositiveButton }
                            api("PATCH","/v1/chats/$chat/messages/$id",JSONObject().put("body",body).put("client_id",UUID.randomUUID().toString()),{ refreshMessages() })
                        }.setNegativeButton("Cancel",null).show()
                } else if(selected==5 && mine) {
                    AlertDialog.Builder(this).setTitle("Delete your message?")
                        .setMessage("This will replace the message with a deletion notice for all members.")
                        .setPositiveButton("Delete") { _, _ ->
                            api("DELETE","/v1/chats/$chat/messages/$id",null,{ refreshMessages() })
                        }.setNegativeButton("Cancel",null).show()
                }
            }.show()
    }

    private fun openSocket() {
        socket?.close(1000,"reconnect")
        if (base.isEmpty() || token.isEmpty() || closed) return
        val wsUrl=base.replaceFirst("https://","wss://")+"/v1/ws"
        socket=client.newWebSocket(Request.Builder().url(wsUrl).build(), object: WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { ws.send(JSONObject().put("token",token).toString()) }
            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val event=JSONObject(text)
                    if(event.optString("type") in listOf("message","chat_created","message_updated","reaction_updated","block_updated")) {
                        runOnUiThread { if(chatId!=null) refreshMessages() else refreshChats() }
                    }
                } catch (_: Exception) {}
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!closed && token.isNotEmpty()) ui.postDelayed({ if(!closed && token.isNotEmpty()) openSocket() },6000)
            }
        })
    }
    private fun signOut() {
        token=""; selfId=""; session.clear(); socket?.close(1000,"logout"); socket=null; showLogin()
    }
    @Deprecated("Use system Back dispatcher where supported")
    override fun onBackPressed() {
        if(chatId!=null) showChats() else super.onBackPressed()
    }
    override fun onDestroy() {
        closed=true; socket?.close(1000,"finish"); client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}
