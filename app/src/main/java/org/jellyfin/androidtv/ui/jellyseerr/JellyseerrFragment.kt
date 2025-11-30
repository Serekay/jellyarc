package org.jellyfin.androidtv.ui.jellyseerr

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import timber.log.Timber
import org.jellyfin.androidtv.data.repository.JellyseerrMovieDetails
import org.jellyfin.androidtv.data.repository.JellyseerrEpisode
import org.jellyfin.androidtv.data.repository.JellyseerrRequest
import org.jellyfin.androidtv.data.repository.JellyseerrSearchItem
import org.jellyfin.androidtv.data.repository.JellyseerrCast
import org.jellyfin.androidtv.data.repository.JellyseerrPersonDetails
import org.jellyfin.androidtv.data.repository.JellyseerrCompany
import org.jellyfin.androidtv.data.repository.JellyseerrGenreSlider
import org.jellyfin.androidtv.data.repository.JellyseerrGenre
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import org.jellyfin.androidtv.util.sdk.TrailerUtils
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.Icon as MaterialIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import org.koin.androidx.viewmodel.ext.android.viewModel


class JellyseerrFragment : Fragment() {
	private val viewModel: JellyseerrViewModel by viewModel()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			JellyseerrScreen(viewModel = viewModel)
		}
	}

	override fun onResume() {
		super.onResume()
		// Restore detail overlay when coming back from external trailer playback
		viewModel.restoreOverlayAfterExternalPlayback()
	}
}

