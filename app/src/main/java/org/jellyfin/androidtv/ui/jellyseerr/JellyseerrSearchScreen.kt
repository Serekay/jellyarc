package org.jellyfin.androidtv.ui.jellyseerr

import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.JellyseerrSearchItem
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.koin.androidx.compose.koinViewModel

/**
 * Modern, dedicated search screen for Jellyseerr.
 * Implements 2025 design trends:
 * - Glassmorphism effects with translucent backgrounds
 * - Clear focus states with bold borders and scaling
 * - Content-first approach with minimal UI chrome
 * - Smooth D-pad navigation optimized for TV
 */
@Composable
fun JellyseerrSearchScreen(
	viewModel: JellyseerrViewModel = koinViewModel(),
	onNavigateBack: () -> Unit,
) {
	val state by viewModel.uiState.collectAsState()
	val searchFocusRequester = remember { FocusRequester() }
	val listState = rememberLazyListState()
	val keyboardController = LocalSoftwareKeyboardController.current

	// Auto-focus search input on screen load and open keyboard
	LaunchedEffect(Unit) {
		kotlinx.coroutines.delay(150) // Small delay to ensure UI is ready
		searchFocusRequester.requestFocus()
		repeat(3) { attempt ->
			kotlinx.coroutines.delay(80L * (attempt + 1)) // staggered retries
			keyboardController?.show()
		}
	}

	// Debounced search with 450ms delay
	@OptIn(FlowPreview::class)
	LaunchedEffect(Unit) {
		snapshotFlow { state.query.trim() }
			.debounce(450)
			.collectLatest { trimmed ->
				if (trimmed.isBlank()) {
					viewModel.clearSearchResults()
				} else {
					viewModel.search(trimmed)
				}
			}
	}

	// Back button handling
	BackHandler {
		viewModel.clearQuery()
		onNavigateBack()
	}

	// Modern glassmorphism background
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(
				Brush.verticalGradient(
					colors = listOf(
						Color(0xFF0F172A), // Deep blue-slate
						Color(0xFF1E293B), // Medium slate
						Color(0xFF0F172A), // Deep blue-slate
					)
				)
			)
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 48.dp, vertical = 24.dp)
		) {
			// Search input ohne extra Box/Rahmen
			SearchTextInput(
				query = state.query,
				onQueryChange = { viewModel.updateQuery(it) },
				onQuerySubmit = { /* debounced search handles this */ },
				modifier = Modifier
					.fillMaxWidth()
					.focusRequester(searchFocusRequester),
				focusRequester = searchFocusRequester,
				showKeyboardOnFocus = true,
				placeholder = stringResource(R.string.jellyseerr_search_hint),
			)

			Spacer(modifier = Modifier.size(16.dp))

			// Filter tabs with modern design
			if (state.query.isNotBlank() && state.results.isNotEmpty()) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier.padding(bottom = 16.dp)
				) {
					val filters = listOf(
						JellyseerrSearchFilter.ALL to stringResource(R.string.lbl_all_items),
						JellyseerrSearchFilter.MOVIES to stringResource(R.string.lbl_movies),
						JellyseerrSearchFilter.TV to stringResource(R.string.lbl_tv_series),
						JellyseerrSearchFilter.PEOPLE to stringResource(R.string.jellyseerr_cast_title),
					)

					filters.forEach { (filter, label) ->
						ModernFilterTab(
							label = label,
							isSelected = state.searchFilter == filter,
							onClick = { viewModel.updateSearchFilter(filter) }
						)
					}
				}
			}

			// Search results with categorization
			when {
				state.query.isBlank() -> {
					// Empty state - just show blank since search input is already focused
					Box(modifier = Modifier.fillMaxSize())
				}
				state.isLoading -> {
					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center
					) {
						Text(
							text = stringResource(R.string.lbl_loading_elipses),
							color = Color.White.copy(alpha = 0.6f),
							fontSize = 18.sp
						)
					}
				}
				state.results.isEmpty() -> {
					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center
					) {
						Text(
							text = stringResource(R.string.jellyseerr_no_results),
							color = Color.White.copy(alpha = 0.6f),
							fontSize = 18.sp
						)
					}
				}
				else -> {
					// Categorized results
					val categorizedResults = categorizeSearchResults(state)

					LazyColumn(
						state = listState,
						modifier = Modifier.fillMaxSize(),
						verticalArrangement = Arrangement.spacedBy(16.dp)
					) {
						categorizedResults.forEach { (categoryTitle, categoryItems, showCategory) ->
							if (showCategory && categoryItems.isNotEmpty()) {
								// Category header
								if (categoryTitle.isNotBlank()) {
									item {
										Text(
											text = categoryTitle,
											color = Color.White,
											fontSize = 22.sp,
											fontWeight = FontWeight.Bold,
											modifier = Modifier.padding(vertical = 8.dp)
										)
									}
								}

								// Category items in rows of 5
								val rows = categoryItems.chunked(5)
								items(rows.size) { rowIndex ->
									val rowItems = rows[rowIndex]

									Row(
										horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
										modifier = Modifier
											.fillMaxWidth()
											.padding(vertical = 8.dp)
									) {
										rowItems.forEach { item ->
											JellyseerrSearchCard(
												item = item,
												onClick = onSearchItemClick(viewModel, item),
											)
										}
									}

									// Load more items when reaching the end
									if (rowIndex == rows.lastIndex && !state.isLoading && state.searchHasMore) {
										LaunchedEffect(rows.size) {
											viewModel.loadMoreSearchResults()
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
}

/**
 * Modern filter tab with glassmorphism and focus states
 */
@Composable
private fun ModernFilterTab(
	label: String,
	isSelected: Boolean,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val view = LocalView.current

	LaunchedEffect(isFocused) {
		if (isFocused) {
			view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
		}
	}

	Box(
		modifier = Modifier
			.clip(RoundedCornerShape(12.dp))
			.background(
				if (isSelected) {
					Brush.horizontalGradient(
						colors = listOf(
							Color(0xFF6366F1), // Indigo
							Color(0xFF8B5CF6), // Purple
						)
					)
				} else {
					Brush.horizontalGradient(
						colors = listOf(
							Color(0xFF1F2937),
							Color(0xFF374151),
						)
					)
				}
			)
			.border(
				width = if (isFocused) 3.dp else 1.dp,
				color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
				shape = RoundedCornerShape(12.dp)
			)
			.clickable(
				interactionSource = interactionSource,
				indication = null
			) { onClick() }
			.focusable(interactionSource = interactionSource)
			.padding(horizontal = 20.dp, vertical = 10.dp)
	) {
		Text(
			text = label,
			color = Color.White,
			fontSize = 16.sp,
			fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
		)
	}
}

/**
 * Categorize search results by media type
 */
@Composable
private fun categorizeSearchResults(
	state: JellyseerrUiState
): List<Triple<String, List<JellyseerrSearchItem>, Boolean>> {
	val movies = state.results.filter { it.mediaType == "movie" }
	val tvShows = state.results.filter { it.mediaType == "tv" }
	val people = state.results.filter { it.mediaType == "person" }

	return when (state.searchFilter) {
		JellyseerrSearchFilter.ALL -> listOf(
			Triple(stringResource(R.string.lbl_movies), movies, movies.isNotEmpty()),
			Triple(stringResource(R.string.lbl_tv_series), tvShows, tvShows.isNotEmpty()),
			Triple(stringResource(R.string.jellyseerr_cast_title), people, people.isNotEmpty()),
		)
		JellyseerrSearchFilter.MOVIES -> listOf(
			Triple("", movies, movies.isNotEmpty()),
		)
		JellyseerrSearchFilter.TV -> listOf(
			Triple("", tvShows, tvShows.isNotEmpty()),
		)
		JellyseerrSearchFilter.PEOPLE -> listOf(
			Triple("", people, people.isNotEmpty()),
		)
	}
}
