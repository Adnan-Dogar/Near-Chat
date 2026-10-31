package com.example.neartalk

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Online MVP: account sign-in, user codes, one-to-one and small group chats. No E2EE or push yet. */
class MainActivity : Activity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var chatsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var myDisplayName = "You"
    private var myCode = ""
    private var activeChatId: String? = null
    private var activeChatTitle = ""
    private val bg = 0xFF0D1626.toInt()
    private val panel = 0xFF1B2A40.toInt()
    private val fg = Color.WHITE
    private val muted = 0xFFA9BED2.toInt()
    private val accent = 0xFF54E3BA.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.FIREBASE_API_KEY.startsWith("SET_") ||
            BuildConfig.FIREBASE_APP_ID.startsWith("SET_") ||
            BuildConfig.FIREBASE_PROJECT_ID.startsWith("SET_")) {
            val container = screen("Near-Chat Online")
            label(container, "Firebase setup required", 21, fg)
            label(container, "This APK is a buildable source prototype. Add your Firebase Android app ID, API key, and project ID in the build settings, and deploy firestore.rules before signing in.", 15, muted)
            label(container, "See SETUP_FIREBASE.md in your GitHub repository.", 14, accent)
            return
        }
        try {
            val opts = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()
            val firebase = FirebaseApp.getApps(this).firstOrNull()
                ?: FirebaseApp.initializeApp(this, opts, FirebaseApp.DEFAULT_APP_NAME)
            auth = FirebaseAuth.getInstance(firebase)
            db = FirebaseFirestore.getInstance(firebase)
        } catch (e: Exception) {
            val container = screen("Configuration error")
            label(container, e.localizedMessage ?: "Check your Firebase configuration.", 14, muted)
            return
        }
        if (auth.currentUser == null) showSignIn() else loadProfile()
    }

    private fun showSignIn(register: Boolean = false) {
        clearListeners()
        val container = screen("Near-Chat · Online")
        label(container, if (register) "Create your account" else "Welcome back", 22, fg)
        label(container, "Message people anywhere with an internet connection.", 14, muted)
        val name = input(container, "Display name", false)
        name.visibility = if (register) View.VISIBLE else View.GONE
        val email = input(container, "Email", false).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val password = input(container, "Password (at least 6 characters)", true)
        button(container, if (register) "Create account" else "Sign in") {
            val mail = email.text.toString().trim()
            val pass = password.text.toString()
            if (!mail.contains('@') || pass.length < 6) { notice("Enter a valid email and a password of at least 6 characters."); return@button }
            if (register) {
                val display = name.text.toString().trim()
                if (display.length !in 2..32) { notice("Name must be 2–32 characters."); return@button }
                auth.createUserWithEmailAndPassword(mail, pass)
                    .addOnSuccessListener { createProfile(display) }
                    .addOnFailureListener { notice("Sign-up failed: ${it.localizedMessage}") }
            } else {
                auth.signInWithEmailAndPassword(mail, pass)
                    .addOnSuccessListener { loadProfile() }
                    .addOnFailureListener { notice("Sign-in failed: ${it.localizedMessage}") }
            }
        }
        button(container, if (register) "Already registered? Sign in" else "New here? Create an account", secondary = true) {
            showSignIn(!register)
        }
    }

    private fun createProfile(display: String) {
        val uid = auth.currentUser?.uid ?: return
        val code = UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.ROOT)
        val profile = mapOf(
            "displayName" to display,
            "code" to code,
            "status" to "Available to chat",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("profiles").document(uid).set(profile)
            .addOnSuccessListener { myDisplayName = display; myCode = code; showChats() }
            .addOnFailureListener { notice("Account created, but profile setup failed. Retry after signing in."); showProfileSetup() }
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return showSignIn()
        db.collection("profiles").document(uid).get()
            .addOnSuccessListener { profile ->
                if (!profile.exists()) showProfileSetup()
                else {
                    myDisplayName = profile.getString("displayName") ?: "You"
                    myCode = profile.getString("code") ?: ""
                    showChats()
                }
            }
            .addOnFailureListener { notice("Couldn't load profile: ${it.localizedMessage}"); showSignIn() }
    }

    private fun showProfileSetup() {
        val container = screen("Finish your profile")
        val name = input(container, "Display name", false)
        button(container, "Save profile") {
            val display = name.text.toString().trim()
            if (display.length !in 2..32) { notice("Enter a name of 2–32 characters."); return@button }
            createProfile(display)
        }
        button(container, "Sign out", secondary = true) { auth.signOut(); showSignIn() }
    }

    private fun showChats() {
        clearListeners()
        activeChatId = null
        val uid = auth.currentUser?.uid ?: return showSignIn()
        val container = screen("Near-Chat")
        label(container, "Hi, $myDisplayName", 17, fg)
        label(container, "Your friend code: $myCode", 13, accent)
        button(container, "Copy my friend code", secondary = true) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Near-Chat code", myCode))
            notice("Code copied. Share it with someone you trust.")
        }
        button(container, "+ Chat using a friend code") { showAddFriend() }
        button(container, "+ Create group chat", secondary = true) { showCreateGroup() }
        button(container, "Edit my status", secondary = true) { editStatus() }
        val heading = label(container, "Your conversations", 18, fg)
        heading.setPadding(0, dp(18), 0, dp(8))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(list)
        button(container, "Sign out", secondary = true) { auth.signOut(); showSignIn() }
        chatsListener = db.collection("chats").whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { notice("Cannot load chats: ${error.localizedMessage}"); return@addSnapshotListener }
                list.removeAllViews()
                if (snapshot == null || snapshot.isEmpty) { label(list, "No chats yet. Invite someone using your friend code.", 14, muted); return@addSnapshotListener }
                snapshot.documents.sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }.forEach { doc ->
                    val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val isGroup = doc.id.startsWith("group_")
                    val peer = members.firstOrNull { it != uid }
                    val groupTitle = doc.getString("title").orEmpty()
                    val line = button(list, if (isGroup) "# $groupTitle" else "Chat · ${peer?.take(8) ?: "Unknown"}", secondary = true) {
                        openChat(doc.id, if (isGroup) groupTitle else "Chat")
                    }
                    if (!isGroup && peer != null) db.collection("profiles").document(peer).get()
                        .addOnSuccessListener { p ->
                            line.text = "💬 ${p.getString("displayName") ?: "Contact"}"
                            line.setOnClickListener { openChat(doc.id, p.getString("displayName") ?: "Contact") }
                        }
                }
            }
    }

    private fun editStatus() {
        val edit = EditText(this).apply {
            hint = "Your short status"
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(100))
        }
        AlertDialog.Builder(this).setTitle("Change status").setView(edit)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val text = edit.text.toString().trim()
                if (text.isNotEmpty()) db.collection("profiles").document(auth.currentUser!!.uid)
                    .update("status", text).addOnFailureListener { notice("Couldn't save status.") }
            }.show()
    }

    private fun showAddFriend() {
        val field = EditText(this).apply {
            hint = "12-character friend code"
            setSingleLine(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this).setTitle("Start a chat").setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Find") { _, _ -> findFriend(field.text.toString().trim()) }
            .show()
    }

    private fun findFriend(code: String) {
        if (code.length != 12) { notice("Friend codes contain 12 characters."); return }
        db.collection("profiles").whereEqualTo("code", code.uppercase(Locale.ROOT)).limit(1).get()
            .addOnSuccessListener { matches ->
                val profile = matches.documents.firstOrNull()
                if (profile == null) { notice("No user found with that code."); return@addOnSuccessListener }
                if (profile.id == auth.currentUser?.uid) { notice("That's your own friend code."); return@addOnSuccessListener }
                val friendName = profile.getString("displayName") ?: "Friend"
                AlertDialog.Builder(this).setTitle("Chat with $friendName?")
                    .setMessage(profile.getString("status") ?: "")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Start") { _, _ -> createConversation(listOf(auth.currentUser!!.uid, profile.id), "", friendName) }
                    .show()
            }
            .addOnFailureListener { notice("Search failed: ${it.localizedMessage}") }
    }

    private fun showCreateGroup() {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(4), dp(18), dp(4)) }
        val title = input(wrapper, "Group title", false)
        val codes = input(wrapper, "Friend codes separated by commas", false)
        AlertDialog.Builder(this).setTitle("New group · up to 8 people")
            .setView(wrapper).setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val groupTitle = title.text.toString().trim()
                val ids = codes.text.toString().split(',').map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct()
                if (groupTitle.length !in 2..40 || ids.isEmpty() || ids.size > 7 || ids.any { it.length != 12 }) {
                    notice("Enter a group title and 1–7 valid friend codes.")
                } else lookupGroupMembers(ids, 0, mutableListOf(auth.currentUser!!.uid), groupTitle)
            }.show()
    }

    private fun lookupGroupMembers(codes: List<String>, index: Int, members: MutableList<String>, title: String) {
        if (index == codes.size) {
            if (members.size < 2) { notice("Add another member."); return }
            createConversation(members, title, title)
            return
        }
        db.collection("profiles").whereEqualTo("code", codes[index]).limit(1).get()
            .addOnSuccessListener { found ->
                val person = found.documents.firstOrNull()
                if (person == null) notice("Group member code not found: ${codes[index]}")
                else {
                    if (person.id !in members) members.add(person.id)
                    lookupGroupMembers(codes, index + 1, members, title)
                }
            }
            .addOnFailureListener { notice("Group member lookup failed.") }
    }

    private fun createConversation(members: List<String>, title: String, screenName: String) {
        val unique = members.distinct().sorted()
        if (unique.size !in 2..8) return
        val id = if (unique.size == 2) unique.joinToString("_") else "group_${UUID.randomUUID()}"
        val data = mapOf("members" to unique, "title" to title, "createdBy" to auth.currentUser!!.uid,
            "createdAt" to FieldValue.serverTimestamp())
        val reference = db.collection("chats").document(id)
        // Creating a new document is allowed; reading a non-existent chat is not.
        // If the deterministic direct chat already exists, creation becomes a denied update:
        // in that case we read the existing chat (as a member) and open it.
        reference.set(data).addOnSuccessListener { openChat(id, screenName) }
            .addOnFailureListener {
                reference.get().addOnSuccessListener { existing ->
                    if (existing.exists()) openChat(id, screenName)
                    else notice("Cannot create this chat. Check Firestore rules.")
                }.addOnFailureListener { notice("Cannot open chat. Check Firestore rules.") }
            }
    }

    private fun openChat(id: String, title: String) {
        clearListeners()
        activeChatId = id
        activeChatTitle = title
        val uid = auth.currentUser?.uid ?: return showSignIn()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(15), dp(18), dp(15), dp(12))
        }
        setContentView(root)
        button(root, "← Back to chats", secondary = true) { showChats() }
        label(root, title, 23, fg)
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(this).apply { setFillViewport(true); addView(messages) }
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val field = EditText(this).apply {
            hint = "Message..."
            setHintTextColor(muted)
            setTextColor(fg)
            maxLines = 4
            setBackgroundColor(panel)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            filters = arrayOf(android.text.InputFilter.LengthFilter(4000))
        }
        composer.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
        val send = Button(this).apply { text = "Send"; isAllCaps = false }
        composer.addView(send, LinearLayout.LayoutParams(-2, -2))
        root.addView(composer)
        send.setOnClickListener {
            val body = field.text.toString().trim()
            if (body.isBlank() || body.length > 4000) return@setOnClickListener
            send.isEnabled = false
            db.collection("chats").document(id).collection("messages").add(
                mapOf("sender" to uid, "text" to body, "sentAt" to FieldValue.serverTimestamp())
            ).addOnSuccessListener { field.text.clear(); send.isEnabled = true }
                .addOnFailureListener { send.isEnabled = true; notice("Could not send: ${it.localizedMessage}") }
        }
        messagesListener = db.collection("chats").document(id).collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING).limitToLast(150)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { notice("Chat unavailable: ${error.localizedMessage}"); return@addSnapshotListener }
                messages.removeAllViews()
                if (snapshot == null || snapshot.isEmpty) label(messages, "Start the conversation!", 14, muted)
                snapshot?.documents?.forEach { doc ->
                    val own = doc.getString("sender") == uid
                    val time = doc.getTimestamp("sentAt")?.toDate()
                    val formatted = if (time == null) "Sending…" else DateFormat.getTimeInstance(DateFormat.SHORT).format(time)
                    val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (own) Gravity.END else Gravity.START }
                    val bubble = TextView(this).apply {
                        text = doc.getString("text") ?: ""
                        setTextColor(if (own) bg else fg)
                        textSize = 15f
                        setPadding(dp(14), dp(11), dp(14), dp(11))
                        maxWidth = (resources.displayMetrics.widthPixels * .76).toInt()
                        background = rounded(if (own) accent else panel, 15)
                    }
                    row.addView(bubble)
                    val stamp = TextView(this).apply { text = "${if (own) "You" else "Member"} · $formatted"; setTextColor(muted); textSize = 10f }
                    row.addView(stamp)
                    messages.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                }
                scroller.post { scroller.fullScroll(View.FOCUS_DOWN) }
            }
    }

    private fun clearListeners() { chatsListener?.remove(); messagesListener?.remove(); chatsListener = null; messagesListener = null }
    override fun onDestroy() { clearListeners(); super.onDestroy() }

    private fun screen(title: String): LinearLayout {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); setFillViewport(true) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(26), dp(22), dp(26)) }
        scroll.addView(content)
        setContentView(scroll)
        label(content, title, 28, accent).setPadding(0, 0, 0, dp(14))
        return content
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(parent: LinearLayout, text: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            this.text = text; textSize = size.toFloat(); setTextColor(color)
            setPadding(dp(4), dp(7), dp(4), dp(7))
            if (size >= 20) setTypeface(typeface, Typeface.BOLD)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    private fun input(parent: LinearLayout, hint: String, secret: Boolean): EditText =
        EditText(this).apply {
            this.hint = hint
            setTextColor(fg); setHintTextColor(muted)
            setSingleLine(true)
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = rounded(panel, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })
        }
    private fun button(parent: LinearLayout, text: String, secondary: Boolean = false, click: () -> Unit): Button =
        Button(this).apply {
            this.text = text; isAllCaps = false; textSize = 14f
            setTextColor(if (secondary) fg else bg)
            background = rounded(if (secondary) panel else accent, 12)
            setOnClickListener { click() }
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(51)).apply { bottomMargin = dp(9) })
        }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
