package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.weblite.webview.EmbeddedWebView
import ca.weblite.webview.swing.WebViewComponent
import ca.weblite.webview.swing.WebViewHeavyweightComponent
import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthState
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.MicrosoftLogo
import io.ezz.launcher.ui.components.MinecraftSkinHead
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.awt.Canvas
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

@Composable
fun MicrosoftAuthModal(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val authState by viewModel.microsoftAuthState.collectAsState()
    val isBrowserMode = authState is MicrosoftAuthState.EmbeddedBrowser

    // Auto-dismiss after user confirms continue or after success duration
    LaunchedEffect(authState) {
        if (authState is MicrosoftAuthState.Cancelled) {
            delay(1200)
            onDismiss()
        }
    }

    // Modal Dim Backdrop & Centered Responsive Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Keep clicks on backdrop from propagating */ }
            )
            .onKeyEvent { event ->
                if (event.key == Key.Escape) {
                    viewModel.cancelMicrosoftLogin()
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val availableW = maxWidth
            val availableH = maxHeight

            val modalWidth = if (isBrowserMode) {
                (availableW * 0.74f).coerceIn(760.dp, 880.dp).coerceAtMost(availableW - 32.dp)
            } else {
                540.dp.coerceAtMost(availableW - 32.dp)
            }

            val modalHeight = if (isBrowserMode) {
                (availableH * 0.88f).coerceIn(650.dp, 750.dp).coerceAtMost(availableH - 32.dp)
            } else {
                Dp.Unspecified
            }

            Box(
                modifier = Modifier
                    .width(modalWidth)
                    .then(
                        if (modalHeight != Dp.Unspecified) Modifier.height(modalHeight)
                        else Modifier
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D1117))
                    .border(1.dp, Color(0xFF21262D), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ==========================================
                    // 1. MODAL HEADER
                    // ==========================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MicrosoftLogo(size = 20.dp)
                            Column {
                                Text(
                                    text = "Sign in to Ezz Launcher",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sign in with your Microsoft account to connect your Minecraft Java Edition profile.",
                                    color = Color(0xFF8B949E),
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // Close button [X]
                        val closeInteraction = remember { MutableInteractionSource() }
                        val isCloseHovered by closeInteraction.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCloseHovered) Color(0xFF21262D) else Color(0xFF161B22))
                                .clickable(
                                    interactionSource = closeInteraction,
                                    indication = null,
                                    onClick = {
                                        viewModel.cancelMicrosoftLogin()
                                        onDismiss()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isCloseHovered) Color.White else Color(0xFF8B949E),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Divider below header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF21262D))
                    )

                    // ==========================================
                    // 2. MODAL BODY (CONTENT / WEBVIEW)
                    // ==========================================
                    if (isBrowserMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF0D1117)),
                            contentAlignment = Alignment.Center
                        ) {
                            EmbeddedBrowserStateView(
                                authUrl = (authState as MicrosoftAuthState.EmbeddedBrowser).authUrl
                            )
                        }

                        // Divider above footer
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF21262D))
                        )

                        // ==========================================
                        // 3. MODAL FOOTER (CANCEL BUTTON)
                        // ==========================================
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EzzButton(
                                text = "Cancel",
                                onClick = {
                                    viewModel.cancelMicrosoftLogin()
                                    onDismiss()
                                },
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    } else {
                        // Other non-browser authentication states (Idle, Connecting, Authenticating, etc.)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = authState,
                                transitionSpec = {
                                    fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                                },
                                label = "MicrosoftAuthStateTransition"
                            ) { state ->
                                when (state) {
                                    is MicrosoftAuthState.Idle -> {
                                        IdleStateView(onStart = { viewModel.startMicrosoftLogin() })
                                    }

                                    // STATE 1: Microsoft Sign-In (Connecting securely)
                                    is MicrosoftAuthState.OpeningBrowser,
                                    is MicrosoftAuthState.ConnectingToMicrosoft -> {
                                        ProgressStateView(
                                            stepLabel = "STATE 1 OF 5",
                                            title = "Microsoft Sign-In",
                                            subtitle = "Connecting securely to Microsoft...",
                                            onCancel = { viewModel.cancelMicrosoftLogin() }
                                        )
                                    }

                                    // STATE 2: Handled above in if (isBrowserMode)
                                    is MicrosoftAuthState.EmbeddedBrowser -> {
                                        // Handled in dedicated browser mode above
                                    }

                                    // STATE 3: Authenticating (Verifying your Microsoft account)
                                    is MicrosoftAuthState.CompletingMicrosoftAuth,
                                    is MicrosoftAuthState.SigningIn -> {
                                        ProgressStateView(
                                            stepLabel = "STATE 3 OF 5",
                                            title = "Authenticating",
                                            subtitle = "Verifying your Microsoft account...",
                                            onCancel = { viewModel.cancelMicrosoftLogin() }
                                        )
                                    }

                                    // STATE 4: Minecraft Authentication (Connecting to Xbox Live)
                                    is MicrosoftAuthState.ConnectingToXboxLive -> {
                                        ProgressStateView(
                                            stepLabel = "STATE 4 OF 5",
                                            title = "Minecraft Authentication",
                                            subtitle = "Connecting to Xbox Live...",
                                            onCancel = { viewModel.cancelMicrosoftLogin() }
                                        )
                                    }

                                    // STATE 5: Minecraft Authentication (Connecting to Minecraft Services)
                                    is MicrosoftAuthState.ConnectingToMinecraftServices,
                                    is MicrosoftAuthState.CompletingMinecraftAuth,
                                    is MicrosoftAuthState.MinecraftProfileLoading -> {
                                        ProgressStateView(
                                            stepLabel = "STATE 5 OF 5",
                                            title = "Minecraft Authentication",
                                            subtitle = "Connecting to Minecraft Services...",
                                            onCancel = { viewModel.cancelMicrosoftLogin() }
                                        )
                                    }

                                    // STATE 6: SUCCESS (You're signed in!)
                                    is MicrosoftAuthState.Success,
                                    is MicrosoftAuthState.Ready -> {
                                        val account = if (state is MicrosoftAuthState.Success) state.account else (state as MicrosoftAuthState.Ready).account
                                        SuccessStateView(
                                            account = account,
                                            skinManager = viewModel.skinService,
                                            onContinue = onDismiss
                                        )
                                    }

                                    // CANCELLED STATE
                                    is MicrosoftAuthState.Cancelled -> {
                                        CancelledStateView(onClose = onDismiss)
                                    }

                                    // ERROR STATE (User-friendly, no raw exceptions)
                                    is MicrosoftAuthState.Failed -> {
                                        ErrorStateView(
                                            state = state,
                                            onRetry = { viewModel.startMicrosoftLogin() },
                                            onCancel = onDismiss
                                        )
                                    }

                                    else -> {
                                        ProgressStateView(
                                            stepLabel = "PROCESSING",
                                            title = "Microsoft Sign-In",
                                            subtitle = "Processing your sign-in...",
                                            onCancel = { viewModel.cancelMicrosoftLogin() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper to host and synchronize native Edge WebView2 with Java Swing and Compose Desktop
 * on Windows with HiDPI display scaling (e.g. 125%, 150%, 200%).
 */
private class HiDpiWebViewManager(
    val webView: WebViewComponent
) {
    private val embeddedField by lazy {
        try {
            WebViewHeavyweightComponent::class.java.getDeclaredField("embedded").apply {
                isAccessible = true
            }
        } catch (_: Throwable) {
            null
        }
    }

    private val canvasField by lazy {
        try {
            WebViewHeavyweightComponent::class.java.getDeclaredField("canvas").apply {
                isAccessible = true
            }
        } catch (_: Throwable) {
            null
        }
    }

    val canvas: Canvas? by lazy {
        try {
            (canvasField?.get(webView) as? Canvas)
                ?: (webView.components.firstOrNull { it is Canvas } as? Canvas)
        } catch (_: Throwable) {
            webView.components.firstOrNull { it is Canvas } as? Canvas
        }
    }

    private fun getEmbedded(): EmbeddedWebView? {
        return try {
            embeddedField?.get(webView) as? EmbeddedWebView
        } catch (_: Throwable) {
            null
        }
    }

    init {
        canvas?.let { c ->
            // Prevent white flash by setting the canvas background to dark charcoal
            c.background = java.awt.Color(0x0D, 0x11, 0x17)

            // Remove the default unscaled component listener from EmbeddedCanvas
            for (listener in c.componentListeners) {
                c.removeComponentListener(listener)
            }

            // Install DPI-aware component listener
            c.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    syncNativeBounds()
                }
                override fun componentMoved(e: ComponentEvent?) {
                    syncNativeBounds()
                }
            })

            // Listen for hierarchy changes (when attached to window)
            c.addHierarchyListener { event ->
                if ((event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L ||
                    (event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) != 0L
                ) {
                    SwingUtilities.invokeLater { syncNativeBounds() }
                }
            }

            // Listen for ancestor movements/resizes
            c.addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
                override fun ancestorResized(e: HierarchyEvent?) {
                    syncNativeBounds()
                }
                override fun ancestorMoved(e: HierarchyEvent?) {
                    syncNativeBounds()
                }
            })
        }
    }

    fun syncNativeBounds() {
        val c = canvas ?: return
        if (c.width <= 0 || c.height <= 0) return
        val embedded = getEmbedded() ?: return
        val window = SwingUtilities.getWindowAncestor(c) ?: return

        try {
            val point = SwingUtilities.convertPoint(c, 0, 0, window)
            val insets = window.insets ?: Insets(0, 0, 0, 0)
            val logicalX = point.x - insets.left
            val logicalY = point.y - insets.top
            val logicalW = c.width
            val logicalH = c.height

            // Retrieve the active HiDPI scaling transform for this monitor/window
            val transform = c.graphicsConfiguration?.defaultTransform
                ?: window.graphicsConfiguration?.defaultTransform
            val scaleX = transform?.scaleX ?: 1.0
            val scaleY = transform?.scaleY ?: 1.0

            val physicalX = (logicalX * scaleX).roundToInt()
            val physicalY = (logicalY * scaleY).roundToInt()
            val physicalW = (logicalW * scaleX).roundToInt()
            val physicalH = (logicalH * scaleY).roundToInt()

            embedded.setBounds(physicalX, physicalY, physicalW, physicalH)
        } catch (_: Throwable) {
            // Ignore race conditions during window teardown
        }
    }
}

/**
 * Initial Idle view inside modal before sign-in begins.
 */
@Composable
private fun IdleStateView(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            MicrosoftLogo(size = 30.dp)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Connect Your Microsoft Account",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "A Microsoft account with a Minecraft Java Edition license is required to play on official multiplayer servers.",
                color = Color(0xFF8B949E),
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        EzzButton(
            text = "Continue to Sign In",
            onClick = onStart,
            variant = EzzButtonVariant.PRIMARY,
            size = EzzButtonSize.LARGE,
            modifier = Modifier.fillMaxWidth(0.75f)
        )
    }
}

