package org.jellyfin.androidtv.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jellyfin.androidtv.ui.base.Text

data class CustomDialogButton(
	val text: String,
	val onClick: () -> Unit,
	val isPrimary: Boolean = false,
	val requestFocusOnShow: Boolean = false
)

@Composable
fun CustomDialog(
	title: String,
	message: String,
	buttons: List<CustomDialogButton>,
	onDismissRequest: (() -> Unit)? = null
) {
	Dialog(
		onDismissRequest = onDismissRequest ?: {},
		properties = DialogProperties(
			usePlatformDefaultWidth = false,
			dismissOnBackPress = onDismissRequest != null,
			dismissOnClickOutside = false,
		),
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black.copy(alpha = 0.7f)),
			contentAlignment = Alignment.Center,
		) {
			CustomDialogContent(
				title = title,
				message = message,
				buttons = buttons
			)
		}
	}
}

@Composable
private fun CustomDialogContent(
	title: String,
	message: String,
	buttons: List<CustomDialogButton>
) {
	val focusRequesters = remember { buttons.map { FocusRequester() } }

	LaunchedEffect(Unit) {
		val focusIndex = buttons.indexOfFirst { it.requestFocusOnShow }
		if (focusIndex >= 0) {
			focusRequesters[focusIndex].requestFocus()
		}
	}

	Column(
		modifier = Modifier
			.width(600.dp)
			.background(
				color = Color(0xFF1E1E1E),
				shape = RoundedCornerShape(16.dp)
			)
			.padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		// Titel
		Text(
			text = title,
			fontSize = 28.sp,
			color = Color.White,
			textAlign = TextAlign.Center
		)

		Spacer(modifier = Modifier.height(24.dp))

		// Message
		Text(
			text = message,
			fontSize = 20.sp,
			color = Color.White.copy(alpha = 0.9f),
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth()
		)

		Spacer(modifier = Modifier.height(32.dp))

		// Buttons
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.Center,
			verticalAlignment = Alignment.CenterVertically
		) {
			buttons.forEachIndexed { index, button ->
				if (index > 0) {
					Spacer(modifier = Modifier.width(16.dp))
				}

				SimpleButton(
					text = button.text,
					onClick = button.onClick,
					modifier = Modifier.focusRequester(focusRequesters[index]),
					backgroundColor = if (button.isPrimary) Color(0xFF00A4DC) else Color.Transparent,
					contentColor = Color.White
				)
			}
		}
	}
}

@Composable
private fun SimpleButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	backgroundColor: Color = Color.Transparent,
	contentColor: Color = Color.White
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()

	Box(
		modifier = modifier
			.clip(RoundedCornerShape(8.dp))
			.background(if (isFocused) backgroundColor.copy(alpha = 0.8f) else backgroundColor)
			.border(
				width = if (isFocused) 3.dp else 1.dp,
				color = if (isFocused) Color.White else contentColor.copy(alpha = 0.5f),
				shape = RoundedCornerShape(8.dp)
			)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick
			)
			.focusable(interactionSource = interactionSource)
			.padding(horizontal = 24.dp, vertical = 12.dp),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = text,
			fontSize = 18.sp,
			color = contentColor
		)
	}
}
