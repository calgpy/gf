package com.goatfutbol.tv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.goatfutbol.tv.api.GitHubClient
import com.goatfutbol.tv.api.WorkflowDispatchBody
import com.goatfutbol.tv.data.MatchRepository
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    // CONFIGURACION (LLENAR ESTO)
    private val GITHUB_TOKEN = "Bearer PON_TU_TOKEN_AQUI" 
    private val OWNER = "calgpy"
    private val REPO = "gf"
    private val WORKFLOW_ID = "scrape.yml"

    private lateinit var tvMatchTitle: TextView
    private lateinit var btnWatch: Button
    private lateinit var btnUpdate: Button
    private lateinit var layoutLoading: LinearLayout
    private lateinit var tvLoadingStatus: TextView
    private lateinit var tvLogo: TextView

    private val repository = MatchRepository()
    private var clickCount = 0
    private var lastUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMatchTitle = findViewById(R.id.tvMatchTitle)
        btnWatch = findViewById(R.id.btnWatch)
        btnUpdate = findViewById(R.id.btnUpdate)
        layoutLoading = findViewById(R.id.layoutLoading)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        tvLogo = findViewById(R.id.tvLogo)

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

        // SECRET TRIGGER (3 Clicks)
        tvLogo.setOnClickListener {
            clickCount++
            if (clickCount >= 3) {
                Toast.makeText(this, "Modo Admin Activado", Toast.LENGTH_SHORT).show()
                btnUpdate.visibility = View.VISIBLE
                clickCount = 0
            }
        }

        btnUpdate.setOnClickListener {
            triggerWorkflow()
        }
    }

    private fun loadMatchData() {
        repository.getMatch { match ->
            if (match != null) {
                tvMatchTitle.text = match.title
                lastUrl = match.url
            } else {
                tvMatchTitle.text = "Error cargando datos"
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
                    // Workflow iniciado. Ahora simulamos polling.
                    startPolling()
                } else {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error al iniciar: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                layoutLoading.visibility = View.GONE
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
    }
}
