package io.github.ehbridge.ui

import android.Manifest
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import io.github.ehbridge.*
import io.github.ehbridge.model.*

class MainViewModel(app: Application) : AndroidViewModel(app) { val repository = (app as BridgeApp).repository }

class MainActivity : ComponentActivity() {
    private val repository get() = (application as BridgeApp).repository
    private val inputPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> acceptTree(true, result.resultCode, result.data) }
    private val outputPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> acceptTree(false, result.resultCode, result.data) }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Foreground work is allowed even if notification permission is declined. */ }
    private val reportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val text = report(this, repository.state.value)
            Thread {
                try {
                    contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) }
                    runOnUiThread { Toast.makeText(this, R.string.report_saved, Toast.LENGTH_SHORT).show() }
                } catch (_: Exception) { repository.error(Issue(Code.READ_WRITE_FAILURE)) }
            }.start()
        }
    }
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(newBase.withBridgeLocale()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val state = vm.repository.state.collectAsStateWithLifecycle().value
            BridgeTheme(state.theme) {
                val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                MainScreen(state, vm.repository, onPick = ::pick,
                    onStart = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        repository.begin()
                    },
                    onLanguage = { tag ->
                        repository.language(tag)
                        if (Build.VERSION.SDK_INT >= 33) getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
                        recreate()
                    },
                    onExport = { reportPicker.launch("EhBridge-report.txt") })
            }
        }
    }
    private fun pick(input: Boolean) {
        if (repository.state.value.busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        if (!input) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val previous = if (input) repository.state.value.input else repository.state.value.output
        previous?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, android.net.Uri.parse(it.uri)) }
        (if (input) inputPicker else outputPicker).launch(intent)
    }
    private fun acceptTree(input: Boolean, code: Int, data: Intent?) {
        if (code != RESULT_OK || data?.data == null || repository.state.value.busy) return
        val uri = data.data!!
        val required = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (input) 0 else Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            if (flags and required != required) throw SecurityException()
            contentResolver.takePersistableUriPermission(uri, if (input) Intent.FLAG_GRANT_READ_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            repository.select(input, uri)
        } catch (_: Exception) { repository.error(Issue(Code.PERMISSION_REQUIRED)) }
    }
}
