package org.jellyfin.androidtv.ui.jellyseerr

import android.view.SoundEffectConstants
import android.widget.ImageView
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.JellyseerrCompany
import org.jellyfin.androidtv.data.repository.JellyseerrGenre
import org.jellyfin.androidtv.data.repository.JellyseerrGenreSlider
import org.jellyfin.androidtv.data.repository.JellyseerrRequest
import org.jellyfin.androidtv.data.repository.JellyseerrSearchItem
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.koin.androidx.compose.koinViewModel

private const val VIEW_ALL_TRENDING = "view_all_trending"
private const val VIEW_ALL_POPULAR_MOVIES = "view_all_popular_movies"
private const val VIEW_ALL_POPULAR_TV = "view_all_popular_tv"
private const val VIEW_ALL_UPCOMING_MOVIES = "view_all_upcoming_movies"
private const val VIEW_ALL_UPCOMING_TV = "view_all_upcoming_tv"
private const val VIEW_ALL_SEARCH_RESULTS = "view_all_search_results"

@Composable
internal fun JellyseerrContent(
	viewModel: JellyseerrViewModel = koinViewModel(),
	onShowSeasonDialog: () -> Unit,
	firstCastFocusRequester: FocusRequester,
) {
	val state by viewModel.uiState.collectAsState()
	val keyboardController = LocalSoftwareKeyboardController.current
	val searchFocusRequester = remember { FocusRequester() }
	val allTrendsListState = rememberLazyListState()
	val sectionSpacing = 5.dp // Abstand zwischen Sektionen
	val sectionInnerSpacing = 6.dp // Abstand innerhalb einer Sektion (label + Inhalt)
	val sectionTitleFontSize = 26.sp
	val itemFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
	val viewAllFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
	val lastRestoredFocus = remember { mutableStateOf<Pair<String?, String?>>(null to null) }
	val itemFocusKey: (String, Int) -> String = { row, id -> "$row-$id" }

	LaunchedEffect(
		state.selectedItem,
		state.showAllTrendsGrid,
		state.selectedPerson,
		state.lastFocusedItemId,
		state.lastFocusedViewAllKey,
		state.showSearchResultsGrid,
		state.query,
	) {
		val browsing = state.selectedItem == null &&
			state.selectedPerson == null &&
			!state.showAllTrendsGrid &&
			!state.showSearchResultsGrid

		if (!browsing || state.query.isNotBlank()) {
			lastRestoredFocus.value = null to null
			return@LaunchedEffect
		}

		val targetPair = state.lastFocusedItemId to state.lastFocusedViewAllKey
		if (lastRestoredFocus.value == targetPair) return@LaunchedEffect

		// Längerer Delay um sicherzustellen, dass Scroll-Animation abgeschlossen ist
		delay(400)

		val itemId = state.lastFocusedItemId
		if (itemId != null) {
			// Mehrere Versuche, da das Element möglicherweise noch nicht gerendert wurde
			repeat(3) { attempt ->
				val focusRequester = itemFocusRequesters[itemId]
				if (focusRequester != null) {
					try {
						focusRequester.requestFocus()
						lastRestoredFocus.value = targetPair
						return@LaunchedEffect
					} catch (e: IllegalStateException) {
						// Element noch nicht sichtbar
					}
				}
				if (attempt < 2) delay(150)
			}
			return@LaunchedEffect
		}

		val viewAllKey = state.lastFocusedViewAllKey
		if (viewAllKey != null) {
			// Mehrere Versuche, da das Element möglicherweise noch nicht gerendert wurde
			repeat(3) { attempt ->
				val focusRequester = viewAllFocusRequesters[viewAllKey]
				if (focusRequester != null) {
					try {
						focusRequester.requestFocus()
						lastRestoredFocus.value = targetPair
						return@LaunchedEffect
					} catch (e: IllegalStateException) {
						// Element noch nicht sichtbar
					}
				}
				if (attempt < 2) delay(150)
			}
		}
	}

	val focusRequesterForItem: (String) -> FocusRequester = { key ->
		itemFocusRequesters.getOrPut(key) { FocusRequester() }
	}

	val focusRequesterForViewAll: (String) -> FocusRequester = { key ->
		viewAllFocusRequesters.getOrPut(key) { FocusRequester() }
	}

	BackHandler(
		enabled =
			state.selectedItem != null ||
				state.selectedPerson != null ||
				state.showAllTrendsGrid ||
				state.showSearchResultsGrid,
	) {
		when {
			state.selectedItem != null -> viewModel.closeDetails()
			state.selectedPerson != null -> viewModel.closePerson()
			state.showAllTrendsGrid -> viewModel.closeAllTrends()
				state.showSearchResultsGrid -> viewModel.closeSearchResultsGrid()
		}
	}

	@OptIn(FlowPreview::class)
	LaunchedEffect(Unit) {
		snapshotFlow { state.query.trim() }
			.debounce(450)
			.collectLatest { trimmed ->
				if (trimmed.isBlank()) {
					viewModel.closeSearchResultsGrid()
					return@collectLatest
				}
				viewModel.search()
			}
	}


	val selectedItem = state.selectedItem
	val selectedPerson = state.selectedPerson
	val isShowingDetail = selectedItem != null || selectedPerson != null

	// When a detail/person overlay is visible, keep browse content out of the focus graph
	// so D-pad navigation cannot jump into the rows behind the overlay.
	if (isShowingDetail) {
		Box(modifier = Modifier.fillMaxSize())
		return
	}

	// Browse-Ansicht - bleibt im Compose-Tree für bessere Performance und Fokus-Erhaltung
	// Nur die Sichtbarkeit und Fokussierbarkeit wird gesteuert
	Box(
		modifier = Modifier
			.fillMaxSize()
	) {
		val scrollState = rememberScrollState(initial = state.mainScrollPosition)
		val isShowingGrid = state.showAllTrendsGrid || state.showSearchResultsGrid

		// Speichere die Scroll-Position wenn sich diese ändert
		LaunchedEffect(scrollState.value) {
			if (!isShowingGrid && scrollState.value > 0) {
				viewModel.updateMainScrollPosition(scrollState.value)
			}
		}

		// Stelle die Scroll-Position wieder her und setze dann den Fokus
		LaunchedEffect(
			state.selectedItem,
			state.selectedPerson,
			state.showAllTrendsGrid,
			state.showSearchResultsGrid,
		) {
			val isBrowsing = state.selectedItem == null &&
				state.selectedPerson == null &&
				!state.showAllTrendsGrid &&
				!state.showSearchResultsGrid

			if (isBrowsing && state.mainScrollPosition > 0 && scrollState.value != state.mainScrollPosition) {
				// Scroll zur gespeicherten Position
				scrollState.animateScrollTo(state.mainScrollPosition)
			}
		}

		val columnModifier = if (isShowingGrid) {
			Modifier
				.fillMaxSize()
				.padding(24.dp)
		} else {
			Modifier
				.fillMaxSize()
				.verticalScroll(scrollState)
				.padding(24.dp)
		}

		Column(
			modifier = columnModifier,
		) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Box(
					modifier = Modifier
						.weight(1f),
				) {
					SearchTextInput(
						query = state.query,
						onQueryChange = { viewModel.updateQuery(it) },
						onQuerySubmit = {
							viewModel.search()
							keyboardController?.hide()
						},
						modifier = Modifier
							.fillMaxWidth()
							.focusRequester(searchFocusRequester),
						showKeyboardOnFocus = true,
					)
				}
			}

			Spacer(modifier = Modifier.size(sectionSpacing))

			val shouldShowError = state.errorMessage?.contains("HTTP 400", ignoreCase = true) != true
			if (state.errorMessage != null && shouldShowError) {
				Text(
					text = stringResource(R.string.jellyseerr_error_prefix, state.errorMessage ?: ""),
					color = Color.Red,
					modifier = Modifier.padding(bottom = 16.dp),
				)
			}

			if (isShowingGrid) {
				val headerText = if (state.showSearchResultsGrid) {
					stringResource(R.string.jellyseerr_search_results_title)
				} else {
					state.discoverTitle?.takeIf { it.isNotBlank() }
						?: stringResource(state.discoverCategory.titleResId)
				}
				Text(text = headerText, color = Color.White, fontSize = sectionTitleFontSize)

				val isCategoryScreen = state.discoverCategory in setOf(
					JellyseerrDiscoverCategory.MOVIE_GENRE,
					JellyseerrDiscoverCategory.TV_GENRE,
					JellyseerrDiscoverCategory.MOVIE_STUDIOS,
					JellyseerrDiscoverCategory.TV_NETWORKS,
				)

				val gridResults = when {
					state.showSearchResultsGrid -> state.results
					state.showAllTrendsGrid -> state.results
					isCategoryScreen -> state.results
					state.query.isBlank() -> state.results.take(20)
					else -> state.results
				}

				if (gridResults.isEmpty() && !state.isLoading) {
					Text(
						text = stringResource(R.string.jellyseerr_no_results),
						modifier = Modifier.padding(vertical = 8.dp),
					)
				} else {
					val rows = gridResults.chunked(5)

					LazyColumn(
						state = allTrendsListState,
						modifier = Modifier
							.fillMaxSize()
							.padding(top = 8.dp),
					) {
						items(rows.size) { rowIndex ->
							val rowItems = rows[rowIndex]

							Row(
								horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
								modifier = Modifier
									.fillMaxWidth()
									.padding(vertical = 15.dp),
							) {
								for (item in rowItems) {
									JellyseerrSearchCard(
										item = item,
										onClick = onSearchItemClick(viewModel, item),
										// do not overwrite last focused main-page card while inside grids
									)
								}
							}

							if (rowIndex == rows.lastIndex && !state.isLoading) {
								when {
									state.showAllTrendsGrid && state.discoverHasMore -> {
										LaunchedEffect(key1 = rows.size) {
											viewModel.loadMoreTrends()
										}
									}
									state.showSearchResultsGrid && state.searchHasMore -> {
										LaunchedEffect(key1 = rows.size) {
											viewModel.loadMoreSearchResults()
										}
									}
								}
							}
						}
					}
				}
			} else {
				val titleRes = if (state.query.isBlank()) {
					R.string.jellyseerr_discover_title
				} else {
					R.string.jellyseerr_search_results_title
				}
				Text(text = stringResource(titleRes), color = JellyfinTheme.colorScheme.onBackground, fontSize = sectionTitleFontSize)

				val baseResults = if (state.query.isBlank()) {
					state.trendingResults.take(20)
				} else {
					state.results
				}

				if (baseResults.isEmpty() && !state.isLoading) {
					Text(
						text = stringResource(R.string.jellyseerr_no_results),
						modifier = Modifier.padding(vertical = 8.dp),
					)
				} else {
					val focusRequester = FocusRequester()
					val listState = rememberLazyListState(
						initialFirstVisibleItemIndex = state.scrollPositions["discover"]?.index ?: 0,
						initialFirstVisibleItemScrollOffset = state.scrollPositions["discover"]?.offset ?: 0,
					)

					// Speichere Scroll-Position wenn sich der Zustand ändert
					LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
						if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
							viewModel.saveScrollPosition(
								"discover",
								listState.firstVisibleItemIndex,
								listState.firstVisibleItemScrollOffset
							)
						}
					}

					LazyRow(
						state = listState,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						contentPadding = PaddingValues(horizontal = 24.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(250.dp)
							.padding(top = 15.dp),
					) {
						val showViewAllCard = !state.showSearchResultsGrid
						val maxIndex = baseResults.lastIndex
						val extraItems = if (showViewAllCard) 1 else 0

						items(maxIndex + 1 + extraItems) { index ->
							when {
								index in 0..maxIndex -> {
									val item = baseResults[index]
									val cardModifier = if (index == 0) {
										Modifier.focusRequester(focusRequester)
									} else {
										Modifier
									}
									val focusKey = itemFocusKey("discover", item.id)

									JellyseerrSearchCard(
										item = item,
										onClick = onSearchItemClick(viewModel, item),
										modifier = cardModifier,
										focusRequester = focusRequesterForItem(focusKey),
										onFocus = { viewModel.updateLastFocusedItem(focusKey) },
									)
								}

								showViewAllCard && index == maxIndex + 1 -> {
									val posterUrls = remember(baseResults) {
										baseResults.shuffled().take(4).mapNotNull { it.posterPath }
									}
									val viewAllKey = if (state.query.isBlank()) {
										VIEW_ALL_TRENDING
									} else {
										VIEW_ALL_SEARCH_RESULTS
									}
									val onViewAllClick = if (state.query.isBlank()) {
										{ viewModel.showAllTrends() }
									} else {
										{ viewModel.showAllSearchResults() }
									}
									JellyseerrViewAllCard(
										onClick = onViewAllClick,
										posterUrls = posterUrls,
										focusRequester = focusRequesterForViewAll(viewAllKey),
										onFocus = { viewModel.updateLastFocusedViewAll(viewAllKey) },
									)
								}
							}
						}
					}
				}

				// Beliebte Filme
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_popular_title),
					color = JellyfinTheme.colorScheme.onBackground,
					fontSize = sectionTitleFontSize,
					)

					if (state.popularResults.isEmpty()) {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))
						Text(
							text = stringResource(R.string.jellyseerr_no_results),
							modifier = Modifier.padding(horizontal = 24.dp),
							color = JellyfinTheme.colorScheme.onBackground,
						)
					} else {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))

						val popularListState = rememberLazyListState(
							initialFirstVisibleItemIndex = state.scrollPositions["popular"]?.index ?: 0,
							initialFirstVisibleItemScrollOffset = state.scrollPositions["popular"]?.offset ?: 0,
						)

						LaunchedEffect(popularListState.firstVisibleItemIndex, popularListState.firstVisibleItemScrollOffset) {
							if (popularListState.firstVisibleItemIndex > 0 || popularListState.firstVisibleItemScrollOffset > 0) {
								viewModel.saveScrollPosition(
									"popular",
									popularListState.firstVisibleItemIndex,
									popularListState.firstVisibleItemScrollOffset
								)
							}
						}

						LazyRow(
							state = popularListState,
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							contentPadding = PaddingValues(horizontal = 24.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(250.dp),
						) {
							val maxIndex = state.popularResults.lastIndex
							val extraItems = 1

							items(
								count = maxIndex + 1 + extraItems,
								key = { index ->
									if (index <= maxIndex) "popular_movie_${state.popularResults[index].id}"
									else "popular_movies_view_all"
								}
							) { index ->
								when {
									index in 0..maxIndex -> {
										val item = state.popularResults[index]
									val focusKey = itemFocusKey("popular_movies", item.id)
									JellyseerrSearchCard(
										item = item,
										onClick = { viewModel.showDetailsForItem(item) },
										focusRequester = focusRequesterForItem(focusKey),
										onFocus = { viewModel.updateLastFocusedItem(focusKey) },
									)
									}
								index == maxIndex + 1 -> {
									val posterUrls = remember(state.popularResults) {
										state.popularResults.shuffled().take(4).mapNotNull { it.posterPath }
									}
									JellyseerrViewAllCard(
										onClick = { viewModel.showAllPopularMovies() },
										posterUrls = posterUrls,
										focusRequester = focusRequesterForViewAll(VIEW_ALL_POPULAR_MOVIES),
										onFocus = { viewModel.updateLastFocusedViewAll(VIEW_ALL_POPULAR_MOVIES) },
									)
								}
								}
							}
						}
					}
				}

				// Beliebte Serien
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_popular_tv_title),
					color = JellyfinTheme.colorScheme.onBackground,
					fontSize = sectionTitleFontSize,
					)

					if (state.popularTvResults.isEmpty()) {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))
						Text(
							text = stringResource(R.string.jellyseerr_no_results),
							modifier = Modifier.padding(horizontal = 24.dp),
							color = JellyfinTheme.colorScheme.onBackground,
						)
					} else {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))

						LazyRow(
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							contentPadding = PaddingValues(horizontal = 24.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(250.dp),
						) {
							val maxIndex = state.popularTvResults.lastIndex
							val extraItems = 1

							items(
								count = maxIndex + 1 + extraItems,
								key = { index ->
									if (index <= maxIndex) "popular_tv_${state.popularTvResults[index].id}"
									else "popular_tv_view_all"
								}
							) { index ->
								when {
									index in 0..maxIndex -> {
										val item = state.popularTvResults[index]
										val focusKey = itemFocusKey("popular_tv", item.id)
										JellyseerrSearchCard(
											item = item,
											onClick = { viewModel.showDetailsForItem(item) },
											focusRequester = focusRequesterForItem(focusKey),
											onFocus = { viewModel.updateLastFocusedItem(focusKey) },
										)
									}
								index == maxIndex + 1 -> {
									val posterUrls = remember(state.popularTvResults) {
										state.popularTvResults.shuffled().take(4).mapNotNull { it.posterPath }
									}
									JellyseerrViewAllCard(
										onClick = { viewModel.showAllPopularTv() },
										posterUrls = posterUrls,
										focusRequester = focusRequesterForViewAll(VIEW_ALL_POPULAR_TV),
										onFocus = { viewModel.updateLastFocusedViewAll(VIEW_ALL_POPULAR_TV) },
									)
								}
								}
							}
						}
					}
				}


				// Demnächst erscheinende Filme
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_upcoming_movies_title),
					color = JellyfinTheme.colorScheme.onBackground,
					fontSize = sectionTitleFontSize,
					)


					if (state.upcomingMovieResults.isEmpty()) {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))
						Text(
							text = stringResource(R.string.jellyseerr_no_results),
							modifier = Modifier.padding(horizontal = 24.dp),
							color = JellyfinTheme.colorScheme.onBackground,
						)
					} else {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))

						LazyRow(
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							contentPadding = PaddingValues(horizontal = 24.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(250.dp),
						) {
							val maxIndex = state.upcomingMovieResults.lastIndex
							val extraItems = 1

							items(
								count = maxIndex + 1 + extraItems,
								key = { index ->
									if (index <= maxIndex) "upcoming_movie_${state.upcomingMovieResults[index].id}"
									else "upcoming_movies_view_all"
								}
							) { index ->
								when {
									index in 0..maxIndex -> {
										val item = state.upcomingMovieResults[index]
										val focusKey = itemFocusKey("upcoming_movies", item.id)
										JellyseerrSearchCard(
											item = item,
											onClick = { viewModel.showDetailsForItem(item) },
											focusRequester = focusRequesterForItem(focusKey),
											onFocus = { viewModel.updateLastFocusedItem(focusKey) },
										)
									}
								index == maxIndex + 1 -> {
									val posterUrls = remember(state.upcomingMovieResults) {
										state.upcomingMovieResults.shuffled().take(4).mapNotNull { it.posterPath }
									}
									JellyseerrViewAllCard(
										onClick = { viewModel.showAllUpcomingMovies() },
										posterUrls = posterUrls,
										focusRequester = focusRequesterForViewAll(VIEW_ALL_UPCOMING_MOVIES),
										onFocus = { viewModel.updateLastFocusedViewAll(VIEW_ALL_UPCOMING_MOVIES) },
									)
								}
								}
							}
						}
					}
				}

				// Demnächst erscheinende Serien
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_upcoming_tv_title),
					color = JellyfinTheme.colorScheme.onBackground,
					fontSize = sectionTitleFontSize,
					)

					if (state.upcomingTvResults.isEmpty()) {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))
						Text(
							text = stringResource(R.string.jellyseerr_no_results),
							modifier = Modifier.padding(horizontal = 24.dp),
							color = JellyfinTheme.colorScheme.onBackground,
						)
					} else {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))

						LazyRow(
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							contentPadding = PaddingValues(horizontal = 24.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(250.dp),
						) {
							val maxIndex = state.upcomingTvResults.lastIndex
							val extraItems = 1

							items(
								count = maxIndex + 1 + extraItems,
								key = { index ->
									if (index <= maxIndex) "upcoming_tv_${state.upcomingTvResults[index].id}"
									else "upcoming_tv_view_all"
								}
							) { index ->
								when {
									index in 0..maxIndex -> {
										val item = state.upcomingTvResults[index]
										val focusKey = itemFocusKey("upcoming_tv", item.id)
										JellyseerrSearchCard(
											item = item,
											onClick = { viewModel.showDetailsForItem(item) },
											focusRequester = focusRequesterForItem(focusKey),
											onFocus = { viewModel.updateLastFocusedItem(focusKey) },
										)
									}
									index == maxIndex + 1 -> {
										val posterUrls = remember(state.upcomingTvResults) {
											state.upcomingTvResults.shuffled().take(4).mapNotNull { it.posterPath }
										}
										JellyseerrViewAllCard(
											onClick = { viewModel.showAllUpcomingTv() },
											posterUrls = posterUrls,
											focusRequester = focusRequesterForViewAll(VIEW_ALL_UPCOMING_TV),
											onFocus = { viewModel.updateLastFocusedViewAll(VIEW_ALL_UPCOMING_TV) },
										)
									}
								}
							}
						}
					}
				}

				// Film-Genres
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank() && state.movieGenres.isNotEmpty()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_movie_genres_title),
						color = JellyfinTheme.colorScheme.onBackground,
						fontSize = sectionTitleFontSize,
					)

					Spacer(modifier = Modifier.size(sectionInnerSpacing))

					LazyRow(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						contentPadding = PaddingValues(horizontal = 24.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(110.dp),
					) {
						items(
							items = state.movieGenres,
							key = { it.id }
						) { genre ->
							val genreKey = "movie_genre_${genre.id}"
							JellyseerrGenreCard(
								genre = genre,
								onClick = {
									viewModel.updateLastFocusedViewAll(genreKey)
									viewModel.showMovieGenre(genre)
								},
								focusRequester = focusRequesterForViewAll(genreKey),
								onFocus = { viewModel.updateLastFocusedViewAll(genreKey) },
							)
						}
				}
			}

			// Serien-Genres
			if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank() && state.tvGenres.isNotEmpty()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_tv_genres_title),
						color = JellyfinTheme.colorScheme.onBackground,
						fontSize = sectionTitleFontSize,
					)

					Spacer(modifier = Modifier.size(sectionInnerSpacing))

					LazyRow(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						contentPadding = PaddingValues(horizontal = 24.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(110.dp),
					) {
						items(
							items = state.tvGenres,
							key = { it.id }
						) { genre ->
							val genreKey = "tv_genre_${genre.id}"
							JellyseerrGenreCard(
								genre = genre,
								onClick = {
									viewModel.updateLastFocusedViewAll(genreKey)
									viewModel.showTvGenre(genre)
								},
								focusRequester = focusRequesterForViewAll(genreKey),
								onFocus = { viewModel.updateLastFocusedViewAll(genreKey) },
							)
						}
					}
				}

				// Filmstudios
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_movie_studios_title),
						color = JellyfinTheme.colorScheme.onBackground,
						fontSize = sectionTitleFontSize,
					)

					Spacer(modifier = Modifier.size(sectionInnerSpacing))

					LazyRow(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						contentPadding = PaddingValues(horizontal = 24.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(110.dp),
					) {
						items(
							items = JellyseerrStudioCards,
							key = { it.id }
						) { studio ->
							val studioKey = "movie_studio_${studio.id}"
							JellyseerrCompanyCard(
								name = studio.name,
								logoUrl = studio.logoUrl,
								onClick = {
									viewModel.updateLastFocusedViewAll(studioKey)
									viewModel.showMovieStudio(studio)
								},
								focusRequester = focusRequesterForViewAll(studioKey),
								onFocus = { viewModel.updateLastFocusedViewAll(studioKey) },
							)
						}
					}
				}

				// Sender
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_tv_networks_title),
						color = JellyfinTheme.colorScheme.onBackground,
						fontSize = sectionTitleFontSize,
					)

					Spacer(modifier = Modifier.size(sectionInnerSpacing))

					LazyRow(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						contentPadding = PaddingValues(horizontal = 24.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(110.dp),
					) {
						items(
							items = JellyseerrNetworkCards,
							key = { it.id }
						) { network ->
							val networkKey = "tv_network_${network.id}"
							JellyseerrCompanyCard(
								name = network.name,
								logoUrl = network.logoUrl,
								onClick = {
									viewModel.updateLastFocusedViewAll(networkKey)
									viewModel.showTvNetwork(network)
								},
								focusRequester = focusRequesterForViewAll(networkKey),
								onFocus = { viewModel.updateLastFocusedViewAll(networkKey) },
							)
						}
					}
				}

				// Bisherige Anfragen (eigene Anfragen)
				if (state.selectedItem == null && state.selectedPerson == null && state.query.isBlank()) {
					Spacer(modifier = Modifier.size(sectionSpacing))

					Text(
						text = stringResource(R.string.jellyseerr_recent_requests_title),
						color = JellyfinTheme.colorScheme.onBackground,
						fontSize = sectionTitleFontSize,
					)

					if (state.recentRequests.isEmpty()) {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))
						Text(
							text = stringResource(R.string.jellyseerr_no_results),
							modifier = Modifier.padding(horizontal = 24.dp),
							color = JellyfinTheme.colorScheme.onBackground,
						)
					} else {
						Spacer(modifier = Modifier.size(sectionInnerSpacing))

						LazyRow(
							horizontalArrangement = Arrangement.spacedBy(12.dp),
							contentPadding = PaddingValues(horizontal = 24.dp),
							modifier = Modifier
								.fillMaxWidth()
								.height(120.dp),
						) {
							itemsIndexed(
								items = state.recentRequests,
								key = { index, _ -> "recent_request_$index" }
							) { index, item ->
								val requestKey = "recent_request_$index"
								JellyseerrRecentRequestCard(
									item = item,
									onClick = {
										viewModel.updateLastFocusedViewAll(requestKey)
										viewModel.showDetailsForItem(item)
									},
									focusRequester = focusRequesterForViewAll(requestKey),
									onFocus = { viewModel.updateLastFocusedViewAll(requestKey) },
								)
							}
						}
					}
				}
			}
		}
	}
}


