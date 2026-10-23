package com.dieka.wallet.data

import org.json.JSONObject

internal object CardCodec {
    fun decode(o: JSONObject): BankCard {
        val card = BankCard(
            id = o.getString("id"), bank = o.getString("bank"),
            title = o.getString("title"), number = o.getString("number"),
            holder = o.optString("holder"), expiry = o.optString("expiry"),
            cvv = o.optString("cvv"), debit = o.optBoolean("debit"),
            theme = o.optInt("theme"), cover = o.optString("cover"),
            network = o.optString("network", "UnionPay"),
            appLink = o.optJSONObject("appLink")?.let {
                AppLink(it.getString("packageName"), it.optString("activityName"), it.optString("detectedName"), it.optString("customName"))
            },
        )
        val design = o.optJSONObject("design")
        return card.copy(design = if (design == null) CardDesign.legacy(card) else CardDesign(
            emblem = design.optString("emblem"), bank = design.optString("bank"),
            kind = design.optString("kind"), badge = design.optString("badge"),
            title = design.optString("title"), number = design.optString("number"),
            network = design.optString("network"),
        ))
    }

    fun encode(c: BankCard): JSONObject = JSONObject().apply {
        put("id", c.id); put("bank", c.bank); put("title", c.title)
        put("number", c.number); put("holder", c.holder); put("expiry", c.expiry)
        put("cvv", c.cvv); put("debit", c.debit); put("theme", c.theme)
        put("cover", c.cover); put("network", c.network)
        c.appLink?.let { link ->
            put("appLink", JSONObject().apply {
                put("packageName", link.packageName); put("activityName", link.activityName)
                put("detectedName", link.detectedName); put("customName", link.customName)
            })
        }
        put("design", JSONObject().apply {
            put("emblem", c.design.emblem); put("bank", c.design.bank)
            put("kind", c.design.kind); put("badge", c.design.badge)
            put("title", c.design.title); put("number", c.design.number)
            put("network", c.design.network)
        })
    }
}
