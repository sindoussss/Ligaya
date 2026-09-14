package com.ligaya.designsystem.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import com.ligaya.designsystem.rememberIsReduceMotionEnabled
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Her emotions, matching the EMOTIONS table in assets/mascot/engine.js. Listening and speaking are not
 * emotions — they're overlays started with [LigayaMascotController.startListening]/[LigayaMascotController.startSpeaking]
 * and sit on top of whichever emotion is current.
 */
enum class LigayaEmotion(internal val jsName: String) {
    Neutral("neutral"),
    Happy("happy"),
    Thinking("thinking"),
    Concerned("concerned"),
    Surprised("surprised"),
    Attentive("attentive"),
    Reassuring("reassuring"),
    Emergency("emergency"),
    Resolved("resolved"),
}

/** System follows the device's reduce-motion setting, Calm keeps idle motion small, Still removes it. */
enum class LigayaMotionMode(internal val jsName: String) {
    System("system"),
    Calm("calm"),
    Still("still"),
}

/** A held head turn. The artwork is flat, so the three-quarter views are a few degrees of turn, not a true 3/4. */
enum class LigayaCameraView(internal val jsName: String) {
    Front("front"),
    LeftThreeQuarter("left_3_4"),
    RightThreeQuarter("right_3_4"),
}

/** How she's framed in her container, in the artwork's 1008x740 canvas. Mirrors FRAMES in engine.js. */
enum class LigayaFrame(internal val jsName: String, val x: Float, val y: Float, val w: Float, val h: Float, val cover: Boolean) {
    Bust("bust", 150f, 22f, 720f, 718f, cover = false),
    Portrait("portrait", 250f, 30f, 520f, 560f, cover = false),
    Head("head", 350f, 110f, 330f, 330f, cover = true),
}

data class LigayaMascotState(
    val emotion: LigayaEmotion,
    val speaking: Boolean,
    val listening: Boolean,
    val camera: LigayaCameraView,
)

private const val TAG = "LigayaMascot"
private const val PAGE_URL = "https://appassets.androidplatform.net/assets/mascot/mascot.html"
private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Drives one [LigayaMascot]. It holds the *desired* state, not just a pipe to the page: every call updates
 * that state and is forwarded if the engine is running, and whenever a WebView's engine starts (first
 * load, or the WebView being recreated after a configuration change or navigation) the whole state is
 * replayed onto it. Calls made before the page has loaded therefore still take effect.
 *
 * Main thread only. Safe to call repeatedly; unchanged calls are no-ops in the engine.
 */
@Stable
class LigayaMascotController internal constructor() {
    private var webView: WebView? = null
    private var engineRunning = false

    private var emotion = LigayaEmotion.Neutral
    private var listening = false
    private var speaking = false
    private var camera = LigayaCameraView.Front
    private var motion = LigayaMotionMode.System
    private var frame = LigayaFrame.Bust

    private val stateHolder = mutableStateOf<LigayaMascotState?>(null)
    private val readyHolder = mutableStateOf(false)

    /** Last state the engine reported, or null before it has started. */
    val state: State<LigayaMascotState?> get() = stateHolder

    /** True once her layered artwork has decoded and is painting. */
    val isReady: State<Boolean> get() = readyHolder

    fun setEmotion(value: LigayaEmotion) {
        emotion = value
        send("setEmotion('${value.jsName}')")
    }

    fun startListening() {
        listening = true
        speaking = false
        send("startListening()")
    }

    fun stopListening() {
        listening = false
        send("stopListening()")
    }

    fun startSpeaking() {
        speaking = true
        listening = false
        send("startSpeaking()")
    }

    fun stopSpeaking() {
        speaking = false
        send("stopSpeaking()")
    }

    /** One blink now. Transient: not replayed onto a recreated WebView. */
    fun blink() = send("blink()")

    /** A brief genuine smile, then back to the current emotion. Transient. */
    fun smile() = send("smile()")

    fun setCameraView(value: LigayaCameraView) {
        camera = value
        send("setCameraView('${value.jsName}')")
    }

