package com.kye.app.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kye.app.data.Receipts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes a stored receipt, shrunk to about [maxSide] px, off the main thread. */
@Composable
private fun rememberReceipt(fileName: String, maxSide: Int): Bitmap? {
    val context = LocalContext.current
    val state by produceState<Bitmap?>(null, fileName, maxSide) {
        value = withContext(Dispatchers.IO) {
            val f: File = Receipts.file(context, fileName)
            if (!f.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    return state
}

/** A receipt shown with its id so it can be deleted; [id] is null for photos not saved yet. */
data class ReceiptItem(val id: Long?, val fileName: String)

/**
 * "Bill photos": thumbnails plus Camera / Gallery buttons. Tapping a thumbnail opens it full
 * screen (zoom, share, delete).
 */
@Composable
fun ReceiptStrip(
    items: List<ReceiptItem>,
    busy: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDelete: (ReceiptItem) -> Unit
) {
    var viewing by remember { mutableStateOf<ReceiptItem?>(null) }
    Column {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val bmp = rememberReceipt(item.fileName, 240)
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { viewing = item },
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), contentDescription = "Bill photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else Text("📄")
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            OutlinedButton(onClick = onCamera, enabled = !busy) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Camera")
            }
            OutlinedButton(onClick = onGallery, enabled = !busy) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gallery")
            }
        }
    }
    viewing?.let { item ->
        ReceiptViewer(item.fileName, onClose = { viewing = null }, onDelete = {
            viewing = null
            onDelete(item)
        })
    }
}

/** Full-screen bill photo: pinch to zoom, share to WhatsApp/Gmail, delete. */
@Composable
fun ReceiptViewer(fileName: String, onClose: () -> Unit, onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    val bmp = rememberReceipt(fileName, 2000)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = "Bill photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(zoom)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    val uri = Receipts.shareUri(context, fileName)
                    val send = Intent(Intent.ACTION_SEND).setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, "Share bill"))
                }) { Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White) }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete photo", tint = Color.White) }
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
            }
        }
    }
}

/** "Goal: None · ✈️ Goa trip · 🏍️ New bike". Hidden when there are no goals. */
@Composable
fun GoalChips(goals: List<com.kye.app.data.Goal>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    if (goals.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChip("None", selectedId == null, { onSelect(null) })
        goals.forEach { g -> ChoiceChip(g.name, selectedId == g.id, { onSelect(if (selectedId == g.id) null else g.id) }, leading = g.emoji) }
    }
}

/** Gold scheme tag for a Saving entry (an instalment paid outside the scheme page). */
@Composable
fun SchemeChips(schemes: List<com.kye.app.data.GoldScheme>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    if (schemes.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Gold scheme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChip("None", selectedId == null, { onSelect(null) })
        schemes.forEach { s -> ChoiceChip(s.name, selectedId == s.id, { onSelect(if (selectedId == s.id) null else s.id) }, leading = "🪙") }
    }
}

/** Which credit card an expense went on (shown when "Credit card" is the payment mode). */
@Composable
fun CardChips(cards: List<com.kye.app.data.CreditCard>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    if (cards.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Card", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChip("None", selectedId == null, { onSelect(null) })
        cards.forEach { c ->
            ChoiceChip(c.name + if (c.last4.isNotBlank()) " ··${c.last4}" else "", selectedId == c.id, { onSelect(if (selectedId == c.id) null else c.id) }, leading = "💳")
        }
    }
}
