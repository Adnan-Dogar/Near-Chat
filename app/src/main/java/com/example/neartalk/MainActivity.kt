package com.example.neartalk

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Offline, foreground, verified one-to-one Nearby chat with app-private message history. */
class MainActivity : Activity() {
    private enum class Mode { IDLE, ADVERTISING, DISCOVERING, CONNECTING, CONNECTED }
    private lateinit var client: ConnectionsClient
    private lateinit var store: ChatStore
    private lateinit var nameInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var chatPeer: TextView
    private lateinit var hostButton: Button
    private lateinit var searchButton: Button
    private lateinit var stopButton: Button
    private lateinit var sendButton: Button
    private lateinit var historyButton: Button
    private lateinit var peersSection: LinearLayout
    private lateinit var peersList: LinearLayout
    private lateinit var chatMessages: LinearLayout
    private lateinit var chatScroll: ScrollView

    private var mode = Mode.IDLE
    private var pendingAction: Mode? = null
    private var localName = ""
    private var remoteEndpoint: String? = null
    private var remotePeerId: String? = null
    private var remoteName = ""
    private var pendingEndpoint: String? = null
    private var viewedPeerId: String? = null
    private var pairingDialog: AlertDialog? = null
    private val discoveredPeers = linkedMapOf<String, String>()
    private val outgoingTransfers = mutableMapOf<Long, Pair<String, String>>()
    private val serviceId = "com.example.neartalk"
    private val strategy = Strategy.P2P_POINT_TO_POINT

    private val myPeerId by lazy {
        val prefs = getPreferences(MODE_PRIVATE)
        prefs.getString("peer_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("peer_id", it).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        client = Nearby.getConnectionsClient(this)
        store = ChatStore(this)
        nameInput = findViewById(R.id.nameInput)
        messageInput = findViewById(R.id.messageInput)
        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        chatPeer = findViewById(R.id.chatPeer)
        hostButton = findViewById(R.id.hostButton)
        searchButton = findViewById(R.id.searchButton)
        stopButton = findViewById(R.id.stopButton)
        sendButton = findViewById(R.id.sendButton)
        historyButton = findViewById(R.id.historyButton)
        peersSection = findViewById(R.id.peersSection)
        peersList = findViewById(R.id.peersList)
        chatMessages = findViewById(R.id.chatMessages)
        chatScroll = findViewById(R.id.chatScroll)

        nameInput.setText(getPreferences(MODE_PRIVATE).getString("display_name", ""))
        messageInput.filters = arrayOf(InputFilter.LengthFilter(4000))
        hostButton.setOnClickListener { prepareConnection(Mode.ADVERTISING) }
        searchButton.setOnClickListener { prepareConnection(Mode.DISCOVERING) }
        stopButton.setOnClickListener { stopSession("Disconnected. Your chat history is saved on this phone.") }
        sendButton.setOnClickListener { sendMessage() }
        historyButton.setOnClickListener { showHistoryPicker() }
        historyButton.setOnLongClickListener { confirmDeleteViewedHistory(); true }
        addSystemMessage("Tap Be visible on one phone and Find nearby on the other. Both people verify the code before chatting.")
        updateUi()
    }

    private fun prepareConnection(action: Mode) {
        if (mode != Mode.IDLE) return
        val proposedName = nameInput.text.toString().trim()
        if (proposedName.length !in 2..18) {
            nameInput.error = "Enter 2–18 characters"
            return
        }
        localName = proposedName
        getPreferences(MODE_PRIVATE).edit().putString("display_name", localName).apply()
        val missing = requiredRuntimePermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            pendingAction = action
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
        } else begin(action)
    }

