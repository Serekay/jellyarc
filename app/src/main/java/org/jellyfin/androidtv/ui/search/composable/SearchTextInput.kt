package org.jellyfin.androidtv.ui.search.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun SearchTextInput(
	query: String,
	onQueryChange: (query: String) -> Unit,
	onQuerySubmit: () -> Unit,
	modifier: Modifier = Modifier,
	showKeyboardOnFocus: Boolean = true,
	placeholder: String? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()

	// Use transparent white border instead of theme purple
	val borderColor = if (focused) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
	val textColor = androidx.compose.ui.graphics.Color.White

	ProvideTextStyle(
		LocalTextStyle.current.copy(
			color = textColor,
			fontSize = 16.sp,
		)
	) {
		BasicTextField(
			modifier = modifier
				.focusable(interactionSource = interactionSource),
			value = query,
			singleLine = true,
			interactionSource = interactionSource,
			onValueChange = { onQueryChange(it) },
			keyboardActions = KeyboardActions { onQuerySubmit() },
			keyboardOptions = KeyboardOptions.Default.copy(
				keyboardType = KeyboardType.Text,
				imeAction = ImeAction.Search,
				autoCorrectEnabled = true,
				showKeyboardOnFocus = showKeyboardOnFocus,
			),
			textStyle = LocalTextStyle.current,
			cursorBrush = SolidColor(borderColor),
			decorationBox = { innerTextField ->
				val scale = if (focused) 1.05f else 1f

				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.graphicsLayer(
							scaleX = scale,
							scaleY = scale,
							shadowElevation = if (focused) 16f else 0f,
						)
						.background(
							color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
							shape = RoundedCornerShape(percent = 30)
						)
						.border(2.dp, borderColor, RoundedCornerShape(percent = 30))
						.padding(12.dp),
				) {
					Icon(ImageVector.vectorResource(R.drawable.ic_search), contentDescription = null)
					Spacer(Modifier.width(12.dp))
					Box(modifier = Modifier.weight(1f)) {
						if (query.isEmpty() && placeholder != null) {
							Text(
								text = placeholder,
								color = textColor.copy(alpha = 0.5f),
								fontSize = 16.sp
							)
						}
						innerTextField()
					}
				}
			},
		)
	}
}
