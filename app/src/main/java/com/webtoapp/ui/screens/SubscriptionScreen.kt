package com.webtoapp.ui.screens

import android.app.Activity
import com.webtoapp.ui.components.PremiumButton
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webtoapp.core.billing.*
import com.webtoapp.core.i18n.Strings
import kotlinx.coroutines.launch
import com.webtoapp.ui.components.ThemedBackgroundBox
import com.webtoapp.ui.components.EnhancedElevatedCard

/** Safely find the Activity from a Context, traversing ContextWrapper chain. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 订阅购买页面
 *
 * 展示 Free、Pro、Ultra 三个层级的订阅方案。
 * 支持月度、年度和终身套餐切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    billingManager: BillingManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val isConnected by billingManager.isConnected.collectAsStateWithLifecycle()
    val products by billingManager.products.collectAsStateWithLifecycle()
    val purchaseState by billingManager.purchaseState.collectAsStateWithLifecycle()
    val currentSub by billingManager.currentSubscription.collectAsStateWithLifecycle()

    // 0=月度, 1=年度, 2=终身
    var selectedPeriod by remember { mutableIntStateOf(1) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 处理购买结果
    LaunchedEffect(purchaseState) {
        when (val state = purchaseState) {
            is PurchaseState.Success -> {
                snackbarHostState.showSnackbar("${state.plan.displayName} 订阅成功！")
                billingManager.resetPurchaseState()
            }
            is PurchaseState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                billingManager.resetPurchaseState()
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("选择套餐") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ThemedBackgroundBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 标题
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "解锁全部功能",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "选择适合你的订阅方案",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 周期切换: 月度 / 年度 / 终身
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            PeriodChip(
                                text = "月度",
                                selected = selectedPeriod == 0,
                                onClick = { selectedPeriod = 0 }
                            )
                            PeriodChip(
                                text = "年度 (省20%)",
                                selected = selectedPeriod == 1,
                                onClick = { selectedPeriod = 1 }
                            )
                            PeriodChip(
                                text = "终身",
                                selected = selectedPeriod == 2,
                                onClick = { selectedPeriod = 2 }
                            )
                        }
                    }
                }
            }

            // 当前订阅状态
            currentSub?.let { sub ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "当前方案: ${sub.plan.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (sub.autoRenewing) "自动续费中" else "不续费",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // ═══ Free 方案 ═══
            item {
                FreeCard()
            }

            // ═══ Pro 方案 ═══
            item {
                when (selectedPeriod) {
                    0 -> {
                        val plan = SubscriptionPlan.PRO_MONTHLY
                        val price = billingManager.getFormattedPrice(plan) ?: "$3"
                        val isCurrent = currentSub?.plan?.tierName == "pro"
                        SubscriptionCard(
                            tierName = "Pro",
                            price = price,
                            period = "/月",
                            gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                            features = proFeatures(),
                            isCurrent = isCurrent,
                            isLoading = purchaseState is PurchaseState.Loading,
                            onSubscribe = { if (!isCurrent && activity != null) billingManager.launchPurchase(activity, plan) }
                        )
                    }
                    1 -> {
                        val plan = SubscriptionPlan.PRO_YEARLY
                        val price = billingManager.getFormattedPrice(plan) ?: "$28.80"
                        val isCurrent = currentSub?.plan?.tierName == "pro"
                        SubscriptionCard(
                            tierName = "Pro",
                            price = price,
                            period = "/年",
                            gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                            features = proFeatures(),
                            isCurrent = isCurrent,
                            isLoading = purchaseState is PurchaseState.Loading,
                            onSubscribe = { if (!isCurrent && activity != null) billingManager.launchPurchase(activity, plan) }
                        )
                    }
                    2 -> {
                        // Pro 终身
                        SubscriptionCard(
                            tierName = "Pro",
                            price = "$99",
                            period = " 一次性",
                            gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                            features = proFeatures() + listOf(
                                FeatureItem(Icons.Outlined.AllInclusive, "永不过期", "一次购买，终身使用")
                            ),
                            isCurrent = false,
                            isLoading = purchaseState is PurchaseState.Loading,
                            isLifetime = true,
                            onSubscribe = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("请使用激活码兑换终身套餐")
                                }
                            }
                        )
                    }
                }
            }

            // ═══ Ultra 方案 ═══
            item {
                when (selectedPeriod) {
                    0 -> {
                        val plan = SubscriptionPlan.ULTRA_MONTHLY
                        val price = billingManager.getFormattedPrice(plan) ?: "$9"
                        val isCurrent = currentSub?.plan?.tierName == "ultra"
                        SubscriptionCard(
                            tierName = "Ultra",
                            price = price,
                            period = "/月",
                            gradient = listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
                            isRecommended = true,
                            features = ultraFeatures(),
                            isCurrent = isCurrent,
                            isLoading = purchaseState is PurchaseState.Loading,
                            onSubscribe = { if (!isCurrent && activity != null) billingManager.launchPurchase(activity, plan) }
                        )
                    }
                    1 -> {
                        val plan = SubscriptionPlan.ULTRA_YEARLY
                        val price = billingManager.getFormattedPrice(plan) ?: "$86.40"
                        val isCurrent = currentSub?.plan?.tierName == "ultra"
                        SubscriptionCard(
                            tierName = "Ultra",
                            price = price,
                            period = "/年",
                            gradient = listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
                            isRecommended = true,
                            features = ultraFeatures(),
                            isCurrent = isCurrent,
                            isLoading = purchaseState is PurchaseState.Loading,
                            onSubscribe = { if (!isCurrent && activity != null) billingManager.launchPurchase(activity, plan) }
                        )
                    }
                    2 -> {
                        // Ultra 终身
                        SubscriptionCard(
                            tierName = "Ultra",
                            price = "$199",
                            period = " 一次性",
                            gradient = listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
                            isRecommended = true,
                            features = ultraFeatures() + listOf(
                                FeatureItem(Icons.Outlined.AllInclusive, "永不过期", "一次购买，终身使用")
                            ),
                            isCurrent = false,
                            isLoading = purchaseState is PurchaseState.Loading,
                            isLifetime = true,
                            upgradeNote = "Pro 终身用户仅需补差价 \$100",
                            onSubscribe = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("请使用激活码兑换终身套餐")
                                }
                            }
                        )
                    }
                }
            }

            // 恢复购买
            item {
                TextButton(
                    onClick = {
                        scope.launch {
                            billingManager.queryCurrentSubscription()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("恢复购买", textDecoration = TextDecoration.Underline)
                }
            }

            // 说明
            item {
                Text(
                    "月度/年度订阅通过 Google Play 自动扣费，可随时取消。终身套餐通过激活码兑换，一次购买永久有效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
        }
}

// ─── 功能列表 ───

private fun proFeatures() = listOf(
    FeatureItem(Icons.Outlined.Cloud, "云端项目管理", "最多 5 个项目"),
    FeatureItem(Icons.Outlined.VpnKey, "激活码系统", "最多 1000 个激活码"),
    FeatureItem(Icons.Outlined.Update, "版本自动更新", "GitHub / Gitee / R2"),
    FeatureItem(Icons.Outlined.Campaign, "应用公告推送", "最多 10 条活跃公告"),
    FeatureItem(Icons.Outlined.Settings, "远程配置", "无限 KV 配置项"),
    FeatureItem(Icons.Outlined.Webhook, "Webhook", "事件通知集成"),
    FeatureItem(Icons.Outlined.Analytics, "数据分析", "安装/打开/活跃/崩溃"),
)

private fun ultraFeatures() = listOf(
    FeatureItem(Icons.Filled.Star, "包含全部 Pro 功能", ""),
    FeatureItem(Icons.Outlined.Notifications, "FCM 推送通知", "实时触达用户"),
    FeatureItem(Icons.Outlined.VpnKey, "激活码上限提升", "最多 5000 个激活码"),
    FeatureItem(Icons.Outlined.Campaign, "公告上限提升", "最多 30 条活跃公告"),
    FeatureItem(Icons.Outlined.Cloud, "项目上限提升", "最多 10 个项目"),
    FeatureItem(Icons.Outlined.Storage, "R2 云存储", "Cloudflare CDN 加速"),
    FeatureItem(Icons.Outlined.Speed, "优先技术支持", "48 小时内响应"),
)

// ─── 组件 ───

private data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

@Composable
private fun PeriodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

/**
 * Free 套餐卡片 — 展示免费版功能
 */
