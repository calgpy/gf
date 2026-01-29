package com.goatfutbol.tv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.goatfutbol.tv.api.GitHubClient
import com.goatfutbol.tv.api.WorkflowDispatchBody
import com.goatfutbol.tv.data.MatchRepository
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    // CONFIGURACION (LLENAR ESTO)
    private val GITHUB_TOKEN = "Bearer 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" 
    private val OWNER = "calgpy"
    private val REPO = "gf"
    private val WORKFLOW_ID = "scrape.yml"

    private lateinit var tvMatchTitle: TextView
    private lateinit var btnWatch: Button
    private lateinit var btnUpdate: Button
    private lateinit var layoutLoading: LinearLayout
    private lateinit var tvLoadingStatus: TextView
    private lateinit var tvLogo: TextView

    // Debug Overlay Views
    private lateinit var layoutDebugOverlay: RelativeLayout
    private lateinit var tvLogOverlay: TextView
    private lateinit var btnCopyLog: Button

    private val repository = MatchRepository()
    private var clickCount = 0
    private var lastUrl = ""
    
    private val clickHandler = Handler(Looper.getMainLooper())
    private val processClicksRunnable = Runnable { 
        when (clickCount) {
            2 -> {
                // Toggle Overlay
                if (layoutDebugOverlay.visibility == View.VISIBLE) {
                    layoutDebugOverlay.visibility = View.GONE
                    log("Overlay DEBUG Desactivado")
                } else {
                    layoutDebugOverlay.visibility = View.VISIBLE
                    log("Overlay DEBUG Activado")
                }
            }
            3 -> {
                // Toggle Admin Mode
                if (btnUpdate.visibility == View.VISIBLE) {
                    btnUpdate.visibility = View.GONE
                    log("Modo Admin Desactivado")
                    Toast.makeText(this@MainActivity, "Modo Admin Desactivado", Toast.LENGTH_SHORT).show()
                } else {
                    btnUpdate.visibility = View.VISIBLE
                    log("Modo Admin ACTIVADO")
                    Toast.makeText(this@MainActivity, "Modo Admin Activado", Toast.LENGTH_SHORT).show()
                }
            }
        }
        clickCount = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMatchTitle = findViewById(R.id.tvMatchTitle)
        btnWatch = findViewById(R.id.btnWatch)
        btnUpdate = findViewById(R.id.btnUpdate)
        layoutLoading = findViewById(R.id.layoutLoading)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        tvLogo = findViewById(R.id.tvLogo)
        
        layoutDebugOverlay = findViewById(R.id.layoutDebugOverlay)
        tvLogOverlay = findViewById(R.id.tvLogOverlay)
        btnCopyLog = findViewById(R.id.btnCopyLog)

        setupCrashHandler()
        log("App Iniciada correctamente.")

        loadMatchData()

        btnWatch.setOnClickListener {
            if (lastUrl.isNotEmpty()) {
                val intent = Intent(this, WebViewActivity::class.java)
                intent.putExtra("EXTRA_URL", lastUrl)
                startActivity(intent)
            } else {
                Toast.makeText(this, "No hay URL disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // SECRET TRIGGER (Debounced)
        tvLogo.setOnClickListener {
            clickCount++
            log("Click buffer: $clickCount")
            
            // Reset timer and wait for more clicks
            clickHandler.removeCallbacks(processClicksRunnable)
            clickHandler.postDelayed(processClicksRunnable, 500) // 500ms delay to commit
        }
        
        btnCopyLog.setOnClickListener {
            copyLogToClipboard()
        }

        btnUpdate.setOnClickListener {
            triggerWorkflow()
        }
    }

    private fun loadMatchData() {
        repository.getMatch { result ->
            result.onSuccess { match ->
                tvMatchTitle.text = match.title
                lastUrl = match.url
                log("Datos cargados: ${match.title}")
            }.onFailure { e ->
                tvMatchTitle.text = "Error cargando datos"
                log("Error Repo: ${e.message}")
            }
        }
    }

    private fun triggerWorkflow() {
        layoutLoading.visibility = View.VISIBLE
        btnUpdate.visibility = View.GONE
        
        updateLoadingStatus("Iniciando scraper en GitHub...", 0)

        GitHubClient.service.triggerWorkflow(
            token = GITHUB_TOKEN,
            owner = OWNER,
            repo = REPO,
            workflowId = WORKFLOW_ID,
            body = WorkflowDispatchBody()
        ).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    log("Workflow disparado EXITOSAMENTE.")
                    // Workflow iniciado. Ahora simulamos polling.
                    startPolling()
                } else {
                    layoutLoading.visibility = View.GONE
                    val errorMsg = "Error al iniciar: ${response.code()} ${response.message()}"
                    log(errorMsg)
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                layoutLoading.visibility = View.GONE
                log("Fallo de RED: ${t.message}")
                Toast.makeText(this@MainActivity, "Fallo de red", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun startPolling() {
        // Simulacion de espera y carga (GitHub tarda unos 30-60 segs)
        val handler = Handler(Looper.getMainLooper())
        
        handler.postDelayed({ updateLoadingStatus("Buscando partido en vivo...", 25) }, 5000)
        handler.postDelayed({ updateLoadingStatus("Extrayendo enlace de video...", 50) }, 15000)
        handler.postDelayed({ updateLoadingStatus("Procesando claves...", 75) }, 25000)
        handler.postDelayed({ 
            updateLoadingStatus("Finalizando...", 100)
            loadMatchData() // Recargar datos reales
            layoutLoading.visibility = View.GONE
            Toast.makeText(this, "Actualizado!", Toast.LENGTH_LONG).show()
        }, 40000)
    }
    
    private fun updateLoadingStatus(text: String, percent: Int) {
        tvLoadingStatus.text = "$text ($percent%)"
        log("Status Update: $text")
    }

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val newLog = "[$timestamp] $message\n${tvLogOverlay.text}"
            tvLogOverlay.text = newLog
        }
    }

    private fun copyLogToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("GoatFutbol Log", tvLogOverlay.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Log copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    private fun setupCrashHandler() {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("CRASH FATAL: ${throwable.message}")
            throwable.printStackTrace()
            // Give UI a moment to update before crashing (best effort)
            try { Thread.sleep(2000) } catch (e: InterruptedException) {}
            oldHandler?.uncaughtException(thread, throwable)
        }
    }
}