@Composable
private fun JellyseerrScreen(
	viewModel: JellyseerrViewModel = koinViewModel(),
) {
	val userPreferences = koinInject<UserPreferences>()
	val url = userPreferences[UserPreferences.jellyseerrUrl]
	val apiKey = userPreferences[UserPreferences.jellyseerrApiKey]
	val state by viewModel.uiState.collectAsState()
	var showSeasonDialog by remember { mutableStateOf(false) }
	val firstCastFocusRequester = remember { FocusRequester() }

	// Backdrop Rotation State
	var currentBackdropUrl by remember { mutableStateOf<String?>(null) }

	// Rotiere Backdrop alle 10 Sekunden
	LaunchedEffect(state.results) {
		while (true) {
			val itemsWithBackdrop = state.results
				.take(20)
				.mapNotNull { it.backdropPath }
				.filter { it.isNotBlank() }

			if (itemsWithBackdrop.isNotEmpty()) {
				currentBackdropUrl = itemsWithBackdrop.random()
			}

			delay(10000)
		}
	}

	// Dialog schließen wenn selectedItem sich ändert (z.B. beim Zurücknavigieren)
	LaunchedEffect(state.selectedItem) {
		if (state.selectedItem == null) {
			showSeasonDialog = false
		}
	}

	// Fokussiere ersten Cast-Eintrag wenn Dialog geschlossen wird
	LaunchedEffect(showSeasonDialog) {
		if (!showSeasonDialog && state.selectedItem != null) {
			kotlinx.coroutines.delay(100)
			try {
				firstCastFocusRequester.requestFocus()
			} catch (e: IllegalStateException) {
				// Ignore if no focusable found
			}
		}
	}

	// BackHandler für Dialog
	BackHandler(enabled = showSeasonDialog) {
		showSeasonDialog = false
	}

	Box(modifier = Modifier.fillMaxSize()) {
		val toastMessage = state.requestStatusMessage
		val isError = toastMessage?.contains(stringResource(R.string.jellyseerr_request_error), ignoreCase = true) == true

		LaunchedEffect(toastMessage) {
			if (toastMessage != null) {
				delay(3000)
				viewModel.clearRequestStatus()
			}
		}

		        val toastAlpha by animateFloatAsState(
            targetValue = if (toastMessage != null) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
        )
        val selectedItem = state.selectedItem
        val isSearching = state.query.isNotBlank() || state.showSearchResultsGrid
        if (selectedItem != null) {
            val backdropUrl = state.selectedMovie?.backdropPath ?: selectedItem.backdropPath

            if (!backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = 0.4f),
                    url = backdropUrl,
                    aspectRatio = 16f / 9f,
                )
            }
        } else if (state.selectedPerson == null) {
            if (isSearching) {
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
            } else {
                androidx.compose.animation.Crossfade(
                    targetState = currentBackdropUrl,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000),
                    label = "backdrop_crossfade"
                ) { backdropUrl ->
                    if (backdropUrl != null) {
                        AsyncImage(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = 0.4f),
                            url = backdropUrl,
                            aspectRatio = 16f / 9f,
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP,
                        )
                    }
                }
            }
        }

		Column(modifier = Modifier.fillMaxSize()) {
			val state by viewModel.uiState.collectAsState()

			// Show toolbar in normal browse mode or when viewing details/person
			// Hide toolbar only in grid view when not viewing details
			val showToolbar = state.selectedItem != null ||
				state.selectedPerson != null ||
				(!state.showAllTrendsGrid && !state.showSearchResultsGrid)

			if (showToolbar) {
				MainToolbar(MainToolbarActiveButton.Requests)
			}

			if (url.isBlank() || apiKey.isBlank()) {
				Text(
					text = stringResource(R.string.jellyseerr_pref_url_missing),
					modifier = Modifier.padding(24.dp),
					color = Color.White,
				)
			} else {
				// Box um Browse und Detail-Ansichten zu überlagern
				Box(modifier = Modifier.fillMaxSize()) {
					// Browse-Ansicht (JellyseerrContent) - bleibt immer im Tree
					JellyseerrContent(
						viewModel = viewModel,
						onShowSeasonDialog = { showSeasonDialog = true },
						firstCastFocusRequester = firstCastFocusRequester,
					)

					// Detail-Overlay - wird über der Browse-Ansicht angezeigt
					if (selectedItem != null) {
						JellyseerrDetail(
							item = selectedItem,
							details = state.selectedMovie,
							seasonEpisodes = state.seasonEpisodes,
							requestStatusMessage = state.requestStatusMessage,
							onRequestClick = { seasons ->
								viewModel.request(selectedItem, seasons)
							},
							ownRequests = state.ownRequests,
							onShowSeasonDialog = { showSeasonDialog = true },
							onBeforeExternalTrailer = { viewModel.rememberOverlayForExternalPlayback() },
							onCastClick = { cast ->
								viewModel.showPerson(cast)
							},
							onGenreClick = { genre ->
								val slider = JellyseerrGenreSlider(id = genre.id, name = genre.name, backdropUrl = null)
								if (selectedItem.mediaType == "tv") {
									viewModel.showTvGenreFromDetail(slider)
								} else {
									viewModel.showMovieGenreFromDetail(slider)
								}
							},
							onCollectionItemClick = { item -> viewModel.showDetailsForItem(item) },
							firstCastFocusRequester = firstCastFocusRequester,
						)
					}

					// Person-Overlay - wird über der Browse-Ansicht angezeigt
					val selectedPerson = state.selectedPerson
					if (selectedPerson != null) {
						JellyseerrPersonScreen(
							person = selectedPerson,
							credits = state.personCredits,
							onCreditClick = { item -> viewModel.showDetailsForItemFromPerson(item) },
							focusRequesterForItem = { key -> FocusRequester() },
							onItemFocused = { key -> viewModel.updateLastFocusedItem(key) },
						)
					}
				}
			}
		}

		if (toastAlpha > 0f && toastMessage != null) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Box(
					modifier = Modifier
						.graphicsLayer(alpha = toastAlpha)
						.padding(horizontal = 24.dp)
						.background(
							color = if (isError) Color(0xCCB00020) else Color(0xCC00A060),
							shape = RoundedCornerShape(12.dp),
						)
						.border(2.dp, Color.White, RoundedCornerShape(12.dp))
						.padding(horizontal = 16.dp, vertical = 12.dp),
				) {
					Text(text = toastMessage, color = Color.White)
				}
			}
		}
		
		// Season Dialog auf oberster Ebene (über allem anderen)
		if (showSeasonDialog && state.selectedItem != null) {
			val selectedItem = state.selectedItem!!
			val details = state.selectedMovie
			val navigationRepository = koinInject<org.jellyfin.androidtv.ui.navigation.NavigationRepository>()
			val availableSeasons = details?.seasons
				?.filter { it.seasonNumber > 0 }
				?.sortedBy { it.seasonNumber }
				.orEmpty()

			// Automatisch alle Staffel-Episoden laden um Verfügbarkeit zu prüfen
			LaunchedEffect(selectedItem.id, availableSeasons) {
				availableSeasons.forEach { season ->
					val seasonKey = SeasonKey(selectedItem.id, season.seasonNumber)
					if (!state.seasonEpisodes.containsKey(seasonKey)) {
						viewModel.loadSeasonEpisodes(selectedItem.id, season.seasonNumber)
					}
				}
			}

			Dialog(
				onDismissRequest = {
					showSeasonDialog = false
					// Refresh details to sync with Jellyseerr server when dialog closes
					viewModel.refreshCurrentDetails()
				},
				properties = DialogProperties(usePlatformDefaultWidth = false),
			) {
				val firstButtonFocusRequester = remember { FocusRequester() }
				val seasonListState = rememberLazyListState()
				val dialogBackdropUrl = details?.backdropPath ?: selectedItem.backdropPath
				val overlayBrush = Brush.verticalGradient(
					colors = listOf(
						JellyfinTheme.colorScheme.popover.copy(alpha = 0.85f),
						JellyfinTheme.colorScheme.popover.copy(alpha = 0.95f),
					),
				)

				Box(
					modifier = Modifier
						.fillMaxWidth()
						.fillMaxHeight(),
				) {
					if (!dialogBackdropUrl.isNullOrBlank()) {
						AsyncImage(
							modifier = Modifier.matchParentSize(),
							url = dialogBackdropUrl,
							aspectRatio = 16f / 9f,
							scaleType = ImageView.ScaleType.CENTER_CROP,
						)
					} else {
						Box(
							modifier = Modifier
								.matchParentSize()
								.background(JellyfinTheme.colorScheme.popover),
						)
					}

					Box(
						modifier = Modifier
							.matchParentSize()
							.background(overlayBrush),
					)

					Column(
						modifier = Modifier
							.fillMaxSize()
							.padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
					) {
						// Header Row mit Titel und "Alle anfragen" Button
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = stringResource(R.string.jellyseerr_seasons_label),
								color = JellyfinTheme.colorScheme.onBackground,
							)

							// "Alle anfragen" Button im Header
							val unrequestedSeasons = availableSeasons.filter { it.status == null }
							if (unrequestedSeasons.isNotEmpty()) {
								val requestAllInteraction = remember { MutableInteractionSource() }
								val requestAllFocused by requestAllInteraction.collectIsFocusedAsState()

								Button(
									onClick = {
										val seasonsToRequest = unrequestedSeasons.map { it.seasonNumber }
										if (seasonsToRequest.isNotEmpty()) {
											viewModel.request(selectedItem, seasonsToRequest)
										}
										showSeasonDialog = false
									},
									colors = ButtonDefaults.colors(
										containerColor = Color(0xFF9933CC),
										contentColor = Color.White,
										focusedContainerColor = Color(0xFFDD66FF),
										focusedContentColor = Color.Black,
									),
									interactionSource = requestAllInteraction,
									modifier = Modifier.border(
										width = if (requestAllFocused) 3.dp else 0.dp,
										color = Color.White,
										shape = CircleShape
									),
								) {
									Text(text = stringResource(R.string.jellyseerr_request_all_seasons))
								}
							}
						}

						Spacer(modifier = Modifier.size(8.dp))

						val expandedSeasons = remember { mutableStateMapOf<Int, Boolean>() }

						LazyColumn(
							state = seasonListState,
							modifier = Modifier.weight(1f),
							contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
						) {
							itemsIndexed(
								items = availableSeasons,
								key = { _, season -> season.seasonNumber },
							) { index, season ->
								val number = season.seasonNumber
								val episodeCount = season.episodeCount

								// Jellyseerr Status Codes:
								// null = Nicht angefragt
								// 1 = Pending approval, 2 = Approved, 3 = Declined, 4 = Partially Available, 5 = Available
								val jellyseerrStatus = season.status

								val expanded = expandedSeasons[number] == true
								val seasonKey = SeasonKey(selectedItem.id, number)

								// Prüfe tatsächliche Episoden-Verfügbarkeit aus Jellyfin
								val loadedEpisodes = state.seasonEpisodes[seasonKey]
								val availableEpisodesCount = loadedEpisodes?.count { it.isAvailable } ?: 0
								val totalEpisodesCount = loadedEpisodes?.size ?: (episodeCount ?: 0)

								// Verfügbarkeit basierend auf tatsächlichen Episoden aus Jellyfin
								val hasAvailableEpisodes = availableEpisodesCount > 0
								val allEpisodesAvailable = availableEpisodesCount == totalEpisodesCount && totalEpisodesCount > 0

								// Staffel ist verfügbar wenn alle Episoden verfügbar sind
								val isAvailable = allEpisodesAvailable || jellyseerrStatus == 5

								// Staffel ist teilweise verfügbar wenn mindestens eine Episode verfügbar ist
								val isPartiallyAvailable = !isAvailable && (hasAvailableEpisodes || jellyseerrStatus == 4)

								// Staffel ist angefragt wenn status vorhanden ist (1-3) aber nicht verfügbar
								val seasonRequested = !isAvailable && !isPartiallyAvailable && jellyseerrStatus != null && jellyseerrStatus != 5 && jellyseerrStatus != 4 && jellyseerrStatus != 3

								val buttonInteraction = remember { MutableInteractionSource() }
								val buttonFocused by buttonInteraction.collectIsFocusedAsState()

								val buttonColors = when {
									isAvailable -> ButtonDefaults.colors(
										containerColor = Color(0xFF2E7D32),
										contentColor = Color.White,
										focusedContainerColor = Color(0xFF4CAF50),
										focusedContentColor = Color.White,
									)
									isPartiallyAvailable -> ButtonDefaults.colors(
										containerColor = Color(0xFF0097A7),
										contentColor = Color.White,
										focusedContainerColor = Color(0xFF00BCD4),
										focusedContentColor = Color.White,
									)
									seasonRequested -> ButtonDefaults.colors(
										containerColor = Color(0xFF9933CC),
										contentColor = Color.White,
										focusedContainerColor = Color(0xFFDD66FF),
										focusedContentColor = Color.Black,
									)
									else -> ButtonDefaults.colors(
										containerColor = Color(0xFF9933CC),
										contentColor = Color.White,
										focusedContainerColor = Color(0xFFDD66FF),
										focusedContentColor = Color.Black,
									)
								}

								val buttonText = when {
									isAvailable -> stringResource(R.string.lbl_play)
									isPartiallyAvailable -> stringResource(R.string.jellyseerr_partially_available_label)
									seasonRequested -> stringResource(R.string.jellyseerr_requested_label)
									else -> stringResource(R.string.jellyseerr_request_button)
								}

								val buttonModifier = if (index == 0) {
									Modifier.focusRequester(firstButtonFocusRequester)
								} else {
									Modifier
								}

								// Season Card mit vollem Layout
								Column(
									modifier = Modifier
										.fillMaxWidth()
										.padding(vertical = 8.dp)
										.background(
											Color.Black.copy(alpha = 0.4f),
											RoundedCornerShape(12.dp)
										)
										.padding(12.dp),
								) {
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.spacedBy(16.dp),
									) {
										// Season Poster
										Box(
											modifier = Modifier
												.width(100.dp)
												.height(150.dp)
												.clip(RoundedCornerShape(8.dp))
												.background(Color.Gray.copy(alpha = 0.3f)),
										) {
											if (!season.posterPath.isNullOrBlank()) {
												AsyncImage(
													modifier = Modifier.fillMaxSize(),
													url = season.posterPath,
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
														modifier = Modifier.size(40.dp),
														colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF888888)),
													)
												}
											}

											// Status Badge
											when {
												isAvailable -> {
													Box(
														modifier = Modifier
															.align(Alignment.TopEnd)
															.padding(4.dp)
															.clip(RoundedCornerShape(999.dp))
															.background(Color(0xFF2E7D32)),
													) {
														androidx.compose.foundation.Image(
															imageVector = ImageVector.vectorResource(id = R.drawable.ic_check),
															contentDescription = null,
															modifier = Modifier
																.padding(4.dp)
																.size(16.dp),
														)
													}
												}
												seasonRequested -> {
													Box(
														modifier = Modifier
															.align(Alignment.TopEnd)
															.padding(4.dp)
															.clip(RoundedCornerShape(999.dp))
															.background(Color(0xFF9933CC)),
													) {
														androidx.compose.foundation.Image(
															imageVector = ImageVector.vectorResource(id = R.drawable.ic_time),
															contentDescription = null,
															modifier = Modifier
																.padding(4.dp)
																.size(16.dp),
														)
													}
												}
											}
										}

										// Season Details
										Column(
											modifier = Modifier.weight(1f),
											verticalArrangement = Arrangement.spacedBy(6.dp),
										) {
											// Season Title
											Text(
												text = season.name ?: "Staffel $number",
												color = JellyfinTheme.colorScheme.onBackground,
												fontSize = 18.sp,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis,
											)

											// Meta Info Row
											Row(
												horizontalArrangement = Arrangement.spacedBy(8.dp),
												verticalAlignment = Alignment.CenterVertically,
											) {
												if (episodeCount != null && episodeCount > 0) {
													Box(
														modifier = Modifier
															.clip(RoundedCornerShape(999.dp))
															.background(JellyfinTheme.colorScheme.badge)
															.padding(horizontal = 8.dp, vertical = 4.dp),
													) {
														Text(
															text = stringResource(
																R.string.jellyseerr_episodes_count,
																episodeCount,
															),
															color = JellyfinTheme.colorScheme.onBadge,
															fontSize = 12.sp,
														)
													}
												}

												if (!season.airDate.isNullOrBlank()) {
													Text(
														text = season.airDate.take(10),
														color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
														fontSize = 12.sp,
													)
												}
											}

											// Overview
											if (!season.overview.isNullOrBlank()) {
												Text(
													text = season.overview,
													color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.8f),
													fontSize = 12.sp,
													maxLines = 3,
													overflow = TextOverflow.Ellipsis,
												)
											}

											Spacer(modifier = Modifier.weight(1f))

											// Action Button
											Button(
												onClick = {
													when {
														isAvailable && selectedItem.jellyfinId != null -> {
															// Direkt zum Jellyfin Content navigieren
															val uuid = selectedItem.jellyfinId.toUUIDOrNull()
															if (uuid != null) {
																navigationRepository.navigate(
																	org.jellyfin.androidtv.ui.navigation.Destinations.itemDetails(uuid),
																)
																showSeasonDialog = false
															} else {
																navigationRepository.navigate(
																	org.jellyfin.androidtv.ui.navigation.Destinations.search(
																		selectedItem.title,
																	),
																)
																showSeasonDialog = false
															}
														}
														isAvailable -> {
															// Fallback: Suche öffnen
															navigationRepository.navigate(
																org.jellyfin.androidtv.ui.navigation.Destinations.search(
																	selectedItem.title,
																),
															)
															showSeasonDialog = false
														}
														seasonRequested || isPartiallyAvailable -> {
															// Bereits angefragt oder teilweise verfügbar - keine Aktion (disabled)
														}
														else -> {
															viewModel.request(selectedItem, listOf(number))
															showSeasonDialog = false
														}
													}
												},
												enabled = !seasonRequested && !isPartiallyAvailable,
												colors = buttonColors,
												interactionSource = buttonInteraction,
												modifier = buttonModifier
													.border(
														width = if (buttonFocused) 3.dp else 0.dp,
														color = Color.White,
														shape = CircleShape
													),
											) {
												if (isAvailable) {
													MaterialIcon(
														imageVector = ImageVector.vectorResource(id = R.drawable.ic_play),
														contentDescription = stringResource(R.string.lbl_play),
													)
												} else {
													Text(
														text = buttonText,
														textAlign = TextAlign.Center,
													)
												}
											}
										}
									}

									// Expandable Episodes Toggle
									val rowInteractionSource = remember { MutableInteractionSource() }
									var rowFocused by remember { mutableStateOf(false) }
									val expandRowBackground = if (rowFocused) {
										JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.5f)
									} else {
										Color.Transparent
									}

									Spacer(modifier = Modifier.height(8.dp))

									Row(
										modifier = Modifier
											.fillMaxWidth()
											.onFocusChanged { rowFocused = it.isFocused }
											.clickable(
												interactionSource = rowInteractionSource,
												indication = null,
											) {
												expandedSeasons[number] = !expanded
											}
											.background(expandRowBackground, RoundedCornerShape(8.dp))
											.padding(horizontal = 8.dp, vertical = 6.dp),
										horizontalArrangement = Arrangement.spacedBy(8.dp),
										verticalAlignment = Alignment.CenterVertically,
									) {
										val indicatorText = if (expanded) "▾" else "▸"
										Text(
											text = indicatorText,
											color = JellyfinTheme.colorScheme.onBackground,
											fontSize = 16.sp,
										)
										Text(
											text = if (expanded) "Episoden ausblenden" else "Episoden anzeigen",
											color = JellyfinTheme.colorScheme.onBackground,
											fontSize = 14.sp,
										)
									}
								}

								if (expanded) {
									LaunchedEffect(expanded, seasonKey) {
										if (expanded) {
											viewModel.loadSeasonEpisodes(selectedItem.id, number)
										}
									}

									val episodes = state.seasonEpisodes[seasonKey]
									val isLoadingEpisodes = state.loadingSeasonKeys.contains(seasonKey)
									val seasonError = state.seasonErrors[seasonKey]

									Column(
										modifier = Modifier
											.fillMaxWidth()
											.padding(start = 32.dp, end = 4.dp),
										verticalArrangement = Arrangement.spacedBy(8.dp),
									) {
										when {
											isLoadingEpisodes -> {
												Text(
													text = stringResource(R.string.loading),
													color = JellyfinTheme.colorScheme.onBackground,
												)
											}
											!seasonError.isNullOrBlank() -> {
												Text(
													text = seasonError,
													color = Color.Red,
												)
											}
											episodes.isNullOrEmpty() -> {
												Text(
													text = stringResource(R.string.jellyseerr_no_episodes),
													color = JellyfinTheme.colorScheme.onBackground,
												)
											}
											else -> {
												val episodeView = LocalView.current
												episodes.forEach { episode ->
													val episodeInteraction = remember(episode.id) { MutableInteractionSource() }
													val episodeFocused by episodeInteraction.collectIsFocusedAsState()
													val episodeBackground = if (episodeFocused) {
														JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.3f)
													} else {
														Color.Transparent
													}

													// Sound beim Fokussieren
													LaunchedEffect(episodeFocused) {
														if (episodeFocused) {
															episodeView.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
														}
													}

													val episodeClickHandler = if (episode.isAvailable && episode.jellyfinId != null) {
														{
															val uuid = episode.jellyfinId.toUUIDOrNull()
															if (uuid != null) {
																navigationRepository.navigate(
																	org.jellyfin.androidtv.ui.navigation.Destinations.itemDetails(uuid),
																)
															} else {
																navigationRepository.navigate(
																	org.jellyfin.androidtv.ui.navigation.Destinations.search(
																		"${selectedItem.title} ${episode.name.orEmpty()}",
																	),
																)
															}
														}
													} else {
														{ /* Nicht klickbar */ }
													}

													JellyseerrEpisodeRow(
														episode = episode,
														modifier = Modifier
															.fillMaxWidth()
															.padding(vertical = 4.dp)
															.clickable(
																interactionSource = episodeInteraction,
																indication = null,
																onClick = episodeClickHandler,
															),
														backgroundColor = episodeBackground,
														onClick = null, // onClick wird jetzt über Modifier gesteuert
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

				// Fokus auf ersten Button setzen wenn Dialog geöffnet wird
				LaunchedEffect(Unit) {
					// Stelle sicher, dass die Liste am Anfang ist
					seasonListState.scrollToItem(0)
					kotlinx.coroutines.delay(100)
					firstButtonFocusRequester.requestFocus()
					// Nach Fokus nochmals zum Anfang scrollen um sicherzustellen, dass erste Staffel sichtbar ist
					kotlinx.coroutines.delay(50)
					seasonListState.animateScrollToItem(0)
				}
			}
		}
	}
}