    fun resetCamera() = setCameraView(LigayaCameraView.Front)

    fun reset() {
        emotion = LigayaEmotion.Neutral
        listening = false
        speaking = false
        camera = LigayaCameraView.Front
        send("reset()")
    }

    internal fun setMotion(value: LigayaMotionMode) {
        motion = value
        send("setMotion('${value.jsName}')")
    }

    internal fun setFrame(value: LigayaFrame) {
        if (frame == value) return
        frame = value
        send("setFrame('${value.jsName}', { animate: true })")
    }

    internal val currentFrame: LigayaFrame get() = frame

    internal fun viewForLifecycle(): WebView? = webView

    internal fun attach(view: WebView) {
        if (webView !== view) {
            webView = view
            engineRunning = false
        }
    }

    internal fun detach(view: WebView) {
        if (webView === view) {
            webView = null
            engineRunning = false
            readyHolder.value = false
        }
    }

    internal fun onEngineCreated(view: WebView) {
        if (webView !== view) return
        engineRunning = true
        Log.d(TAG, "engine created")
        val script = buildString {
            append("(function(l){l.reset();")
            append("l.setMotion('${motion.jsName}');")
            append("l.setFrame('${frame.jsName}');")
            append("l.setEmotion('${emotion.jsName}');")
            append("l.setCameraView('${camera.jsName}');")
            if (listening) append("l.startListening();")
            if (speaking) append("l.startSpeaking();")
            append("})(window.ligaya);")
        }
        view.evaluateJavascript(script, null)
    }

    internal fun onReady(view: WebView) {
        if (webView === view) readyHolder.value = true
    }

    internal fun onStateEvent(view: WebView, json: String) {
        if (webView !== view) return
        val obj = JSONObject(json)
        stateHolder.value = LigayaMascotState(
            emotion = LigayaEmotion.entries.firstOrNull { it.jsName == obj.optString("emotion") } ?: LigayaEmotion.Neutral,
            speaking = obj.optBoolean("speaking"),
            listening = obj.optBoolean("listening"),
            camera = LigayaCameraView.entries.firstOrNull { it.jsName == obj.optString("camera") } ?: LigayaCameraView.Front,
        )
    }

    private fun send(call: String) {
        if (engineRunning) webView?.evaluateJavascript("window.ligaya.$call;", null)
    }
}

@Composable
fun rememberLigayaMascotController(): LigayaMascotController = remember { LigayaMascotController() }

