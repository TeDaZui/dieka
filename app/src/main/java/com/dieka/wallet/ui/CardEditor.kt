@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.dieka.wallet.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.data.CardDesign
import com.dieka.wallet.data.DesignField
import com.dieka.wallet.data.CardValidation
import com.dieka.wallet.data.AppLink
import com.dieka.wallet.MainActivity

@Composable
fun CardEditor(initial: BankCard?, busy: Boolean, onDismiss: () -> Unit, onSave: (BankCard) -> Unit, onError: (String) -> Unit, open: Boolean = true, onClosed: () -> Unit = {}) {
    // Deliberately not rememberSaveable: do not put sensitive input into saved-state bundles.
    var bank by remember { mutableStateOf(initial?.bank.orEmpty()) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var number by remember { mutableStateOf(initial?.number.orEmpty()) }
    var holder by remember { mutableStateOf(initial?.holder.orEmpty()) }
    var expiryDigits by remember { mutableStateOf(initial?.expiry.orEmpty().replace("/", "")) }
    val expiry = if (expiryDigits.length > 2) expiryDigits.take(2) + "/" + expiryDigits.drop(2) else expiryDigits
    var cvv by remember { mutableStateOf(initial?.cvv.orEmpty()) }
    var debit by remember { mutableStateOf(initial?.debit ?: false) }
    var theme by remember { mutableIntStateOf(initial?.theme ?: 5) }
    var cover by remember { mutableStateOf(initial?.cover.orEmpty()) }
    var network by remember { mutableStateOf(initial?.network ?: "UnionPay") }
    var design by remember { mutableStateOf(initial?.design ?: CardDesign()) }
    var activeField by remember { mutableStateOf<DesignField?>(null) }
    var faceText by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var appLink by remember { mutableStateOf(initial?.appLink) }
    var linking by remember { mutableStateOf(false) }
    var showCvv by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        (context as? MainActivity)?.endCoverSelection()
        if (uri != null) cropUri = uri
    }
    val numberError = CardValidation.numberError(number)
    val expiryError = CardValidation.expiryError(expiry, debit)
    val cvvError = CardValidation.cvvError(cvv)
    MotionDialog(open, onDismiss = { if (!busy) onDismiss() }, onClosed = onClosed) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(onClick = onDismiss, enabled = open && !busy) { Icon(Icons.Outlined.Close, "取消编辑") }
                    Text(if (initial == null) "添加银行卡" else "编辑银行卡", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(enabled = open && !busy, onClick = {
                        attempted = true
                        if (bank.isNotBlank() && title.isNotBlank() && numberError == null && expiryError == null && cvvError == null) {
                            onSave(BankCard(id = initial?.id ?: java.util.UUID.randomUUID().toString(), bank = bank.trim(), title = title.trim(), number = number,
                                holder = holder.trim(), expiry = expiry, cvv = cvv, debit = debit, theme = theme, cover = cover, network = network, design = design, appLink = appLink))
                        }
                    }) { Text(if (busy) "保存中…" else "保存", fontWeight = FontWeight.Bold) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardFace(BankCard(bank = "", title = "", number = "", theme = theme, cover = cover, design = design),
                        Modifier.fillMaxWidth().aspectRatio(1.586f), onEditField = { field ->
                            activeField = field
                            faceText = field.read(design)
                        })
                    if (cover.isEmpty()) Text("点按卡面上的位置编辑文字", fontSize = 12.sp, color = Muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        cardColors.forEachIndexed { i, palette ->
                            Box(Modifier.size(40.dp).border(if (theme == i && cover.isEmpty()) 2.dp else 0.dp, Leaf, CircleShape).padding(4.dp)
                                .background(Brush.linearGradient(palette), CircleShape).semantics { contentDescription = "${themeNames[i]}卡面" }
                                .clickable { theme = i; cover = "" }, contentAlignment = Alignment.Center) {
                                if (theme == i && cover.isEmpty()) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                    TextButton(onClick = {
                        (context as? MainActivity)?.beginCoverSelection()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, enabled = !busy) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("选择自己的卡面图片", fontSize = 12.sp)
                    }
                    if (cover.isNotEmpty()) TextButton(onClick = { cover = "" }) { Text("移除图片，使用内置卡面") }
                    HorizontalDivider(color = Pale)
                    Text("银行卡资料", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !debit, onClick = { debit = false }, label = { Text("信用卡") })
                        FilterChip(selected = debit, onClick = { debit = true }, label = { Text("储蓄卡") })
                    }
                    EditorField(bank, { bank = it.take(30) }, "发卡银行", error = if (attempted && bank.isBlank()) "请填写银行名称" else null)
                    EditorField(title, { title = it.take(30) }, "卡片名称", placeholder = "例如：旅行卡、工资卡", error = if (attempted && title.isBlank()) "给卡片起个名字吧" else null)
                    EditorField(number, { number = it.filter { c -> c in '0'..'9' }.take(19) }, "银行卡号", keyboardType = KeyboardType.Number, error = if (attempted) numberError else null)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorField(expiryDigits, {
                            expiryDigits = it.filter { c -> c in '0'..'9' }.take(4)
                        }, if (debit) "有效期（可选）" else "有效期", Modifier.weight(1f), "MM/YY", KeyboardType.Number, if (attempted) expiryError else null,
                            visualTransformation = ExpiryTransformation)
                        OutlinedTextField(cvv, { cvv = it.filter { c -> c in '0'..'9' }.take(4) }, Modifier.weight(1f), label = { Text("安全码（可选）", fontSize = 12.sp) },
                            singleLine = true, shape = RoundedCornerShape(14.dp), isError = attempted && cvvError != null,
                            supportingText = if (attempted && cvvError != null) { { Text(cvvError, fontSize = 10.sp) } } else null,
                            visualTransformation = if (showCvv) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            trailingIcon = { PressIconButton(onClick = { showCvv = !showCvv }) { Icon(if (showCvv) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "切换安全码可见性", Modifier.size(18.dp)) } })
                    }
                    EditorField(holder, { holder = it.take(50) }, "持卡人（可选）")
                    TextButton(onClick = { linking = true }, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(appLink?.let { "快捷应用：${it.displayName}" } ?: "关联应用（可选）")
                    }
                    Text("卡组织", fontSize = 12.sp, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("UnionPay", "VISA", "Mastercard").forEach { value ->
                            FilterChip(selected = network == value, onClick = { network = value }, label = { Text(value, fontSize = 11.sp) })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    AppLinkSheet(linking, appLink, false, { linking = false }, { appLink = it; linking = false })
    activeField?.let { field ->
        AlertDialog(onDismissRequest = { activeField = null },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
            title = { Text("编辑${field.label}") },
            text = {
                OutlinedTextField(faceText, { faceText = it.take(field.limit) },
                    label = { Text("卡面文字") }, singleLine = true,
                    supportingText = { Text("仅修改卡面显示，不会更改银行卡资料。留空即可隐藏。", fontSize = 11.sp) })
            },
            confirmButton = { TextButton(onClick = { design = field.write(design, faceText.trim()); activeField = null }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { activeField = null }) { Text("取消") } })
    }
    cropUri?.let { uri ->
        CardCropper(uri, onCancel = { cropUri = null }, onConfirm = { cover = it; cropUri = null })
    }
}

@Composable
private fun EditorField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String = "", keyboardType: KeyboardType = KeyboardType.Text, error: String? = null, visualTransformation: VisualTransformation = VisualTransformation.None) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null, visualTransformation = visualTransformation,
        supportingText = if (error != null) { { Text(error, fontSize = 10.sp) } } else null)
}

private object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= 2) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(AnnotatedString(raw.take(2) + "/" + raw.drop(2)), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= 2) offset else offset - 1
        })
    }
}
