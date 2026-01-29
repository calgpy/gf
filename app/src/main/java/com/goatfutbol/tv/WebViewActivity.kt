package com.goatfutbol.tv

import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        setContentView(webView)

        val url = intent.getStringExtra("EXTRA_URL") ?: "about:blank"

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // REVERTIR a User Agent MÓVIL (Android Chrome)
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            private val adDomains = listOf(
                "skinnycrawlinglax.com",
                "doubleclick.net",
                "google-analytics.com",
                "onclickperformance.com",
                "sourshaped.com",
                "realizationnewestfangs.com",
                "charmingpoliteinjunction.com",
                "feasibledecisiveasserted.com"
            )

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleNavigation(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return handleNavigation(request?.url?.toString())
            }

            private fun handleNavigation(url: String?): Boolean {
                if (url == null) return false
                
                // Only block navigation to ad domains (prevents popups/redirects)
                // But allow them to load as resources (scripts, etc)
                for (domain in adDomains) {
                    if (url.contains(domain)) {
                        Logger.log("NAV_BLOCKED: $domain")
                        return true // Block navigation
                    }
                }
                
                // Allow navigation to the actual streaming page
                Logger.log("Navig: $url")
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                
                // Intercept main HTML pages from futbolparaguayotv.github.io
                if (url.contains("futbolparaguayotv.github.io") && url.contains(".html")) {
                    Logger.log("INTERCEPTING: $url")
                    try {
                        // Load original content
                        val connection = java.net.URL(url).openConnection()
                        val originalHtml = connection.getInputStream().bufferedReader().use { it.readText() }
                        
                        // Extract manifest URL and DRM keys from original HTML
                        val manifestRegex = """youtube_theme_manifestUri\s*=\s*['"]([^'"]+)['"]""".toRegex()
                        val manifestMatch = manifestRegex.find(originalHtml)
                        val manifestUrl = manifestMatch?.groupValues?.get(1) ?: ""
                        
                        val keyRegex = """["']([0-9a-f]{32})["']\s*:\s*["']([0-9a-f]{32})["']""".toRegex()
                        val keyMatch = keyRegex.find(originalHtml)
                        val keyId = keyMatch?.groupValues?.get(1) ?: ""
                        val key = keyMatch?.groupValues?.get(2) ?: ""
                        
                        Logger.log("MANIFEST: $manifestUrl")
                        Logger.log("DRM: $keyId:$key")
                        
                        // Create clean HTML
                        val cleanHtml = createCleanPlayerHtml(manifestUrl, keyId, key)
                        
                        return android.webkit.WebResourceResponse(
                            "text/html",
                            "UTF-8",
                            java.io.ByteArrayInputStream(cleanHtml.toByteArray())
                        )
                    } catch (e: Exception) {
                        Logger.log("INTERCEPT_ERROR: ${e.message}")
                    }
                }
                
                // Block ad domains
                for (domain in adDomains) {
                    if (url.contains(domain)) {
                        Logger.log("RESOURCE_BLOCKED: $domain")
                        return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                    }
                }
                
                return null
            }

            private fun createCleanPlayerHtml(manifestUrl: String, keyId: String, key: String): String {
                return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://cdn.jsdelivr.net/npm/shaka-player@4.14.10/dist/shaka-player.compiled.js"></script>
    <style>
        body { margin: 0; padding: 0; background: #000; overflow: hidden; }
        video { width: 100vw; height: 100vh; object-fit: contain; }
    </style>
</head>
<body>
    <video id="video" autoplay></video>
    <script>
        console.log("GF_DEBUG: Clean HTML loaded");
        
        async function initPlayer() {
            try {
                console.log("GF_DEBUG: Starting player initialization");
                
                // Check if Shaka is loaded
                if (typeof shaka === 'undefined') {
                    console.log('GF_DEBUG: ERROR - Shaka Player library not loaded!');
                    return;
                }
                console.log("GF_DEBUG: Shaka library confirmed loaded");
                
                // Check browser support
                if (!shaka.Player.isBrowserSupported()) {
                    console.log('GF_DEBUG: ERROR - Browser not supported!');
                    return;
                }
                console.log("GF_DEBUG: Browser is supported");
                
                const video = document.getElementById('video');
                if (!video) {
                    console.log('GF_DEBUG: ERROR - Video element not found!');
                    return;
                }
                console.log("GF_DEBUG: Video element found");
                
                // Create player instance
                console.log("GF_DEBUG: Creating Shaka Player instance...");
                const player = new shaka.Player(video);
                console.log("GF_DEBUG: Player instance created");
                
                // Configure DRM
                player.configure({
                    drm: {
                        clearKeys: {
                            "$keyId": "$key"
                        }
                    }
                });
                console.log("GF_DEBUG: Player configured with DRM keys");
                
                // Error handling
                player.addEventListener('error', function(event) {
                    const error = event.detail;
                    console.log('GF_DEBUG: Player error - Code: ' + error.code + ', Category: ' + error.category);
                    console.log('GF_DEBUG: Error details: ' + JSON.stringify(error));
                });
                
                console.log("GF_DEBUG: Loading manifest: $manifestUrl");
                await player.load("$manifestUrl");
                console.log("GF_DEBUG: Manifest loaded successfully");
                
                // Start playback
                video.muted = true;
                console.log("GF_DEBUG: Attempting to play (muted)...");
                await video.play();
                console.log("GF_DEBUG: Playback started (muted)");
                
                setTimeout(function() {
                    video.muted = false;
                    video.volume = 1.0;
                    console.log("GF_DEBUG: Unmuted - playback with audio");
                }, 2000);
                
            } catch (error) {
                console.log('GF_DEBUG: EXCEPTION in initPlayer - ' + error.name + ': ' + error.message);
                console.log('GF_DEBUG: Stack: ' + error.stack);
            }
        }
        
        // Install polyfills
        shaka.polyfill.installAll();
        
        // Wait for scripts to load
        console.log("GF_DEBUG: Document ready state: " + document.readyState);
        if (document.readyState === 'loading') {
            console.log("GF_DEBUG: Waiting for DOMContentLoaded...");
            document.addEventListener('DOMContentLoaded', function() {
                console.log("GF_DEBUG: DOMContentLoaded fired");
                initPlayer();
            });
        } else {
            console.log("GF_DEBUG: Document already loaded, calling initPlayer immediately");
            initPlayer();
        }
    </script>
</body>
</html>
                """.trimIndent()
            }


            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Logger.log("START_LOAD: $url")
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    Logger.log("WEB_ERR: ${error?.toString()} @ ${request.url}")
                } else {
                    Logger.log("RESOURCE_ERR: ${error?.toString()} @ ${request?.url}")
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    Logger.log("HTTP_ERR: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase} @ ${request.url}")
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                Logger.log("SSL_ERR: ${error?.toString()} - ACCEPTING ANYWAY")
                // Accept SSL errors to bypass certificate issues
                handler?.proceed()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Logger.log("FINISHED: $url")
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.message()?.let { 
                    if (it.startsWith("GF_DEBUG")) {
                        Logger.log("JS_LOG: $it")
                    }
                }
                return true
            }
        }
        
        // Fullscreen mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        webView.loadUrl(url)
    }
}
