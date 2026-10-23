package com.dieka.wallet.data

import java.util.UUID

data class BankCard(
    val id: String = UUID.randomUUID().toString(),
    val bank: String,
    val title: String,
    val number: String,
    val holder: String = "",
    val expiry: String = "",
    val cvv: String = "",
    val debit: Boolean = false,
    val theme: Int = 0,
    val cover: String = "",
    val network: String = "UnionPay",
    val design: CardDesign = CardDesign(),
    val appLink: AppLink? = null,
) {
    val lastFour: String get() = number.takeLast(4)
    val formattedNumber: String get() = number.chunked(4).joinToString(" ")
    val maskedNumber: String get() = "••••  ••••  ••••  $lastFour"

    companion object {
        fun demos() = listOf(
            BankCard("demo-1", "招商银行", "自由人生 · 经典白", "4111111111111111", "DEMO CARD", "12/29", "123", theme = 0, network = "VISA"),
            BankCard("demo-2", "中国银行", "山海之间 · 长城卡", "5555555555554444", "DEMO CARD", "08/30", "456", theme = 1, network = "Mastercard"),
            BankCard("demo-3", "中国建设银行", "蓝调时刻 · 龙卡", "4000000000000002", "DEMO CARD", "06/29", "789", theme = 2),
            BankCard("demo-4", "交通银行", "落日漫游 · 白金卡", "4242424242424242", "DEMO CARD", "09/28", "321", theme = 3, network = "VISA"),
            BankCard("demo-5", "中国工商银行", "日常随行 · 储蓄卡", "4000000000000069", "DEMO CARD", debit = true, theme = 4),
            BankCard("demo-6", "招商银行", "林间来信 · 一卡通", "4000000000000077", "DEMO CARD", debit = true, theme = 5),
        ).map { it.copy(design = CardDesign.legacy(it).copy(badge = "DEMO")) }
    }
}

data class AppLink(val packageName: String, val activityName: String = "", val detectedName: String = "", val customName: String = "") {
    val displayName get() = customName.trim().ifEmpty { detectedName.ifEmpty { packageName } }
}

/** Appearance is user-authored and never derived from account details during rendering. */
data class CardDesign(
    val emblem: String = "",
    val bank: String = "",
    val kind: String = "",
    val badge: String = "",
    val title: String = "",
    val number: String = "",
    val network: String = "",
) {
    companion object {
        // One-time compatibility snapshot for records saved before independent designs existed.
        fun legacy(card: BankCard) = CardDesign(
            emblem = card.bank.take(1), bank = card.bank,
            kind = if (card.debit) "DEBIT CARD" else "CREDIT CARD", badge = "DIÉKA",
            title = card.title, number = card.maskedNumber, network = card.network,
        )
    }
}

enum class DesignField(val label: String, val limit: Int) {
    EMBLEM("标记", 2), BANK("银行名称", 24), KIND("卡片类别", 24),
    BADGE("右上角文字", 12), TITLE("卡面名称", 30),
    NUMBER("卡面号码", 28), NETWORK("卡组织文字", 18);

    fun read(d: CardDesign): String = when (this) {
        EMBLEM -> d.emblem; BANK -> d.bank; KIND -> d.kind; BADGE -> d.badge
        TITLE -> d.title; NUMBER -> d.number; NETWORK -> d.network
    }
    fun write(d: CardDesign, text: String): CardDesign = when (this) {
        EMBLEM -> d.copy(emblem = text); BANK -> d.copy(bank = text); KIND -> d.copy(kind = text)
        BADGE -> d.copy(badge = text); TITLE -> d.copy(title = text)
        NUMBER -> d.copy(number = text); NETWORK -> d.copy(network = text)
    }
}

object CardValidation {
    fun numberError(number: String): String? {
        if (number.length !in 12..19 || number.any { it !in '0'..'9' }) return "请输入 12–19 位数字卡号"
        return null
    }

    fun expiryError(expiry: String, debit: Boolean): String? {
        if (expiry.isEmpty() && debit) return null
        if (!Regex("^(0[1-9]|1[0-2])/[0-9]{2}$").matches(expiry)) return "有效期格式为 MM/YY，例如 08/29"
        return null
    }

    fun cvvError(cvv: String): String? =
        if (cvv.isEmpty() || Regex("^[0-9]{3,4}$").matches(cvv)) null else "安全码应为 3 或 4 位数字"
}