/**
 * Progress view used across authentication steps with clean typography and animated spinner.
 */
@Composable
private fun ProgressStateView(
    stepLabel: String,
    title: String,
    subtitle: String,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 26.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0xFF30363D), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stepLabel,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF8B949E),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        EzzButton(
            text = "Cancel",
            onClick = onCancel,
            variant = EzzButtonVariant.SECONDARY,
            size = EzzButtonSize.SMALL
        )
    }
}

/**
 * STATE 2: In-Launcher Embedded Browser (WebView2) with HiDPI bounds scaling
 */
@Composable
private fun EmbeddedBrowserStateView(
    authUrl: String
) {
    var isPageReady by remember { mutableStateOf(false) }
    var webViewManager by remember { mutableStateOf<HiDpiWebViewManager?>(null) }

    LaunchedEffect(authUrl) {
        // Allow WebView2 to initialize and paint its initial frame before revealing
        delay(450)
        isPageReady = true
    }

    LaunchedEffect(webViewManager) {
        val mgr = webViewManager ?: return@LaunchedEffect
        // Verification loop to ensure exact physical bounds are synced across lifecycle
        val verificationDelays = longArrayOf(30, 80, 150, 250, 450, 700)
        for (d in verificationDelays) {
            delay(d)
            mgr.syncNativeBounds()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val webView = WebViewComponent.create()
                val mgr = HiDpiWebViewManager(webView)
                webViewManager = mgr
                webView.url = authUrl
                webView
            },
            update = { webView ->
                if (webView.url != authUrl) {
                    webView.url = authUrl
                }
                webViewManager?.syncNativeBounds()
            }
        )

        // Loading state overlay until page is ready - prevents white flash or empty rectangle
        androidx.compose.animation.AnimatedVisibility(
            visible = !isPageReady,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF161B22))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        MicrosoftLogo(size = 28.dp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Microsoft",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Loading secure sign-in...",
                            color = Color(0xFF8B949E),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * STATE 6: SUCCESS (You're signed in!)
 */
@Composable
private fun SuccessStateView(
    account: io.ezz.launcher.core.model.account.Account,
    skinManager: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF052E16))
                .border(1.dp, Color(0xFF10B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF34D399),
                modifier = Modifier.size(30.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "SUCCESS",
                color = Color(0xFF10B981),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "You're signed in!",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Account Profile Card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MinecraftSkinHead(
                    account = account,
                    skinManager = skinManager,
                    size = 48.dp
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = account.username,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Microsoft Account",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "•",
                            color = Color(0xFF475569),
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Minecraft Java Edition",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        EzzButton(
            text = "Continue",
            onClick = onContinue,
            variant = EzzButtonVariant.PRIMARY,
            size = EzzButtonSize.MEDIUM,
            modifier = Modifier.fillMaxWidth(0.85f)
        )
    }
}

/**
 * Clean cancelled state when user dismisses the sign-in prompt.
 */
@Composable
private fun CancelledStateView(onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0xFF30363D), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sign-in cancelled",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "No account was added.",
                color = Color(0xFF8B949E),
                fontSize = 12.5.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        EzzButton(
            text = "Close",
            onClick = onClose,
            variant = EzzButtonVariant.SECONDARY,
            size = EzzButtonSize.SMALL
        )
    }
}

