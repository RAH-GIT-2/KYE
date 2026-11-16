package com.kye.app.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.domain.Money
import com.kye.app.domain.MonthStory
import com.kye.app.ui.KyeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One full-screen card of the story. */
private data class Slide(
    val colors: List<Long>,
    val emoji: String,
    val kicker: String,
    val big: String,
    val body: String,
    val rows: List<Pair<String, String>> = emptyList()
)

private val GRADIENTS = listOf(
    listOf(0xFF5B4BDB, 0xFFE47BB4),
    listOf(0xFF0B4FB3, 0xFF22B8CF),
    listOf(0xFFB0124F, 0xFFFF9F43),
    listOf(0xFF1F2A5C, 0xFF6C5CE7),
    listOf(0xFF00805F, 0xFF55C9A0),
    listOf(0xFF7A4A08, 0xFFDDA83E),
    listOf(0xFF3D1F6E, 0xFFFD79A8),
    listOf(0xFF1E272E, 0xFF485460)
)

private fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

private fun slidesFor(s: MonthStory): List<Slide> {
    val name = monthName(s.month)
    val out = ArrayList<Slide>()
    if (s.count == 0) {
        out += Slide(GRADIENTS[0], "📖", "Your $name ${s.month.year}", "A quiet month", "Nothing was spent in $name. Add entries and your story fills up.")
        return out.mapIndexed { i, sl -> sl.copy(colors = GRADIENTS[i % GRADIENTS.size]) }
    }
    out += Slide(
        GRADIENTS[0], "📖", "Your $name ${s.month.year}", Money.format(s.spent),
        "spent across ${s.count} ${if (s.count == 1) "entry" else "entries"}"
    )
    s.changePercent?.let { pct ->
        val prevName = monthName(s.month.minusMonths(1))
        out += Slide(
            GRADIENTS[0], if (pct <= 0) "📉" else "📈", "Compared with $prevName",
            when {
                pct < 0 -> "${-pct}% less"
                pct > 0 -> "$pct% more"
                else -> "Just the same"
            },
            "${Money.format(s.prevSpent)} in $prevName → ${Money.format(s.spent)} in $name"
        )
    }
    s.topCategories.firstOrNull()?.let { (cat, emoji, total) ->
        out += Slide(
            GRADIENTS[0], emoji, "Where most of it went", cat,
            "${Money.format(total)} · ${if (s.spent > 0) total * 100 / s.spent else 0}% of your spending",
            s.topCategories.mapIndexed { i, (n, e, t) -> "${i + 1}. $e $n" to Money.format(t) }
        )
    }
    s.biggestDay?.let { day ->
        out += Slide(
            GRADIENTS[0], "📅", "Your biggest day", day.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
            Money.format(s.biggestDayTotal) + (s.biggestDayOn?.let { " · mostly $it" } ?: "")
        )
    }
    if (s.noSpendDays > 0) {
        out += Slide(
            GRADIENTS[0], "🌱", "No-spend days", "${s.noSpendDays} ${if (s.noSpendDays == 1) "day" else "days"}",
            if (s.bestStreak > 1) "Best run: ${s.bestStreak} days in a row" else "Every one of them counts"
        )
    }
    s.favourite?.let { f ->
        out += Slide(GRADIENTS[0], f.emoji, "Your favourite", f.name, "${f.times} times · ${Money.format(f.total)}")
    }
    if (s.saved > 0 || s.income > 0) {
        out += Slide(
            GRADIENTS[0], "💰", if (s.saved > 0) "Put away" else "Came in",
            Money.format(if (s.saved > 0) s.saved else s.income),
            if (s.saved > 0 && s.income > 0) "saved, from ${Money.format(s.income)} income"
            else if (s.saved > 0) "saved this month" else "income this month"
        )
    }
    out += Slide(GRADIENTS[0], "🙏", "That was $name", "See you next month", "Tap Share to send any card")
    return out.mapIndexed { i, sl -> sl.copy(colors = GRADIENTS[i % GRADIENTS.size]) }
}

/**
 * A month as full-screen cards, like a phone "story": bars at the top fill and move on by
 * themselves; tap the right side for the next card, the left side to go back, or swipe.
 * Share sends the card on screen as a picture.
 */
@Composable
fun StoryScreen(vm: KyeViewModel, month: YearMonth, onClose: () -> Unit) {
    val txns by vm.txns.collectAsStateWithLifecycle()
    val story = remember(txns, month) { MonthStory.build(txns, month, LocalDate.now()) }
    val slides = remember(story) { slidesFor(story) }
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()

    // The bar for the card on screen fills over a few seconds, then the next card comes.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pager.currentPage, slides.size) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(6_000, easing = LinearEasing))
        if (pager.currentPage < slides.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Only this part goes into the shared picture.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                // Tap the left side to go back, anywhere else for the next card; swipes go to the pager.
                .pointerInput(slides.size) {
                    detectTapGestures { pos ->
                        val next = if (pos.x < size.width * 0.3f) pager.currentPage - 1 else pager.currentPage + 1
                        if (next in slides.indices) scope.launch { pager.animateScrollToPage(next) }
                    }
                }
        ) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                SlideView(slides[page])
            }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { i ->
                    val fill = when {
                        i < pager.currentPage -> 1f
                        i == pager.currentPage -> progress.value
                        else -> 0f
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KYE story", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                        shareImage(context, bitmap, "KYE-${month}-story.png")
                    }.onFailure { Toast.makeText(context, "Couldn't share: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share")
        }
    }
}

@Composable
private fun SlideView(s: Slide) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(s.colors.map { Color(it) }))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(s.emoji, fontSize = 72.sp)
        Spacer(Modifier.height(20.dp))
        Text(s.kicker, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            s.big, color = Color.White, style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(s.body, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (s.rows.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                s.rows.forEach { (l, r) ->
                    Row {
                        Text(l, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(r, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("KYE · Know Your Expense", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
    }
}

/** Saves the picture in the app's cache and opens the phone's share sheet. */
private suspend fun shareImage(context: Context, bitmap: Bitmap, name: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, name).also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your month").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
