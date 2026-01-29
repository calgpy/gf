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
import com.goatfutbol.tv.WebViewActivity
import com.goatfutbol.tv.R

class MainActivity : AppCompatActivity() {

    // CONFIGURACION (LLENAR ESTO)
    private val GITHUB_TOKEN = "Bearer TOKEN_REMOVED_FOR_SECURITY" 
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

        Logger.setListener { newLogs ->
            tvLogOverlay.text = Logger.getLogs()
        }

        setupCrashHandler()
        Logger.log("App Iniciada correctamente.")

        loadMatchData()

        btnWatch.setOnClickListener {
            showMatchSelector()
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

    private var currentMatches: List<com.goatfutbol.tv.api.MatchItem>? = null

    private fun loadMatchData() {
        repository.getMatch(GITHUB_TOKEN) { result ->
            result.onSuccess { match ->
                if (!match.matches.isNullOrEmpty()) {
                    currentMatches = match.matches
                    tvMatchTitle.text = "${match.matches.size} Partidos Disponibles"
                    lastUrl = match.matches[0].url // Default to first
                    Logger.log("Cargados ${match.matches.size} partidos:")
                    match.matches.forEach { 
                        Logger.log("  - ${it.title} -> ${it.url}")
                    }
                } else {
                    tvMatchTitle.text = match.title
                    lastUrl = match.url
                    currentMatches = null
                    Logger.log("Datos cargados: ${match.title} -> ${match.url}")
                }
            }.onFailure { e ->
                tvMatchTitle.text = "Error cargando datos"
                Logger.log("Error Repo: ${e.message}")
            }
        }
    }

    private fun showMatchSelector() {
        if (currentMatches.isNullOrEmpty()) {
            if (lastUrl.isNotEmpty()) {
                openPlayer(lastUrl)
            } else {
                Toast.makeText(this, "No hay URL disponible", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val titles = currentMatches!!.map { it.title }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Selecciona un Partido")
            .setItems(titles) { _, which ->
                val selectedMatch = currentMatches!![which]
                Logger.log("Seleccionado: ${selectedMatch.title} -> ${selectedMatch.url}")
                openPlayer(selectedMatch.url)
            }
            .show()
    }

    private fun openPlayer(url: String) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("EXTRA_URL", url)
        startActivity(intent)
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
        Logger.log(message)
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