internal fun onSearchItemClick(viewModel: JellyseerrViewModel, item: JellyseerrSearchItem): () -> Unit =
	if (item.mediaType == "person") {
		{ viewModel.showPersonFromSearchItem(item) }
	} else {
		{ viewModel.showDetailsForItem(item) }
	}


@Composable
internal fun JellyseerrRecentRequestCard(
	item: JellyseerrSearchItem,
	onClick: () -> Unit,
	focusRequester: FocusRequester? = null,
	onFocus: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val scale = if (isFocused) 1.05f else 1f
	val view = LocalView.current

	LaunchedEffect(isFocused) {
		if (isFocused) {
			view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
			onFocus?.invoke()
		}
	}

	Box(
		modifier = Modifier
			.width(200.dp)
			.fillMaxHeight()
			.clickable(onClick = onClick, interactionSource = interactionSource, indication = null)
			.then(
				if (focusRequester != null) {
					Modifier.focusRequester(focusRequester)
				} else {
					Modifier
				}
			)
			.focusable(interactionSource = interactionSource)
			.graphicsLayer(
				scaleX = scale,
				scaleY = scale,
			)
			.padding(vertical = 4.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.clip(RoundedCornerShape(8.dp))
				.border(
					width = if (isFocused) 3.dp else 1.dp,
					color = if (isFocused) Color.White else Color(0xFF555555),
					shape = RoundedCornerShape(8.dp),
				),
		) {
			// Backdrop Image
			if (!item.backdropPath.isNullOrBlank()) {
				AsyncImage(
					modifier = Modifier.fillMaxSize(),
					url = item.backdropPath,
					aspectRatio = 16f / 9f,
					scaleType = ImageView.ScaleType.CENTER_CROP,
				)
			} else {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(Color(0xFF1A1A1A)),
				)
			}

			// Dimmer overlay
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.horizontalGradient(
							colors = listOf(
								Color.Black.copy(alpha = 0.85f),
								Color.Black.copy(alpha = 0.4f),
							),
						),
					),
			)

			Row(
				modifier = Modifier
					.fillMaxSize()
					.padding(8.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				// Left side - Text content
				Column(
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight(),
					verticalArrangement = Arrangement.SpaceBetween,
				) {
					Column {
						// Media type badge and year row
						Row(
							horizontalArrangement = Arrangement.spacedBy(4.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							// Media type badge
							val mediaTypeText = if (item.mediaType == "tv") stringResource(R.string.lbl_tv_series) else stringResource(R.string.lbl_movies)
							Box(
								modifier = Modifier
									.clip(RoundedCornerShape(3.dp))
									.background(Color(0xFF424242))
									.padding(horizontal = 4.dp, vertical = 1.dp),
							) {
								Text(
									text = mediaTypeText,
									color = Color.White,
									fontSize = 8.sp,
								)
							}

							// Year
							val year = item.releaseDate?.take(4) ?: ""
							if (year.isNotBlank()) {
								Text(
									text = year,
									color = Color.White.copy(alpha = 0.7f),
									fontSize = 10.sp,
								)
							}
						}

						Spacer(modifier = Modifier.height(2.dp))

						// Title
						Text(
							text = item.title,
							color = Color.White,
							fontSize = 12.sp,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
					}

					// Status Badge
					val statusText = when {
						item.isAvailable -> stringResource(R.string.jellyseerr_available_label)
						item.isPartiallyAvailable -> stringResource(R.string.jellyseerr_partially_available_label)
						item.requestStatus != null -> stringResource(R.string.jellyseerr_requested_label)
						else -> ""
					}

					val statusColor = when {
						item.isAvailable -> Color(0xFF2E7D32)
						item.isPartiallyAvailable -> Color(0xFF0097A7)
						item.requestStatus != null -> Color(0xFFDD8800)
						else -> Color.Transparent
					}

					if (statusText.isNotBlank()) {
						Box(
							modifier = Modifier
								.clip(RoundedCornerShape(4.dp))
								.background(statusColor)
								.padding(horizontal = 6.dp, vertical = 2.dp),
						) {
							Text(
								text = statusText,
								color = Color.White,
								fontSize = 9.sp,
							)
						}
					}
				}

				// Right side - Poster
				Box(
					modifier = Modifier
						.width(50.dp)
						.fillMaxHeight()
						.clip(RoundedCornerShape(6.dp))
						.background(Color.Gray.copy(alpha = 0.3f)),
				) {
					if (!item.posterPath.isNullOrBlank()) {
						AsyncImage(
							modifier = Modifier.fillMaxSize(),
							url = item.posterPath,
							aspectRatio = 2f / 3f,
							scaleType = ImageView.ScaleType.CENTER_CROP,
						)
					} else {
						Box(
							modifier = Modifier.fillMaxSize(),
							contentAlignment = Alignment.Center,
						) {
							androidx.compose.foundation.Image(
								imageVector = ImageVector.vectorResource(id = R.drawable.ic_clapperboard),
								contentDescription = null,
								modifier = Modifier.size(20.dp),
								colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF888888)),
							)
						}
					}
				}
			}
		}
	}
}


