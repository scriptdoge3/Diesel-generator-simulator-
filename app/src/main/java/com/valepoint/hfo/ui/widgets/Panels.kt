package com.valepoint.hfo.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.ui.theme.P

/** A bolted-on instrument panel with an engraved header strip. */
@Composable
fun SectionPanel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.verticalGradient(listOf(P.PanelHigh, P.PanelFace, P.PanelLow))
            )
            .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(4.dp))
            .padding(1.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0x33000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = P.Brass)
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = P.LegendDim)
            }
        }
        Column(Modifier.padding(8.dp)) { content() }
    }
}

/** Engraved label and value, the everyday readout of the panel. */
@Composable
fun Readout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = P.Legend,
    small: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = P.LegendDim
        )
        Text(
            value,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

/** Two position toggle switch with a bakelite dolly. */
@Composable
fun PanelSwitch(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onLabel: String = "RUN",
    offLabel: String = "STOP",
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Column(
        modifier
            .width(72.dp)
            .clickable(enabled = enabled) { onToggle(!on) }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) P.Legend else P.LegendDim,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Canvas(
            Modifier
                .padding(top = 2.dp)
                .size(width = 34.dp, height = 26.dp)
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                P.Bezel, Offset(0f, 0f), Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
            drawRoundRect(
                Color(0xFF20241F), Offset(2f, 2f), Size(w - 4f, h - 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
            val cx = w / 2f
            val topY = if (on) h * 0.24f else h * 0.76f
            drawLine(Color(0xFF15171B), Offset(cx, h / 2f), Offset(cx, topY), strokeWidth = w * 0.30f)
            drawCircle(
                if (enabled) Color(0xFFCFC9B6) else Color(0xFF6E7268),
                w * 0.19f, Offset(cx, topY)
            )
            drawCircle(Color(0x55000000), w * 0.09f, Offset(cx + 1f, h / 2f))
        }
        Text(
            if (on) onLabel else offLabel,
            style = MaterialTheme.typography.bodySmall,
            color = if (on) P.LampGreen else P.LegendDim
        )
    }
}

/** Momentary push button with a coloured cap. */
@Composable
fun PushButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subLabel: String? = null,
    onPress: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) color.copy(alpha = 0.92f) else Color(0xFF3A3F3A))
            .border(BorderStroke(2.dp, Color(0x66000000)), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onPress() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Color(0xFF111311) else P.LegendDim,
            textAlign = TextAlign.Center
        )
        if (subLabel != null) {
            Text(
                subLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) Color(0xCC111311) else P.LegendDim
            )
        }
    }
}

/** Rotary selector with engraved positions. */
@Composable
fun Selector(
    label: String,
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = P.LegendDim)
        Row(Modifier.padding(top = 2.dp)) {
            options.forEachIndexed { i, o ->
                val sel = i == selected
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (sel) P.Brass else Color(0x33000000))
                        .border(BorderStroke(1.dp, Color(0x55000000)), RoundedCornerShape(3.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        o,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sel) Color(0xFF15181A) else P.Legend
                    )
                }
            }
        }
    }
}

/** Raise / lower control of the sort wired to a setting motor. */
@Composable
fun RaiseLower(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onRaise: () -> Unit,
    onLower: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = P.LegendDim)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = P.Legend)
        }
        PushButton("LOWER", P.PanelHigh, onPress = onLower)
        Box(Modifier.width(8.dp))
        PushButton("RAISE", P.PanelHigh, onPress = onRaise)
    }
}

/** Slider in panel colours, used for the few continuously variable controls. */
@Composable
fun PanelSlider(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onChange: (Float) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = P.LegendDim)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = P.Brass,
                activeTrackColor = P.Brass,
                inactiveTrackColor = Color(0xFF23281F),
                activeTickColor = P.Brass,
                inactiveTickColor = Color(0xFF23281F),
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

/** One annunciator window in the lamp box. */
@Composable
fun AnnunciatorWindow(
    legend: String,
    lit: Boolean,
    color: Color,
    firstOut: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (lit) color.copy(alpha = 0.88f) else Color(0xFF23281F))
            .border(
                BorderStroke(if (firstOut) 2.dp else 1.dp, if (firstOut) P.LampWhite else Color(0x66000000)),
                RoundedCornerShape(2.dp)
            )
            .clickable { onClick() }
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            legend,
            style = MaterialTheme.typography.bodySmall,
            color = if (lit) Color(0xFF15130E) else Color(0xFF5A6155),
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodySmall.fontSize
        )
    }
}
