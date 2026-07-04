package com.example.ui

import android.annotation.SuppressLint
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class WebAppInterface(private val onBarcodeDetected: (String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onBarcodeDetected(barcode: String) {
        onBarcodeDetected(barcode)
    }

    @android.webkit.JavascriptInterface
    fun onError(error: String) {
        android.util.Log.e("WasmCameraScanner", "WASM Scanner Error: $error")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WasmCameraScanner(
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val htmlContent = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: #0b0f19;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    overflow: hidden;
                    color: white;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    width: 100vw;
                    height: 100vh;
                }
                #video-container {
                    position: relative;
                    width: 100%;
                    height: 100%;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                }
                video {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                }
                canvas {
                    display: none;
                }
                .scanner-laser {
                    position: absolute;
                    top: 50%;
                    left: 5%;
                    width: 90%;
                    height: 3px;
                    background: linear-gradient(90deg, transparent, #10b981, #34d399, #10b981, transparent);
                    box-shadow: 0 0 15px #10b981;
                    animation: laser-scan 2s infinite ease-in-out;
                    z-index: 10;
                }
                @keyframes laser-scan {
                    0% { top: 15%; }
                    50% { top: 85%; }
                    100% { top: 15%; }
                }
                .status-badge {
                    position: absolute;
                    bottom: 20px;
                    background-color: rgba(11, 15, 25, 0.85);
                    padding: 8px 18px;
                    border-radius: 50px;
                    font-size: 11px;
                    font-weight: 600;
                    letter-spacing: 1px;
                    border: 1px solid rgba(16, 185, 129, 0.4);
                    z-index: 20;
                    text-transform: uppercase;
                    color: #34d399;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
                    animation: pulse 2s infinite ease-in-out;
                }
                @keyframes pulse {
                    0% { box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2); }
                    50% { box-shadow: 0 4px 20px rgba(16, 185, 129, 0.5); }
                    100% { box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2); }
                }
                .loader {
                    position: absolute;
                    border: 3px solid rgba(255, 255, 255, 0.1);
                    border-radius: 50%;
                    border-top: 3px solid #10b981;
                    width: 40px;
                    height: 40px;
                    animation: spin 1s linear infinite;
                    z-index: 30;
                }
                @keyframes spin {
                    0% { transform: rotate(0deg); }
                    100% { transform: rotate(360deg); }
                }
                
                /* Scanning Target Reticle & Spinner */
                .scanner-target {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    width: 220px;
                    height: 130px;
                    border: 2px dashed rgba(255, 255, 255, 0.4);
                    border-radius: 12px;
                    box-sizing: border-box;
                    z-index: 5;
                    pointer-events: none;
                    transition: all 0.3s ease;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                
                /* High-tech spinner for positioning visual aid */
                .scanning-spinner {
                    width: 44px;
                    height: 44px;
                    border: 3px solid rgba(16, 185, 129, 0.15);
                    border-top: 3px solid #10b981;
                    border-radius: 50%;
                    animation: spin 1.2s linear infinite;
                    pointer-events: none;
                    transition: opacity 0.3s ease;
                }
                
                .scanner-target.success {
                    border-color: #10b981;
                    background-color: rgba(16, 185, 129, 0.15);
                    box-shadow: 0 0 20px rgba(16, 185, 129, 0.4);
                    transform: translate(-50%, -50%) scale(1.05);
                }
                
                /* Success screen overlay in WebView */
                .success-overlay {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    background-color: rgba(11, 15, 25, 0.95);
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    z-index: 100;
                    opacity: 0;
                    pointer-events: none;
                    transition: opacity 0.3s ease;
                }
                
                .success-overlay.show {
                    opacity: 1;
                    pointer-events: auto;
                }
                
                .checkmark-icon {
                    width: 60px;
                    height: 60px;
                    border-radius: 50%;
                    background-color: #10b981;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-size: 32px;
                    font-weight: bold;
                    box-shadow: 0 4px 15px rgba(16, 185, 129, 0.4);
                    margin-bottom: 12px;
                    transform: scale(0.5);
                    transition: transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
                }
                
                .success-overlay.show .checkmark-icon {
                    transform: scale(1);
                }
                
                .success-title {
                    font-size: 16px;
                    font-weight: bold;
                    letter-spacing: 1.5px;
                    text-transform: uppercase;
                    color: #34d399;
                }
                
                .success-val {
                    font-family: monospace;
                    font-size: 14px;
                    margin-top: 8px;
                    background: rgba(255, 255, 255, 0.1);
                    padding: 6px 16px;
                    border-radius: 6px;
                    max-width: 80%;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                    border: 1px solid rgba(255, 255, 255, 0.15);
                    color: white;
                }
            </style>
        </head>
        <body>
            <div id="video-container">
                <div class="loader" id="loader"></div>
                <video id="webcam" autoplay playsinline muted></video>
                
                <!-- Reticle & Spinner targeting helper -->
                <div class="scanner-target" id="target-box">
                    <div class="scanning-spinner" id="scanner-spinner"></div>
                </div>
                
                <div class="scanner-laser"></div>
                <div class="status-badge" id="status">Loading zbar.wasm...</div>
                
                <!-- Success feedback screen overlay -->
                <div class="success-overlay" id="success-overlay">
                    <div class="checkmark-icon">✓</div>
                    <div class="success-title">Barcode Decoded</div>
                    <div class="success-val" id="decoded-value"></div>
                </div>
            </div>
            <canvas id="hidden-canvas"></canvas>

            <script type="module">
                import { scanImage } from 'https://cdn.jsdelivr.net/npm/@samsam2310/zbar.wasm@1.1.0/dist/index.js';

                const video = document.getElementById('webcam');
                const canvas = document.getElementById('hidden-canvas');
                const context = canvas.getContext('2d');
                const statusBadge = document.getElementById('status');
                const loader = document.getElementById('loader');
                const targetBox = document.getElementById('target-box');
                const successOverlay = document.getElementById('success-overlay');
                const decodedValue = document.getElementById('decoded-value');
                let active = true;

                // Web Audio synthetic beep synthesizer
                function playBeep() {
                    try {
                        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                        const oscillator = audioCtx.createOscillator();
                        const gainNode = audioCtx.createGain();
                        
                        oscillator.type = 'sine';
                        oscillator.frequency.value = 1400; // Crisp beep sound
                        gainNode.gain.setValueAtTime(0, audioCtx.currentTime);
                        gainNode.gain.linearRampToValueAtTime(0.2, audioCtx.currentTime + 0.04);
                        gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.15);
                        
                        oscillator.connect(gainNode);
                        gainNode.connect(audioCtx.destination);
                        
                        oscillator.start();
                        oscillator.stop(audioCtx.currentTime + 0.15);
                    } catch (e) {
                        console.error("Audio beep generation error: ", e);
                    }
                }

                async function startCamera() {
                    try {
                        statusBadge.innerText = "Requesting Camera...";
                        const stream = await navigator.mediaDevices.getUserMedia({
                            video: { 
                                facingMode: "environment",
                                width: { ideal: 1280 },
                                height: { ideal: 720 }
                            }
                        });
                        video.srcObject = stream;
                        video.addEventListener('loadedmetadata', () => {
                            canvas.width = video.videoWidth;
                            canvas.height = video.videoHeight;
                            statusBadge.innerText = "zbar.wasm ACTIVE SCAN";
                            loader.style.display = "none";
                            tick();
                        });
                    } catch (err) {
                        console.error("Camera access failed", err);
                        statusBadge.innerText = "Camera Access Failed";
                        loader.style.display = "none";
                        if (window.AndroidBridge) {
                            window.AndroidBridge.onError(err.toString());
                        }
                    }
                }

                async function tick() {
                    if (!active) return;
                    if (video.readyState === video.HAVE_ENOUGH_DATA) {
                        context.drawImage(video, 0, 0, canvas.width, canvas.height);
                        try {
                            const symbols = await scanImage(canvas);
                            if (symbols && symbols.length > 0) {
                                const firstSymbol = symbols[0];
                                const barcodeValue = firstSymbol.data;
                                if (barcodeValue && barcodeValue.trim()) {
                                    playBeep();
                                    
                                    // Visual success state
                                    targetBox.classList.add('success');
                                    decodedValue.innerText = barcodeValue;
                                    successOverlay.classList.add('show');
                                    statusBadge.innerText = "DECODED: " + barcodeValue;
                                    
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.onBarcodeDetected(barcodeValue);
                                    }
                                    
                                    // Cool down to prevent multiple quick detections
                                    active = false;
                                    setTimeout(() => {
                                        successOverlay.classList.remove('show');
                                        targetBox.classList.remove('success');
                                        statusBadge.innerText = "zbar.wasm ACTIVE SCAN";
                                        active = true;
                                        tick();
                                    }, 2000);
                                    return;
                                }
                            }
                        } catch (e) {
                            console.error("zbar.wasm decoding exception", e);
                        }
                    }
                    requestAnimationFrame(tick);
                }

                // Initial launch
                startCamera();
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                }
                
                webViewClient = WebViewClient()
                
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        // Grant camera resources requested by JS navigator.mediaDevices.getUserMedia
                        request.grant(request.resources)
                    }
                }

                addJavascriptInterface(WebAppInterface(onBarcodeDetected), "AndroidBridge")
                
                // Load HTML string with secure HTTPS base URL so getUserMedia is permitted
                loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