private val JellyseerrStudioCards = listOf(
	JellyseerrCompany(
		id = 2,
		name = "Disney",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wdrCwmRnLFJhEoH8GSfymY85KHT.png",
	),
	JellyseerrCompany(
		id = 127928,
		name = "20th Century Studios",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/h0rjX5vjW5r8yEnUBStFarjcLT4.png",
	),
	JellyseerrCompany(
		id = 34,
		name = "Sony Pictures",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/GagSvqWlyPdkFHMfQ3pNq6ix9P.png",
	),
	JellyseerrCompany(
		id = 174,
		name = "Warner Bros. Pictures",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ky0xOc5OrhzkZ1N6KyUxacfQsCk.png",
	),
	JellyseerrCompany(
		id = 33,
		name = "Universal",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8lvHyhjr8oUKOOy2dKXoALWKdp0.png",
	),
	JellyseerrCompany(
		id = 4,
		name = "Paramount",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fycMZt242LVjagMByZOLUGbCvv3.png",
	),
	JellyseerrCompany(
		id = 3,
		name = "Pixar",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1TjvGVDMYsj6JBxOAkUHpPEwLf7.png",
	),
	JellyseerrCompany(
		id = 521,
		name = "Dreamworks",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/kP7t6RwGz2AvvTkvnI1uteEwHet.png",
	),
	JellyseerrCompany(
		id = 420,
		name = "Marvel Studios",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/hUzeosd33nzE5MCNsZxCGEKTXaQ.png",
	),
	JellyseerrCompany(
		id = 9993,
		name = "DC",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/2Tc1P3Ac8M479naPp1kYT3izLS5.png",
	),
	JellyseerrCompany(
		id = 41077,
		name = "A24",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1ZXsGaFPgrgS6ZZGS37AqD5uU12.png",
	),
)