@Composable
private fun FreeCard() {
    EnhancedElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            // 头部
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        "Free",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$0",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 36.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "永久免费",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            // 功能列表
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val freeFeatures = listOf(
                    FeatureItem(Icons.Outlined.PhoneAndroid, "无限创建应用", "Web / 媒体 / HTML / 前端"),
                    FeatureItem(Icons.Outlined.Build, "APK 本地构建", "自定义包名、签名"),
                    FeatureItem(Icons.Outlined.Extension, "扩展模块系统", "自定义功能模块"),
                    FeatureItem(Icons.Outlined.Storefront, "应用&模块市场", "浏览和安装社区内容"),
                    FeatureItem(Icons.Outlined.SmartToy, "AI 编程助手", "HTML / 前端 / Node.js"),
                )
                freeFeatures.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            feature.icon, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                feature.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (feature.subtitle.isNotEmpty()) {
                                Text(
                                    feature.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = false
                ) {
                    Text(
                        "当前方案",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    tierName: String,
    price: String,
    period: String,
    gradient: List<Color>,
    features: List<FeatureItem>,
    isCurrent: Boolean,
    isLoading: Boolean,
    isRecommended: Boolean = false,
    isLifetime: Boolean = false,
    upgradeNote: String? = null,
    onSubscribe: () -> Unit
) {
    EnhancedElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = if (isRecommended) {
            Modifier.border(2.dp, Brush.linearGradient(gradient), RoundedCornerShape(20.dp))
        } else Modifier
    ) {
        Column {
            // 头部（渐变）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(gradient))
                    .padding(24.dp)
            ) {
                Column {
                    if (isRecommended) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.25f)
                        ) {
                            Text(
                                "推荐",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tierName,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLifetime) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "∞ 终身",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            price,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 36.sp
                        )
                        if (period.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                period,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    // 升级提示
                    if (upgradeNote != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    upgradeNote,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 功能列表
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            feature.icon, null,
                            modifier = Modifier.size(20.dp),
                            tint = gradient.first()
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                feature.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (feature.subtitle.isNotEmpty()) {
                                Text(
                                    feature.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 订阅按钮
                PremiumButton(
                    onClick = onSubscribe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isCurrent && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = gradient.first()
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            when {
                                isCurrent -> "当前方案"
                                isLifetime -> "使用激活码兑换"
                                else -> "订阅 $tierName"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
