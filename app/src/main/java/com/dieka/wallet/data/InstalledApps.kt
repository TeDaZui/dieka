package com.dieka.wallet.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.text.Collator
import java.util.Locale

object InstalledApps {
    fun list(context: Context): List<AppLink> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())
        return manager.queryIntentActivities(intent, 0).filter {
            it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled && it.activityInfo.packageName != context.packageName
        }.map { AppLink(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(manager).toString()) }
            .distinctBy { it.packageName }.sortedWith { a, b -> collator.compare(a.detectedName, b.detectedName) }
    }

    fun launch(context: Context, link: AppLink): Boolean {
        val manager = context.packageManager
        val explicit = link.activityName.takeIf { it.isNotBlank() }?.let {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(link.packageName, it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val primary = explicit?.takeIf { manager.resolveActivity(it, 0) != null }
            ?: manager.getLaunchIntentForPackage(link.packageName) ?: return false
        return runCatching { context.startActivity(primary); true }.getOrDefault(false)
    }
}