private val JellyseerrNetworkCards = listOf(
	JellyseerrCompany(
		id = 213,
		name = "Netflix",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/wwemzKWzjKYJFfCeiB57q3r4Bcm.png",
	),
	JellyseerrCompany(
		id = 2739,
		name = "Disney+",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/gJ8VX6JSu3ciXHuC2dDGAo2lvwM.png",
	),
	JellyseerrCompany(
		id = 1024,
		name = "Prime Video",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ifhbNuuVnlwYy5oXA5VIb2YR8AZ.png",
	),
	JellyseerrCompany(
		id = 2552,
		name = "Apple TV+",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/4KAy34EHvRM25Ih8wb82AuGU7zJ.png",
	),
	JellyseerrCompany(
		id = 453,
		name = "Hulu",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/pqUTCleNUiTLAVlelGxUgWn1ELh.png",
	),
	JellyseerrCompany(
		id = 49,
		name = "HBO",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/tuomPhY2UtuPTqqFnKMVHvSb724.png",
	),
	JellyseerrCompany(
		id = 4353,
		name = "Discovery+",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1D1bS3Dyw4ScYnFWTlBOvJXC3nb.png",
	),
	JellyseerrCompany(
		id = 2,
		name = "ABC",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ndAvF4JLsliGreX87jAc9GdjmJY.png",
	),
	JellyseerrCompany(
		id = 19,
		name = "FOX",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/1DSpHrWyOORkL9N2QHX7Adt31mQ.png",
	),
	JellyseerrCompany(
		id = 359,
		name = "Cinemax",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/6mSHSquNpfLgDdv6VnOOvC5Uz2h.png",
	),
	JellyseerrCompany(
		id = 174,
		name = "AMC",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/pmvRmATOCaDykE6JrVoeYxlFHw3.png",
	),
	JellyseerrCompany(
		id = 67,
		name = "Showtime",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/Allse9kbjiP6ExaQrnSpIhkurEi.png",
	),
	JellyseerrCompany(
		id = 318,
		name = "Starz",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/8GJjw3HHsAJYwIWKIPBPfqMxlEa.png",
	),
	JellyseerrCompany(
		id = 71,
		name = "The CW",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ge9hzeaU7nMtQ4PjkFlc68dGAJ9.png",
	),
	JellyseerrCompany(
		id = 6,
		name = "NBC",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/o3OedEP0f9mfZr33jz2BfXOUK5.png",
	),
	JellyseerrCompany(
		id = 16,
		name = "CBS",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/nm8d7P7MJNiBLdgIzUK0gkuEA4r.png",
	),
	JellyseerrCompany(
		id = 4330,
		name = "Paramount+",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/fi83B1oztoS47xxcemFdPMhIzK.png",
	),
	JellyseerrCompany(
		id = 4,
		name = "BBC One",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/mVn7xESaTNmjBUyUtGNvDQd3CT1.png",
	),
	JellyseerrCompany(
		id = 56,
		name = "Cartoon Network",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/c5OC6oVCg6QP4eqzW6XIq17CQjI.png",
	),
	JellyseerrCompany(
		id = 80,
		name = "Adult Swim",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/9AKyspxVzywuaMuZ1Bvilu8sXly.png",
	),
	JellyseerrCompany(
		id = 13,
		name = "Nickelodeon",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/ikZXxg6GnwpzqiZbRPhJGaZapqB.png",
	),
	JellyseerrCompany(
		id = 3353,
		name = "Peacock",
		logoUrl = "https://image.tmdb.org/t/p/w780_filter(duotone,ffffff,bababa)/gIAcGTjKKr0KOHL5s4O36roJ8p7.png",
	),
)
