package net.kdt.pojavlaunch.aerix.ui.screens.social

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson

// All identities, messages, presence and invitations in this feature are local demonstration data.
data class SocialPerson(val name: String, val status: String, val avatar: Int, val online: Boolean = true)
data class SocialMessage(val id: Long, val person: String, val text: String, val mine: Boolean, val likes: Int = 0)
data class SocialPreferences(
    val online: Boolean = true, val activity: Boolean = true, val server: Boolean = false,
    val requests: String = "Everyone", val messages: String = "Accepted friends only",
    val invitations: String = "Accepted friends only", val friendAlerts: Boolean = true,
    val messageAlerts: Boolean = true, val partyAlerts: Boolean = true, val serverAlerts: Boolean = true,
    val launcherAlerts: Boolean = true, val gameAlerts: Boolean = true, val previews: Boolean = false
)
data class SocialData(
    val signedIn: Boolean = false, val appearanceComplete: Boolean = false,
    val name: String = "Nova", val skin: String = "Current skin", val cape: String = "None",
    val slim: Boolean = false, val friends: List<SocialPerson> = sampleFriends(),
    val incoming: List<String> = listOf("Ash", "Rowan"), val outgoing: List<String> = emptyList(),
    val blocked: List<String> = listOf("FrostByte", "EchoFox"),
    val messages: List<SocialMessage> = emptyList(), val reports: List<String> = emptyList(),
    val party: List<String> = emptyList(), val muted: Boolean = true, val deafened: Boolean = false,
    val voiceMode: String = "Push-to-talk", val preferences: SocialPreferences = SocialPreferences()
)
fun sampleFriends() = listOf(
    SocialPerson("Kai", "Minecraft running", 0), SocialPerson("Mira", "In a voice party", 1),
    SocialPerson("Luna", "Online in Aerix", 2), SocialPerson("Alex", "Minecraft running", 3),
    SocialPerson("Sam", "Offline", 4, false)
)

object SocialStore {
    private var context: Context? = null
    var data by mutableStateOf(SocialData()); private set
    var connectionError by mutableStateOf(false)
    var emptyPreview by mutableStateOf(false)
    var cosmeticRevision by androidx.compose.runtime.mutableIntStateOf(0)
    var voiceReconnecting by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    private val gson = Gson()

