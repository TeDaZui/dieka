package com.dieka.wallet

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dieka.wallet.ui.DiekaTheme
import com.dieka.wallet.ui.WalletApp

class MainActivity : ComponentActivity() {
    private val model by viewModels<WalletViewModel>()
    private var authentication: CancellationSignal? = null
    private var authInProgress = false
    private var pickingCover = false

    fun beginCoverSelection() { pickingCover = true; model.revealed = false }
    fun endCoverSelection() { pickingCover = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiekaTheme {
                WalletApp(model, authenticate = ::authenticate)
            }
        }
    }

    private fun authenticate(action: () -> Unit) {
        if (authInProgress) return
        if (model.canReuseAuthentication()) { action(); return }
        if (!getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            model.message = "请先在系统设置中设置锁屏密码或指纹，再创建个人卡包。"
            return
        }
        if (!model.prepareKey()) return
        authInProgress = true
        model.revealed = false
        val signal = CancellationSignal()
        authentication = signal
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("解锁叠卡")
            .setSubtitle("验证身份，访问你的本地加密卡包")
            .setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(signal, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                authentication = null
                model.authenticated()
                action()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
                authentication = null
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) model.lock()
                model.message = "验证未完成，卡片信息保持隐藏。"
            }
        })
    }

    override fun onStart() {
        super.onStart()
        model.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // A system credential screen can temporarily stop this Activity.
        if (!authInProgress && !pickingCover && !isChangingConfigurations) model.onBackground()
        model.revealed = false
    }

    override fun onDestroy() {
        authentication?.cancel()
        super.onDestroy()
    }
}