    private fun requiredRuntimePermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.NEARBY_WIFI_DEVICES
        )
        Build.VERSION.SDK_INT >= 31 -> listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION
        )
        Build.VERSION.SDK_INT >= 29 -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return
        val action = pendingAction
        pendingAction = null
        if (action != null && requiredRuntimePermissions().all {
                checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }) begin(action)
        else showNotice("Nearby permissions are needed. Allow Bluetooth / Nearby devices and, if requested, Location.")
    }

    private fun begin(action: Mode) {
        if (mode != Mode.IDLE) return
        when (action) {
            Mode.ADVERTISING -> {
                mode = Mode.ADVERTISING
                updateUi()
                client.startAdvertising(localName, serviceId, connectionCallback,
                    AdvertisingOptions.Builder().setStrategy(strategy).build())
                    .addOnFailureListener { error ->
                        if (mode == Mode.ADVERTISING) stopSession("Advertising failed: ${error.localizedMessage}")
                    }
            }
            Mode.DISCOVERING -> {
                mode = Mode.DISCOVERING
                discoveredPeers.clear()
                renderPeers()
                updateUi()
                client.startDiscovery(serviceId, discoveryCallback,
                    DiscoveryOptions.Builder().setStrategy(strategy).build())
                    .addOnFailureListener { error ->
                        if (mode == Mode.DISCOVERING) stopSession("Discovery failed: ${error.localizedMessage}")
                    }
            }
            else -> Unit
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            runOnUiThread {
                if (mode == Mode.DISCOVERING) {
                    discoveredPeers[endpointId] = info.endpointName
                    renderPeers()
                }
            }
        }
        override fun onEndpointLost(endpointId: String) {
            runOnUiThread {
                discoveredPeers.remove(endpointId)
                if (mode == Mode.DISCOVERING) renderPeers()
            }
        }
    }

    private fun renderPeers() {
        peersList.removeAllViews()
        if (discoveredPeers.isEmpty()) {
            peersList.addView(TextView(this).apply {
                text = "Searching… Ask your friend to tap Be visible."
                textSize = 12f
                setTextColor(0xFFAAC4D5.toInt())
                setPadding(dp(5), dp(12), dp(5), dp(12))
            })
        }
        discoveredPeers.forEach { (id, peerName) ->
            val button = Button(this).apply {
                text = "Connect to $peerName"
                isAllCaps = false
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.button_secondary)
                backgroundTintList = null
                setOnClickListener { connectTo(id, peerName) }
            }
            peersList.addView(button, LinearLayout.LayoutParams(-1, dp(49)))
        }
    }

    private fun connectTo(endpoint: String, name: String) {
        if (mode != Mode.DISCOVERING) return
        client.stopDiscovery()
        pendingEndpoint = endpoint
        remoteName = name
        mode = Mode.CONNECTING
        updateUi()
        client.requestConnection(localName, endpoint, connectionCallback).addOnFailureListener {
            if (mode == Mode.CONNECTING) stopSession("Connection request failed. Try again.")
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            runOnUiThread {
                if (mode != Mode.ADVERTISING && mode != Mode.CONNECTING) {
                    client.rejectConnection(endpointId)
                    return@runOnUiThread
                }
                if (pendingEndpoint != null && pendingEndpoint != endpointId) {
                    client.rejectConnection(endpointId)
                    return@runOnUiThread
                }
                pendingEndpoint = endpointId
                remoteName = info.endpointName
                mode = Mode.CONNECTING
                client.stopAdvertising()
                client.stopDiscovery()
                updateUi()
                pairingDialog?.dismiss()
                pairingDialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle("Verify ${info.endpointName}")
                    .setMessage("Compare this code on BOTH phones:\n\n${info.authenticationDigits}\n\nOnly accept if the codes match.")
                    .setCancelable(false)
                    .setPositiveButton("Codes match") { _, _ ->
                        client.acceptConnection(endpointId, payloadCallback)
                            .addOnFailureListener { stopSession("Couldn't accept the connection.") }
                    }
                    .setNegativeButton("Reject") { _, _ ->
                        client.rejectConnection(endpointId)
                        stopSession("Connection rejected.")
                    }.show()
            }
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            runOnUiThread {
                if (mode != Mode.CONNECTING || endpointId != pendingEndpoint) return@runOnUiThread
                pairingDialog?.dismiss()
                pairingDialog = null
                pendingEndpoint = null
                if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                    remoteEndpoint = endpointId
                    remotePeerId = null
                    mode = Mode.CONNECTED
                    chatMessages.removeAllViews()
                    addSystemMessage("Connected. Loading conversation…")
                    updateUi()
                    sendFrame(WireProtocol.Frame.Hello(myPeerId, localName))
                } else stopSession("Connection unsuccessful. Try again.")
            }
        }
        override fun onDisconnected(endpointId: String) {
            runOnUiThread {
                if (endpointId == remoteEndpoint || endpointId == pendingEndpoint) {
                    stopSession("The other device disconnected. Saved messages are still available in History.")
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val data = payload.asBytes() ?: return
            val frame = WireProtocol.decode(data) ?: return
            runOnUiThread {
                if (mode != Mode.CONNECTED || remoteEndpoint != endpointId) return@runOnUiThread
                when (frame) {
                    is WireProtocol.Frame.Hello -> {
                        if (remotePeerId != null && remotePeerId != frame.peerId) {
                            stopSession("The device changed its identity during the session.")
                            return@runOnUiThread
                        }
                        remotePeerId = frame.peerId
                        remoteName = frame.name
                        store.savePeer(frame.peerId, frame.name)
                        viewedPeerId = frame.peerId
                        renderHistory(frame.peerId)
                        updateUi()
                    }
                    is WireProtocol.Frame.Message -> {
                        val peer = remotePeerId ?: return@runOnUiThread
                        store.add(ChatStore.ChatMessage(frame.id, peer, frame.text, false,
                            System.currentTimeMillis(), "Received"))
                        if (viewedPeerId == peer) renderHistory(peer)
                        sendFrame(WireProtocol.Frame.Receipt(frame.id))
                    }
                    is WireProtocol.Frame.Receipt -> {
                        val peer = remotePeerId ?: return@runOnUiThread
                        store.setStatus(frame.id, peer, "Delivered")
                        if (viewedPeerId == peer) renderHistory(peer)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            runOnUiThread {
                if (endpointId != remoteEndpoint) return@runOnUiThread
                val item = outgoingTransfers[update.payloadId] ?: return@runOnUiThread
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        store.setStatusIfCurrent(item.first, item.second, "Sending", "Sent")
                        outgoingTransfers.remove(update.payloadId)
                        if (viewedPeerId == item.second) renderHistory(item.second)
                    }
                    PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                        store.setStatusIfCurrent(item.first, item.second, "Sending", "Failed")
                        outgoingTransfers.remove(update.payloadId)
                        if (viewedPeerId == item.second) renderHistory(item.second)
                    }
                }
            }
        }
    }

    private fun sendFrame(frame: WireProtocol.Frame, outgoing: Pair<String, String>? = null) {
        val endpoint = remoteEndpoint ?: return
        val payload = try { Payload.fromBytes(WireProtocol.encode(frame)) }
            catch (_: IllegalArgumentException) { showNotice("Message is too long."); return }
        if (outgoing != null) outgoingTransfers[payload.id] = outgoing
        client.sendPayload(endpoint, payload).addOnFailureListener {
            if (outgoing != null) {
                outgoingTransfers.remove(payload.id)
                store.setStatusIfCurrent(outgoing.first, outgoing.second, "Sending", "Failed")
                if (viewedPeerId == outgoing.second) renderHistory(outgoing.second)
            } else if (frame is WireProtocol.Frame.Hello) {
                showNotice("Unable to introduce this device. Reconnect and retry.")
            }
        }
    }

    private fun sendMessage() {
        val peer = remotePeerId ?: return
        if (mode != Mode.CONNECTED || viewedPeerId != peer) return
        val message = messageInput.text.toString().trim()
        if (message.isBlank()) return
        val id = UUID.randomUUID().toString()
        val frame = WireProtocol.Frame.Message(id, message)
        try { WireProtocol.encode(frame) } catch (_: IllegalArgumentException) {
            showNotice("Message too long. Keep it under 24 KB."); return
        }
        store.add(ChatStore.ChatMessage(id, peer, message, true, System.currentTimeMillis(), "Sending"))
        messageInput.text.clear()
        renderHistory(peer)
        sendFrame(frame, id to peer)
    }

    private fun showHistoryPicker() {
        val peers = store.peers()
        if (peers.isEmpty()) { showNotice("No saved chats yet."); return }
        val items = peers.map { it.name + (if (it.id == remotePeerId) " • Connected" else " • Saved") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Saved conversations")
            .setItems(items) { _, selection ->
                viewedPeerId = peers[selection].id
                renderHistory(peers[selection].id)
                updateUi()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmDeleteViewedHistory() {
        val peer = viewedPeerId ?: run { showNotice("Select a saved chat first."); return }
        AlertDialog.Builder(this).setTitle("Delete saved chat?")
            .setMessage("This permanently deletes the saved messages for ${store.peerName(peer)} on this device. It does not delete messages on their phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                store.delete(peer)
                chatMessages.removeAllViews()
                if (peer == remotePeerId) {
                    store.savePeer(peer, remoteName)
                    addSystemMessage("Chat history cleared. You can keep messaging.")
                } else {
                    viewedPeerId = null
                    addSystemMessage("Chat deleted from this phone.")
                }
                updateUi()
            }.show()
    }

    private fun renderHistory(peer: String) {
        chatMessages.removeAllViews()
        val messages = store.messages(peer)
        if (messages.isEmpty()) addSystemMessage("No messages saved for ${store.peerName(peer)} yet.")
        messages.forEach { addChatMessage(it) }
        scrollChatToBottom()
    }

    private fun addChatMessage(message: ChatStore.ChatMessage) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (message.mine) Gravity.END else Gravity.START
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val stamp = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(message.timestamp))
        val header = TextView(this).apply {
            text = "${if (message.mine) "You · ${message.status}" else store.peerName(message.peerId)}  ·  $stamp"
            setTextColor(Color.rgb(153, 180, 200))
            textSize = 10f
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        val bubble = TextView(this).apply {
            text = message.body
            textSize = 14f
            setTextColor(if (message.mine) Color.rgb(10, 31, 43) else Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()
            background = GradientDrawable().apply {
                setColor(if (message.mine) 0xFF4BE7BA.toInt() else 0xFF28475C.toInt())
                cornerRadius = dp(14).toFloat()
            }
        }
        column.addView(header)
        column.addView(bubble)
        row.addView(column)
        chatMessages.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
    }

    private fun addSystemMessage(message: String) {
        chatMessages.addView(TextView(this).apply {
            text = message
            setTextColor(0xFF92AEC1.toInt())
            gravity = Gravity.CENTER
            textSize = 12f
            setPadding(dp(12), dp(14), dp(12), dp(14))
        })
        scrollChatToBottom()
    }

    private fun scrollChatToBottom() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun stopSession(reason: String) {
        pairingDialog?.dismiss()
        pairingDialog = null
        pendingEndpoint = null
        remoteEndpoint = null
        remotePeerId = null
        remoteName = ""
        pendingAction = null
        discoveredPeers.clear()
        outgoingTransfers.clear()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        mode = Mode.IDLE
        if (viewedPeerId != null) renderHistory(viewedPeerId!!) else addSystemMessage(reason)
        showNotice(reason)
        updateUi()
    }

    private fun updateUi() {
        hostButton.isEnabled = mode == Mode.IDLE
        searchButton.isEnabled = mode == Mode.IDLE
        stopButton.isEnabled = mode != Mode.IDLE
        stopButton.text = if (mode == Mode.CONNECTED) "Leave" else "Stop"
        nameInput.isEnabled = mode == Mode.IDLE
        val ready = mode == Mode.CONNECTED && remotePeerId != null && remotePeerId == viewedPeerId
        messageInput.isEnabled = ready
        sendButton.isEnabled = ready
        peersSection.visibility = if (mode == Mode.DISCOVERING) View.VISIBLE else View.GONE
        chatPeer.text = when {
            viewedPeerId != null && viewedPeerId == remotePeerId -> "${remoteName} · Online"
            viewedPeerId != null -> "${store.peerName(viewedPeerId!!)} · Saved"
            else -> "Not connected"
        }
        when (mode) {
            Mode.IDLE -> {
                statusTitle.text = "● Ready to connect"
                statusDetail.text = "Nearby messaging. No internet required. Your history stays on this phone."
            }
            Mode.ADVERTISING -> {
                statusTitle.text = "● Visible to nearby devices"
                statusDetail.text = "Ask the other person to tap Find nearby. Keep this app open."
            }
            Mode.DISCOVERING -> {
                statusTitle.text = "● Searching for people"
                statusDetail.text = "Make sure Bluetooth and Wi-Fi radios are enabled."
            }
            Mode.CONNECTING -> {
                statusTitle.text = "● Check the verification code"
                statusDetail.text = "Both people must confirm matching codes."
            }
            Mode.CONNECTED -> {
                statusTitle.text = "● Connected to $remoteName"
                statusDetail.text = if (remotePeerId == null) "Setting up your private connection…"
                    else "Direct offline chat is ready. Stay nearby and keep this app open."
            }
        }
    }

    private fun showNotice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        pairingDialog?.dismiss()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        store.close()
        super.onDestroy()
    }

    companion object { private const val PERMISSION_REQUEST = 1001 }
}