/**
 * Clean, user-friendly error state with no raw exceptions in normal UI.
 */
@Composable
private fun ErrorStateView(
    state: MicrosoftAuthState.Failed,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val messageLower = state.message.lowercase()
    val isNetwork = messageLower.contains("network") ||
            messageLower.contains("connect") ||
            messageLower.contains("timeout") ||
            messageLower.contains("unreachable") ||
            messageLower.contains("host")

    val userMessage = if (isNetwork) {
        "Check your internet connection and try again."
    } else {
        "We couldn't complete the Microsoft sign-in."
    }

    var showDiagnostics by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B1219))
                .border(1.dp, Color(0xFFDC2626), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isNetwork) Icons.Default.WifiOff else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFF87171),
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Microsoft sign-in failed",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = userMessage,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        val techDetails = state.technicalDetails
        if (!techDetails.isNullOrBlank()) {
            Text(
                text = if (showDiagnostics) "Hide details" else "Diagnostic details",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { showDiagnostics = !showDiagnostics }
                    .padding(4.dp)
            )

            if (showDiagnostics) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090B0E))
                        .border(1.dp, Color(0xFF1E232F), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = techDetails,
                        color = Color(0xFF8B949E),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            EzzButton(
                text = "Cancel",
                onClick = onCancel,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1f)
            )

            if (state.canRetry) {
                EzzButton(
                    text = "Try Again",
                    onClick = onRetry,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
