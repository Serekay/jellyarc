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
import org.jellyfin.androidtv.ui.jellyseerr.JellyseerrSearchFilter
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
	val view = LocalView.current
	val allTrendsListState = rememberLazyListState()
	val sectionSpacing = 5.dp // Abstand zwischen Sektionen
	val sectionInnerSpacing = 6.dp // Abstand innerhalb einer Sektion (label + Inhalt)
	val sectionTitleFontSize = 26.sp
	val itemFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
	val viewAllFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
	val lastRestoredFocus = remember { mutableStateOf<Pair<String?, String?>>(null to null) }
	val itemFocusKey: (String, Int) -> String = { row, id -> "$row-$id" }
	var isSearchButtonFocused by remember { mutableStateOf(false) }
	var suppressRestore by remember { mutableStateOf(false) }

	LaunchedEffect(
		state.selectedItem,
		state.showAllTrendsGrid,
		state.selectedPerson,
		state.lastFocusedItemId,
		state.lastFocusedViewAllKey,
		isSearchButtonFocused,
	) {
		val browsing = state.selectedItem == null &&
			state.selectedPerson == null &&
			!state.showAllTrendsGrid &&
			!isSearchButtonFocused

		if (!browsing) {
			lastRestoredFocus.value = null to null
			return@LaunchedEffect
		}

		if (isSearchButtonFocused || suppressRestore) {
			lastRestoredFocus.value = null to null
			return@LaunchedEffect
		}

		if (suppressRestore) return@LaunchedEffect

		val targetPair = state.lastFocusedItemId to state.lastFocusedViewAllKey
		if (lastRestoredFocus.value == targetPair) return@LaunchedEffect

		// Längerer Delay um sicherzustellen, dass Scroll-Animation abgeschlossen ist
		delay(400)

		if (isSearchButtonFocused || suppressRestore) return@LaunchedEffect

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
				state.showAllTrendsGrid,
	) {
		when {
			state.selectedItem != null -> viewModel.closeDetails()
			state.selectedPerson != null -> viewModel.closePerson()
			state.showAllTrendsGrid -> viewModel.closeAllTrends()
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
		val isShowingGrid = state.showAllTrendsGrid

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
		) {
			val isBrowsing = state.selectedItem == null &&
				state.selectedPerson == null &&
				!state.showAllTrendsGrid

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
			// Moderner Search-Button statt Suchleiste
			if (!isShowingGrid) {
				val searchButtonInteraction = remember { MutableInteractionSource() }
				val searchButtonFocused by searchButtonInteraction.collectIsFocusedAsState()
				val wasSearchButtonFocused = remember { mutableStateOf(false) }

				Box(
					modifier = Modifier
						.padding(bottom = 16.dp)
						.clip(RoundedCornerShape(16.dp))
						.background(Color.Transparent)
						.border(
							width = if (searchButtonFocused) 3.dp else 1.dp,
							color = if (searchButtonFocused) Color.White else Color.White.copy(alpha = 0.2f),
							shape = RoundedCornerShape(16.dp)
						)
						.clickable(
							interactionSource = searchButtonInteraction,
							indication = null
						) { viewModel.enterSearchMode() }
						.focusable(interactionSource = searchButtonInteraction)
						.onFocusChanged { focusState ->
							isSearchButtonFocused = focusState.isFocused
							if (focusState.isFocused) {
								suppressRestore = true
								lastRestoredFocus.value = null to null
								viewModel.updateLastFocusedItem(null)
								viewModel.updateLastFocusedViewAll(null)
							} else {
								suppressRestore = false
							}
						}
						.padding(horizontal = 24.dp, vertical = 16.dp)
				) {
					Row(
						horizontalArrangement = Arrangement.spacedBy(16.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						androidx.compose.foundation.Image(
							imageVector = ImageVector.vectorResource(id = R.drawable.ic_search),
							contentDescription = null,
							modifier = Modifier.size(28.dp),
							colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
						)
						Text(
							text = stringResource(R.string.jellyseerr_search_button),
							color = Color.White,
							fontSize = 20.sp,
							fontWeight = if (searchButtonFocused) FontWeight.Bold else FontWeight.Medium
						)
					}
				}

				LaunchedEffect(searchButtonFocused) {
					// Only play sound when focus changes from unfocused to focused
					if (searchButtonFocused && !wasSearchButtonFocused.value) {
						view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
					}
					wasSearchButtonFocused.value = searchButtonFocused
				}
			}

			val shouldShowError = state.errorMessage?.contains("HTTP 400", ignoreCase = true) != true
			if (state.errorMessage != null && shouldShowError) {
				Text(
					text = stringResource(R.string.jellyseerr_error_prefix, state.errorMessage ?: ""),
					color = Color.Red,
					modifier = Modifier.padding(bottom = 16.dp),
				)
			}

			if (isShowingGrid) {
				val headerText = state.discoverTitle?.takeIf { it.isNotBlank() }
					?: stringResource(state.discoverCategory.titleResId)
				Text(text = headerText, color = Color.White, fontSize = sectionTitleFontSize)

				val isCategoryScreen = state.discoverCategory in setOf(
					JellyseerrDiscoverCategory.MOVIE_GENRE,
					JellyseerrDiscoverCategory.TV_GENRE,
					JellyseerrDiscoverCategory.MOVIE_STUDIOS,
					JellyseerrDiscoverCategory.TV_NETWORKS,
				)

				val gridResults = when {
					state.showAllTrendsGrid -> state.results
					isCategoryScreen -> state.results
					else -> state.results.take(20)
				}

				if (gridResults.isEmpty() && !state.isLoading) {
					Text(
						text = stringResource(R.string.jellyseerr_no_results),
						modifier = Modifier.padding(vertical = 8.dp),
					)
				} else {
					val categorizedResults = listOf(Triple("", gridResults, true))

					LazyColumn(
						state = allTrendsListState,
						modifier = Modifier
							.fillMaxSize()
							.padding(top = 8.dp),
					) {
						categorizedResults.forEach { (categoryTitle, categoryItems, showCategory) ->
							if (showCategory) {
								// Category items in rows
								val rows = categoryItems.chunked(5)
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
											)
										}
									}

									if (rowIndex == rows.lastIndex && !state.isLoading) {
										if (state.showAllTrendsGrid && state.discoverHasMore) {
											LaunchedEffect(key1 = rows.size) {
												viewModel.loadMoreTrends()
											}
										}
									}
								}
							}
						}
					}
				}
			} else {
				// Browse mode - zeige Discover-Inhalte
				if (true) {
					val titleRes = R.string.jellyseerr_discover_title
					Text(text = stringResource(titleRes), color = JellyfinTheme.colorScheme.onBackground, fontSize = sectionTitleFontSize)

					val baseResults = state.trendingResults.take(20)

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
						// Show "View All" card
						val showViewAllCard = true
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
									JellyseerrViewAllCard(
										onClick = { viewModel.showAllTrends() },
										posterUrls = posterUrls,
										focusRequester = focusRequesterForViewAll(VIEW_ALL_TRENDING),
										onFocus = { viewModel.updateLastFocusedViewAll(VIEW_ALL_TRENDING) },
									)
								}
							}
						}
					}
					}
				}

				// Beliebte Filme
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrContentSection(
						title = stringResource(R.string.jellyseerr_popular_title),
						items = state.popularResults,
						scrollKey = "popular",
						scrollPosition = state.scrollPositions["popular"],
						onScrollPositionChange = { key, index, offset ->
							viewModel.saveScrollPosition(key, index, offset)
						},
						onItemClick = { item -> viewModel.showDetailsForItem(item) },
						onViewAllClick = { viewModel.showAllPopularMovies() },
						viewAllKey = VIEW_ALL_POPULAR_MOVIES,
						itemFocusKey = { id -> itemFocusKey("popular_movies", id) },
						focusRequesterForItem = focusRequesterForItem,
						focusRequesterForViewAll = focusRequesterForViewAll,
						onItemFocused = { key -> viewModel.updateLastFocusedItem(key) },
						onViewAllFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
						contentHash = state.contentHashes["popular"],
					)
				}

				// Beliebte Serien
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrContentSection(
						title = stringResource(R.string.jellyseerr_popular_tv_title),
						items = state.popularTvResults,
						scrollKey = "popular_tv",
						scrollPosition = state.scrollPositions["popular_tv"],
						onScrollPositionChange = { key, index, offset ->
							viewModel.saveScrollPosition(key, index, offset)
						},
						onItemClick = { item -> viewModel.showDetailsForItem(item) },
						onViewAllClick = { viewModel.showAllPopularTv() },
						viewAllKey = VIEW_ALL_POPULAR_TV,
						itemFocusKey = { id -> itemFocusKey("popular_tv", id) },
						focusRequesterForItem = focusRequesterForItem,
						focusRequesterForViewAll = focusRequesterForViewAll,
						onItemFocused = { key -> viewModel.updateLastFocusedItem(key) },
						onViewAllFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
						contentHash = state.contentHashes["popularTv"],
					)
				}


				// Demnächst erscheinende Filme
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrContentSection(
						title = stringResource(R.string.jellyseerr_upcoming_movies_title),
						items = state.upcomingMovieResults,
						scrollKey = "upcoming_movies",
						scrollPosition = state.scrollPositions["upcoming_movies"],
						onScrollPositionChange = { key, index, offset ->
							viewModel.saveScrollPosition(key, index, offset)
						},
						onItemClick = { item -> viewModel.showDetailsForItem(item) },
						onViewAllClick = { viewModel.showAllUpcomingMovies() },
						viewAllKey = VIEW_ALL_UPCOMING_MOVIES,
						itemFocusKey = { id -> itemFocusKey("upcoming_movies", id) },
						focusRequesterForItem = focusRequesterForItem,
						focusRequesterForViewAll = focusRequesterForViewAll,
						onItemFocused = { key -> viewModel.updateLastFocusedItem(key) },
						onViewAllFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
						contentHash = state.contentHashes["upcomingMovies"],
					)
				}

				// Demnächst erscheinende Serien
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrContentSection(
						title = stringResource(R.string.jellyseerr_upcoming_tv_title),
						items = state.upcomingTvResults,
						scrollKey = "upcoming_tv",
						scrollPosition = state.scrollPositions["upcoming_tv"],
						onScrollPositionChange = { key, index, offset ->
							viewModel.saveScrollPosition(key, index, offset)
						},
						onItemClick = { item -> viewModel.showDetailsForItem(item) },
						onViewAllClick = { viewModel.showAllUpcomingTv() },
						viewAllKey = VIEW_ALL_UPCOMING_TV,
						itemFocusKey = { id -> itemFocusKey("upcoming_tv", id) },
						focusRequesterForItem = focusRequesterForItem,
						focusRequesterForViewAll = focusRequesterForViewAll,
						onItemFocused = { key -> viewModel.updateLastFocusedItem(key) },
						onViewAllFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
						contentHash = state.contentHashes["upcomingTv"],
					)
				}

				// Film-Genres
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrGenreSection(
						title = stringResource(R.string.jellyseerr_movie_genres_title),
						genres = state.movieGenres,
						onGenreClick = { genre -> viewModel.showMovieGenre(genre) },
						genreFocusKey = { id -> "movie_genre_$id" },
						focusRequesterForGenre = focusRequesterForViewAll,
						onGenreFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
					)
				}

				// Serien-Genres
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrGenreSection(
						title = stringResource(R.string.jellyseerr_tv_genres_title),
						genres = state.tvGenres,
						onGenreClick = { genre -> viewModel.showTvGenre(genre) },
						genreFocusKey = { id -> "tv_genre_$id" },
						focusRequesterForGenre = focusRequesterForViewAll,
						onGenreFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
					)
				}

				// Filmstudios
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrCompanySection(
						title = stringResource(R.string.jellyseerr_movie_studios_title),
						companies = JellyseerrStudioCards,
						onCompanyClick = { studio -> viewModel.showMovieStudio(studio) },
						companyFocusKey = { id -> "movie_studio_$id" },
						focusRequesterForCompany = focusRequesterForViewAll,
						onCompanyFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
					)
				}

				// Sender
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrCompanySection(
						title = stringResource(R.string.jellyseerr_tv_networks_title),
						companies = JellyseerrNetworkCards,
						onCompanyClick = { network -> viewModel.showTvNetwork(network) },
						companyFocusKey = { id -> "tv_network_$id" },
						focusRequesterForCompany = focusRequesterForViewAll,
						onCompanyFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
					)
				}

				// Bisherige Anfragen (eigene Anfragen)
				if (state.selectedItem == null && state.selectedPerson == null) {
					JellyseerrRecentRequestsSection(
						title = stringResource(R.string.jellyseerr_recent_requests_title),
						requests = state.recentRequests,
						onRequestClick = { item -> viewModel.showDetailsForItem(item) },
						requestFocusKey = { index -> "recent_request_$index" },
						focusRequesterForRequest = focusRequesterForViewAll,
						onRequestFocused = { key -> viewModel.updateLastFocusedViewAll(key) },
						sectionSpacing = sectionSpacing,
						sectionInnerSpacing = sectionInnerSpacing,
						sectionTitleFontSize = sectionTitleFontSize,
						contentHash = state.contentHashes["recentRequests"],
					)
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
			// Replace flickering backdrops with a stable gradient background
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.verticalGradient(
							colors = listOf(
								Color(0xFF0F172A),
								Color(0xFF111827),
								Color(0xFF0B1220),
							),
						),
					),
			)

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