    fun attach(ctx: Context) {
        if (context != null) return
        context = ctx.applicationContext
        val saved = ctx.getSharedPreferences("aerix_social_demo", Context.MODE_PRIVATE).getString("data", null)
        if (saved != null) runCatching { gson.fromJson(saved, SocialData::class.java) }.getOrNull()?.let { data = it }
    }
    fun update(transform: (SocialData) -> SocialData) {
        data = transform(data)
        // commit persists the small demo state before the launcher intentionally exits for the game.
        context?.getSharedPreferences("aerix_social_demo", Context.MODE_PRIVATE)?.edit()
            ?.putString("data", gson.toJson(data))?.commit()
    }
    fun signIn() {
        // Mock Google sign-in only; no OAuth, account credentials, or network requests.
        update { it.copy(signedIn = true) }
        SocialShellNav.reset()
    }
    fun signOut() {
        update { it.copy(signedIn = false, party = emptyList(), muted = true) }
        SocialShellNav.reset()
    }
    fun deleteAccount() {
        val files = context?.filesDir
        listOf("social_skin.png", "social_cape.png", "social_skin_pending.png", "social_cape_pending.png").forEach { name -> files?.resolve(name)?.delete() }
        update { SocialData(friends = emptyList(), incoming = emptyList(), blocked = emptyList()) }
        SocialShellNav.reset()
    }
    fun request(name: String): String? {
        if (!Regex("[A-Za-z][A-Za-z0-9_]{2,19}").matches(name)) return "Use 3–20 letters, numbers or underscores. Start with a letter."
        if (name.equals(data.name, true)) return "You cannot add yourself."
        if (data.blocked.any { it.equals(name, true) }) return "Unblock this account in Safety first."
        if (data.friends.any { it.name.equals(name, true) }) return "You are already friends."
        if (data.outgoing.any { it.equals(name, true) }) return "A request is already pending."
        update { it.copy(outgoing = it.outgoing + name) }
        return null
    }
    fun accept(name: String) = update { it.copy(incoming = it.incoming - name,
        friends = it.friends + SocialPerson(name, "Online in Aerix", it.friends.size % 6)) }
    fun remove(name: String) = update { it.copy(friends = it.friends.filterNot { p -> p.name == name },
        messages = it.messages.filterNot { m -> m.person == name }, party = it.party - name) }
    fun block(name: String) {
        remove(name)
        update { it.copy(blocked = (it.blocked + name).distinct(), incoming = it.incoming - name, outgoing = it.outgoing - name) }
    }
    fun report(name: String, reason: String, detail: String) {
        update { it.copy(reports = it.reports + "$name: $reason — $detail") }
        notice = "Report saved locally. This demo does not send reports to moderators."
    }
    fun send(person: String, text: String) {
        if (text.isBlank() || data.friends.none { it.name == person } || person in data.blocked) return
        update { it.copy(messages = it.messages + SocialMessage(System.nanoTime(), person, text.trim().take(2000), true)) }
    }
    fun like(id: Long) = update { it.copy(messages = it.messages.map { m -> if (m.id == id) m.copy(likes = m.likes + 1) else m }) }
    fun messages(person: String): List<SocialMessage> = data.messages.filter { it.person == person }
    fun seedChat(person: String) {
        if (data.friends.none { it.name == person } || person in data.blocked) return
        if (messages(person).isNotEmpty()) return
        update { it.copy(messages = it.messages + listOf(
            SocialMessage(System.nanoTime(), person, "Want to play together?", false),
            SocialMessage(System.nanoTime() + 1, person, "Sure!", true),
            SocialMessage(System.nanoTime() + 2, person, "I'll send you a server invite.", false))) }
    }
    fun createParty() = update { it.copy(party = listOf(it.name), muted = true, deafened = false) }
    fun leaveParty() { update { it.copy(party = emptyList(), muted = true) }; voiceReconnecting = false }
}

enum class SocialRoute {
    AUTH, APPEARANCE, DASHBOARD, PROFILE, SKIN, CAPE, FRIEND, CHAT, PRIVACY, NOTIFICATIONS,
    VOICE, SAFETY, DELETE, REPORT, GUIDELINES, EXPLAIN
}
object SocialShellState {
    var isOpen by mutableStateOf(false); private set
    fun open() { SocialShellNav.reset(); isOpen = true }
    fun close() { isOpen = false }
}
object SocialShellNav {
    private var stack by mutableStateOf(listOf(SocialRoute.AUTH))
    val route get() = stack.last()
    var selectedPerson by mutableStateOf("Kai")
    var tab by mutableStateOf(0); private set
    var railChange by mutableStateOf(false); private set
    fun reset() {
        stack = listOf(when {
            !SocialStore.data.signedIn -> SocialRoute.AUTH
            !SocialStore.data.appearanceComplete -> SocialRoute.APPEARANCE
            else -> SocialRoute.DASHBOARD
        })
    }
    fun push(next: SocialRoute) { railChange = false; if (route != next) stack = stack + next }
    fun back(): Boolean { railChange = false; if (stack.size == 1) return false; stack = stack.dropLast(1); return true }
    fun showTab(index: Int) {
        if (!SocialStore.data.signedIn || !SocialStore.data.appearanceComplete) return
        tab = index; railChange = true; stack = listOf(SocialRoute.DASHBOARD)
    }
    fun chat(name: String) { selectedPerson = name; SocialStore.seedChat(name); push(SocialRoute.CHAT) }
}