/**
 * Ligaya herself: the layered mascot rig (assets/mascot/engine.js) running in a WebView, served over
 * `https://appassets.androidplatform.net/` by [WebViewAssetLoader] so its ES module and assets load from
 * a real origin.
 *
 * Until the rig has decoded, a flattened poster of her is drawn natively with the same framing and bottom
 * fade, so she's visible on the very first frame — including when the WebView is recreated — instead of
 * popping in. The WebView is purely visual: it never takes touches (a scrollable parent still scrolls
 * through it), focus or accessibility focus, and forced dark mode is disabled so her colours are never
 * inverted. Describe her state in text elsewhere on the screen; she's decorative to accessibility services.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LigayaMascot(
    controller: LigayaMascotController,
    modifier: Modifier = Modifier,
    frame: LigayaFrame = LigayaFrame.Bust,
    motion: LigayaMotionMode = LigayaMotionMode.System,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reduceMotion = rememberIsReduceMotionEnabled()
    val poster = remember { MascotPoster.get(context) }
    val ready by controller.isReady

    // The page's canvas fades in over 300ms once ready; the native poster only starts leaving after that,
    // so there's never a frame without her.
    val posterAlpha by animateFloatAsState(if (ready) 0f else 1f, tween(durationMillis = 300, delayMillis = 350), label = "mascotPoster")

    Box(modifier.clearAndSetSemantics { }) {
        if (poster != null && posterAlpha > 0f) {
            Canvas(Modifier.fillMaxSize().alpha(posterAlpha).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                drawPoster(poster, controller.currentFrame)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> createMascotWebView(ctx, controller) },
            update = { view -> controller.attach(view) },
            onRelease = { view ->
                controller.detach(view)
                view.removeJavascriptInterface(BRIDGE_NAME)
                view.stopLoading()
                view.destroy()
            },
        )
    }

    LaunchedEffect(frame) { controller.setFrame(frame) }
    LaunchedEffect(motion, reduceMotion) {
        controller.setMotion(if (reduceMotion) LigayaMotionMode.Still else motion)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.pauseView()
                Lifecycle.Event.ON_RESUME -> controller.resumeView()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private const val BRIDGE_NAME = "LigayaMascotBridge"

private fun LigayaMascotController.pauseView() = viewForLifecycle()?.onPause()
private fun LigayaMascotController.resumeView() = viewForLifecycle()?.onResume()

@SuppressLint("SetJavaScriptEnabled")
private fun createMascotWebView(ctx: Context, controller: LigayaMascotController): WebView {
    if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
        .build()
    val view = MascotWebView(ctx)
    // AndroidView defaults to WRAP_CONTENT, and a wrap-content WebView sizes its layout viewport to the
    // page's content height — 0 here, since every layer is absolutely positioned — so nothing painted.
    view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    view.setBackgroundColor(Color.TRANSPARENT)
    view.isFocusable = false
    view.isFocusableInTouchMode = false
    view.isLongClickable = false
    view.isHapticFeedbackEnabled = false
    view.isVerticalScrollBarEnabled = false
    view.isHorizontalScrollBarEnabled = false
    view.overScrollMode = View.OVER_SCROLL_NEVER
    view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    with(view.settings) {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, false)
    }
    view.webViewClient = object : WebViewClientCompat() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)
    }
    view.addJavascriptInterface(MascotBridge(controller, view), BRIDGE_NAME)
    controller.attach(view)
    view.loadUrl(PAGE_URL)
    return view
}

/** Never consumes touches, so taps and scroll gestures reach whatever is around or behind her. */
private class MascotWebView(context: Context) : WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}

// JavaScript bridge calls arrive on a WebView background thread; everything is handed to the main thread.
private class MascotBridge(private val controller: LigayaMascotController, private val view: WebView) {
    private val debug = view.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @JavascriptInterface
    fun isDebug(): Boolean = debug

    @JavascriptInterface
    fun onEngineCreated() = mainHandler.post { controller.onEngineCreated(view) }.let { }

    @JavascriptInterface
    fun onReady() = mainHandler.post { controller.onReady(view) }.let { }

    @JavascriptInterface
    fun onState(json: String) = mainHandler.post { controller.onStateEvent(view, json) }.let { }
}

private object MascotPoster {
    @Volatile private var cached: ImageBitmap? = null

    fun get(context: Context): ImageBitmap? = cached ?: runCatching {
        context.assets.open("mascot/layers/poster.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }.onFailure { Log.w(TAG, "poster unavailable", it) }.getOrNull()?.also { cached = it }
}

/**
 * Same placement as engine.js layout(): contain framing sits 40% down the spare height, cover keeps the
 * top in view; the artwork's hard bottom edge (canvas y=706) is faded out over 16% of the height.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPoster(poster: ImageBitmap, frame: LigayaFrame) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val contain = minOf(w / frame.w, h / frame.h)
    val cover = maxOf(w / frame.w, h / frame.h)
    val scale = if (frame.cover) cover else contain
    val ty = if (frame.cover) -frame.y * cover else (h - frame.h * contain) * 0.4f - frame.y * contain
    val tx = w / 2f - (frame.x + frame.w / 2f) * scale
    val canvasToPoster = 1008f / poster.width
    translate(tx, ty) {
        scale(scale * canvasToPoster, pivot = Offset.Zero) {
            drawImage(poster)
        }
    }
    val cut = minOf(h, 706f * scale + ty)
    val fadeStart = cut - h * 0.16f
    drawRect(
        brush = Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent, startY = fadeStart, endY = cut),
        blendMode = BlendMode.DstIn,
    )
}
