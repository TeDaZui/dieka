package com.dieka.wallet

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dieka.wallet.data.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.util.UUID

data class WalletNotice(val text: String, val undoId: String? = null, val error: Boolean = false,
    val id: String = UUID.randomUUID().toString())

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val vault = VaultStore(application)
    private val preferences = application.getSharedPreferences("display", 0)
    var demo by mutableStateOf(!vault.exists()); private set
    var locked by mutableStateOf(!demo); private set
    private var snapshot by mutableStateOf(VaultSnapshot(if (demo) BankCard.demos() else emptyList()))
    val cards get() = snapshot.cards
    val trash get() = snapshot.trash.filter { it.recoverable(now) }.sortedByDescending { it.deletedAt }
    var now by mutableLongStateOf(System.currentTimeMillis()); private set
    var today by mutableStateOf(LocalDate.now()); private set
    val expiring get() = if (demo || locked) emptyList() else ExpiryPolicy.upcoming(cards, today)
    var compactCount by mutableIntStateOf(preferences.getInt("compact_count", 4).coerceIn(3, 5)); private set
    var hapticsEnabled by mutableStateOf(preferences.getBoolean("haptics", true)); private set
    var locateRequest by mutableIntStateOf(0); private set
    var linkingCardId by mutableStateOf<String?>(null)
    // Navigation stays in memory across Activity recreation; account data is never put in a saved-state Bundle.
    val searchState = mutableStateOf("")
    val searchingState = mutableStateOf(false)
    val filterState = mutableIntStateOf(0)
    val layoutState = mutableStateOf(false)
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf<WalletNotice?>(null)
    var message: String?
        get() = notice?.text
        set(value) { notice = value?.let { WalletNotice(it, error = true) } }
    var showEditor by mutableStateOf(false)
    var showSort by mutableStateOf(false)
    var showTrash by mutableStateOf(false)
    var editing by mutableStateOf<BankCard?>(null)
    var selectedId by mutableStateOf<String?>(null); private set
    val detail get() = cards.firstOrNull { it.id == selectedId }
    var infoOpen by mutableStateOf(false)
    var revealed by mutableStateOf(false)
    private var generation = 0
    private val session = SessionPolicy()
    private var backgroundLock: Job? = null
    private var foregroundTicker: Job? = null

    fun updateCompactCount(count: Int) {
        compactCount = count.coerceIn(3, 5)
        preferences.edit().putInt("compact_count", compactCount).apply()
    }
    fun updateHaptics(enabled: Boolean) { hapticsEnabled = enabled; preferences.edit().putBoolean("haptics", enabled).apply() }
    fun focus(card: BankCard?, locate: Boolean = false) {
        selectedId = card?.id; infoOpen = false; revealed = false
        if (locate && card != null) locateRequest++
    }
    fun linkApp(cardId: String, link: AppLink?) {
        if (busy || locked) return
        val next = snapshot.copy(cards = cards.map { if (it.id == cardId) it.copy(appLink = link) else it })
        if (demo) { snapshot = next; linkingCardId = null }
        else persist(next) { linkingCardId = null }
    }
    fun refreshTime() { now = System.currentTimeMillis(); today = LocalDate.now() }
    fun canReuseAuthentication() = !locked && session.canReuse(SystemClock.elapsedRealtime())
    fun authenticated() { session.verified(SystemClock.elapsedRealtime()) }
    fun onBackground() {
        revealed = false
        foregroundTicker?.cancel()
        session.background(SystemClock.elapsedRealtime())
        backgroundLock?.cancel()
        backgroundLock = viewModelScope.launch { delay(SessionPolicy.BACKGROUND_MS); lock() }
    }
    fun onForeground() {
        refreshTime()
        backgroundLock?.cancel()
        if (session.mustLock(SystemClock.elapsedRealtime())) lock()
        session.foreground()
        foregroundTicker?.cancel()
        foregroundTicker = viewModelScope.launch {
            while (isActive) { delay(1_000); refreshTime() }
        }
    }
    fun prepareKey(): Boolean = try { vault.prepareKey(); true } catch (_: Exception) {
        message = "无法访问加密密钥。请检查设备锁屏设置；原卡库未被更改。"; false
    }
    fun unlock(addAfter: Boolean = false) {
        val ticket = ++generation
        busy = true
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val stored = vault.load()
                    val cleaned = stored.cleaned(System.currentTimeMillis())
                    if (cleaned != stored) vault.save(cleaned)
                    cleaned
                }
                if (ticket != generation) return@launch
                snapshot = loaded; demo = false; locked = false; refreshTime()
                if (addAfter) { editing = null; showEditor = true }
            } catch (_: Exception) {
                if (ticket == generation) { session.clear(); message = "卡库未能解锁。请重新验证；不会覆盖原有数据。" }
            } finally { if (ticket == generation) busy = false }
        }
    }
    fun save(card: BankCard) {
        if (busy || locked || demo) return
        if (cards.any { it.id != card.id && it.number == card.number }) { message = "这张银行卡已经在卡包里了"; return }
        val next = if (cards.any { it.id == card.id }) cards.map { if (it.id == card.id) card else it } else cards + card
        persist(snapshot.copy(cards = next)) {
            showEditor = false; editing = null; revealed = false
            if (selectedId == null) focus(card, locate = true)
            notice = WalletNotice("已保存到加密卡包")
        }
    }
    fun delete(card: BankCard) {
        if (busy || locked || demo) return
        val (next, entry) = snapshot.remove(card.id, System.currentTimeMillis()) ?: return
        persist(next) { focus(null); notice = WalletNotice("已移除", entry.id) }
    }
    fun restore(entryId: String) {
        if (busy || locked || demo) return
        refreshTime()
        val next = try { snapshot.restore(entryId, now) } catch (e: IllegalArgumentException) {
            message = e.message; return
        } catch (e: IllegalStateException) { message = e.message; return }
        persist(next) { notice = WalletNotice("已恢复卡片") }
    }
    fun reorder(ids: List<String>) {
        if (busy || locked || !showSort) return
        val next = CardOrder.apply(cards, ids)
        if (next == null) { message = "卡片列表已变化，请重新打开排序"; return }
        if (demo || next == cards) { snapshot = snapshot.copy(cards = next); showSort = false }
        else persist(snapshot.copy(cards = next)) { showSort = false }
    }
    private fun persist(next: VaultSnapshot, done: () -> Unit) {
        val ticket = generation
        busy = true
        viewModelScope.launch {
            try {
                val cleaned = next.cleaned(System.currentTimeMillis())
                withContext(Dispatchers.IO) { vault.save(cleaned) }
                if (ticket == generation) { snapshot = cleaned; refreshTime(); done() }
            } catch (_: Exception) {
                if (ticket == generation) { session.clear(); message = "保存失败，请重新验证后再试；原有卡片未被覆盖。" }
            } finally { if (ticket == generation) busy = false }
        }
    }
    fun lock() {
        backgroundLock?.cancel(); session.clear()
        showSort = false; showTrash = false; showEditor = false; editing = null; notice = null; linkingCardId = null
        focus(null)
        if (demo) return
        generation++
        snapshot = VaultSnapshot(); locked = true; busy = false
    }
}
