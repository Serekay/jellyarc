package org.jellyfin.androidtv.ui.jellyseerr

import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon as MaterialIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.JellyseerrCast
import org.jellyfin.androidtv.data.repository.JellyseerrEpisode
import org.jellyfin.androidtv.data.repository.JellyseerrGenre
import org.jellyfin.androidtv.data.repository.JellyseerrMovieDetails
import org.jellyfin.androidtv.data.repository.JellyseerrPersonDetails
import org.jellyfin.androidtv.data.repository.JellyseerrRequest
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.data.repository.JellyseerrSearchItem
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.jellyseerr.JellyseerrRequestMarkers.markItemsWithRequests
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.compose.koinInject
import java.util.Locale

@Composable
internal fun JellyseerrEpisodeRow(
		episode: JellyseerrEpisode,
		modifier: Modifier = Modifier,
		backgroundColor: Color = Color.Transparent,
		onClick: (() -> Unit)? = null,
	) {
	val titleParts = buildList {
		episode.episodeNumber?.let { add(stringResource(R.string.lbl_episode_number, it)) }
		if (!episode.name.isNullOrBlank()) add(episode.name)
	}
	val titleText = titleParts.joinToString(" – ").ifBlank { stringResource(R.string.jellyseerr_episode_title_missing) }

	val rowModifier = if (onClick != null) {
		modifier
			.clickable { onClick() }
			.background(backgroundColor, RoundedCornerShape(8.dp))
			.padding(8.dp)
	} else {
		modifier
			.background(backgroundColor, RoundedCornerShape(8.dp))
			.padding(8.dp)
	}

	Row(
		modifier = rowModifier,
		verticalAlignment = Alignment.Top,
		horizontalArrangement = Arrangement.Start,
	) {
		val thumbnailModifier = Modifier
			.size(110.dp)
			.clip(RoundedCornerShape(8.dp))

		if (!episode.imageUrl.isNullOrBlank()) {
			AsyncImage(
				modifier = thumbnailModifier,
				url = episode.imageUrl,
				aspectRatio = 16f / 9f,
				scaleType = ImageView.ScaleType.CENTER_CROP,
			)
		} else {
			Box(
				modifier = thumbnailModifier
					.background(Color(0xFF333333)),
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

		Spacer(modifier = Modifier.size(12.dp))

		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(
				text = titleText,
				color = JellyfinTheme.colorScheme.onBackground,
			)

			if (!episode.overview.isNullOrBlank()) {
				Text(
					text = episode.overview,
					color = JellyfinTheme.colorScheme.onBackground,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
				)
			}

			// Status Badge: Available oder Missing
			when {
				episode.isAvailable -> {
					Box(
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.background(Color(0xFF2E7D32))
							.padding(horizontal = 8.dp, vertical = 2.dp),
					) {
						Text(
							text = stringResource(R.string.jellyseerr_available_label),
							color = Color.White,
							fontSize = 12.sp,
						)
					}
				}
				episode.isMissing -> {
					Box(
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.background(JellyfinTheme.colorScheme.badge)
							.padding(horizontal = 8.dp, vertical = 2.dp),
					) {
						Text(
							text = stringResource(R.string.jellyseerr_episode_missing_badge),
							color = JellyfinTheme.colorScheme.onBadge,
							fontSize = 12.sp,
						)
					}
				}
				else -> {
					// Nicht verfügbar und nicht explizit als missing markiert
					Box(
						modifier = Modifier
							.clip(RoundedCornerShape(999.dp))
							.background(Color(0xFFD32F2F))
							.padding(horizontal = 8.dp, vertical = 2.dp),
					) {
						Text(
							text = stringResource(R.string.jellyseerr_not_available_label),
							color = Color.White,
							fontSize = 12.sp,
						)
					}
				}
			}
		}
	}
}


@Composable
internal fun JellyseerrPersonScreen(
    person: JellyseerrPersonDetails,
    credits: List<JellyseerrSearchItem>,
    onCreditClick: (JellyseerrSearchItem) -> Unit,
    focusRequesterForItem: (String) -> FocusRequester,
    onItemFocused: (String) -> Unit,
) {
    val firstCreditFocusRequester = remember { FocusRequester() }

    // Try to put focus on the first credit card when opening the person screen
    LaunchedEffect(person.id, credits.size) {
        if (credits.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            firstCreditFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    url = person.profilePath,
                    aspectRatio = 1f,
                    scaleType = ImageView.ScaleType.CENTER_CROP,
                )
            }

            Column {
                Text(person.name, color = JellyfinTheme.colorScheme.onBackground)
                person.knownForDepartment.takeIf { !it.isNullOrBlank() }?.let {
                    Text(it, color = JellyfinTheme.colorScheme.onBackground)
                }
                person.placeOfBirth.takeIf { !it.isNullOrBlank() }?.let {
                    Text(it, color = JellyfinTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(modifier = Modifier.size(32.dp))

        LazyColumn {
            val rows = credits.chunked(5)
            items(rows.size) { rowIndex ->
                val rowItems = rows[rowIndex]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                ) {
                    rowItems.forEachIndexed { index, item ->
                        val focusKey = "person_${person.id}_$rowIndex-${item.id}"
                        JellyseerrSearchCard(
                            item = item,
                            onClick = { onCreditClick(item) },
                            focusRequester = if (rowIndex == 0 && index == 0) {
                                firstCreditFocusRequester
                            } else {
                                focusRequesterForItem(focusKey)
                            },
                            onFocus = { onItemFocused(focusKey) },
                        )
                    }
                }
            }
        }
    }
}



@Composable
internal fun JellyseerrCastRow(
	cast: List<JellyseerrCast>,
	onCastClick: (JellyseerrCast) -> Unit,
	firstCastFocusRequester: FocusRequester? = null,
) {
	val displayCast = cast.take(10)

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 4.dp),
	) {
		itemsIndexed(displayCast) { index, person ->
			JellyseerrCastCard(
				person = person,
				onClick = { onCastClick(person) },
				focusRequester = if (index == 0) firstCastFocusRequester else null,
			)
		}
	}
}


@Composable

internal fun JellyseerrDetail(
	item: JellyseerrSearchItem,
	details: JellyseerrMovieDetails?,
	seasonEpisodes: Map<SeasonKey, List<JellyseerrEpisode>> = emptyMap(),
	ownRequests: List<JellyseerrRequest> = emptyList(),
	requestStatusMessage: String?,
	onRequestClick: (List<Int>?) -> Unit,
	onCastClick: (JellyseerrCast) -> Unit,
	onGenreClick: (JellyseerrGenre) -> Unit,
	onShowSeasonDialog: () -> Unit,
    onCollectionItemClick: (JellyseerrSearchItem) -> Unit,
    firstCastFocusRequester: FocusRequester = remember { FocusRequester() },
) {
	val requestButtonFocusRequester = remember { FocusRequester() }
	val trailerButtonFocusRequester = remember { FocusRequester() }
	val genreButtonFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val navigationRepository = koinInject<org.jellyfin.androidtv.ui.navigation.NavigationRepository>()
    val apiClient = koinInject<ApiClient>()
    val playbackLauncher = koinInject<PlaybackLauncher>()
    val repository = koinInject<JellyseerrRepository>()
    val context = LocalContext.current
    val availableTitle = (details?.title ?: details?.name ?: item.title).trim()
    val trailerButtonText = stringResource(R.string.jellyseerr_watch_trailer_button)
    val isTv = item.mediaType == "tv"
    val trailerButtonEnabled = availableTitle.isNotBlank()
    val genres = details?.genres.orEmpty()
    val certification = details?.certification?.takeIf { !it.isNullOrBlank() }
    val ageValue = certification?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
    val certificationDisplay = if (ageValue != null && Locale.getDefault().country.equals("DE", ignoreCase = true)) {
        "FSK - $ageValue"
    } else {
        ageValue ?: certification
    }
    val detailScrollState = rememberScrollState()
    val headerHeight = 200.dp
    var showCollectionDialog by remember { mutableStateOf(false) }
    var collectionItems by remember { mutableStateOf<List<JellyseerrSearchItem>>(emptyList()) }
    var collectionLoading by remember { mutableStateOf(false) }
    var collectionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        detailScrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(detailScrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Compute values before the layout
        val posterWidth = 160.dp
        val isRequested = item.isRequested
        val (isAvailable, isPartiallyAvailable) = if (isTv && seasonEpisodes.isNotEmpty()) {
            val allLoadedEpisodes = seasonEpisodes.values.flatten()
            val availableEpisodesCount = allLoadedEpisodes.count { it.isAvailable }
            val totalEpisodesCount = allLoadedEpisodes.size
            val allAvailable = availableEpisodesCount == totalEpisodesCount && totalEpisodesCount > 0
            val someAvailable = availableEpisodesCount > 0 && !allAvailable
            val finalAvailable = allAvailable || item.isAvailable
            val finalPartial = !finalAvailable && someAvailable && !isRequested
            Pair(finalAvailable, finalPartial)
        } else {
            val fallbackPartial = if (isTv) {
                false
            } else {
                item.isPartiallyAvailable && !item.isAvailable && !isRequested
            }
            Pair(item.isAvailable, fallbackPartial)
        }

        val requestButtonInteraction = remember { MutableInteractionSource() }
        val requestButtonFocused by requestButtonInteraction.collectIsFocusedAsState()

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
                focusedContainerColor = Color(0xFF00ACC1),
                focusedContentColor = Color.White,
            )
            isRequested -> ButtonDefaults.colors(
                containerColor = Color(0xFFDD8800),
                contentColor = Color.Black,
                focusedContainerColor = Color(0xFFFFBB00),
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
            isRequested -> stringResource(R.string.jellyseerr_requested_label)
            else -> stringResource(R.string.jellyseerr_request_button)
        }

        val isMovieRequestDisabled = !isTv && isRequested && !isAvailable
        val buttonEnabled = when {
            isTv -> true
            isAvailable -> true
            isMovieRequestDisabled -> false
            else -> true
        }

        LaunchedEffect(item.id, genres.size, buttonEnabled, trailerButtonEnabled) {
            kotlinx.coroutines.delay(200)
            when {
                genres.isNotEmpty() -> genreButtonFocusRequester.requestFocus()
                buttonEnabled -> requestButtonFocusRequester.requestFocus()
                trailerButtonEnabled -> trailerButtonFocusRequester.requestFocus()
                else -> firstCastFocusRequester.requestFocus()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
			// Left Column: Poster + Request + Trailer Buttons
			Column(
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				// Poster
				Box(
					modifier = Modifier
						.width(posterWidth)
						.aspectRatio(2f / 3f)
						.clip(RoundedCornerShape(16.dp))
						.border(2.dp, Color.White, RoundedCornerShape(16.dp)),
				) {
                    val posterUrl = details?.posterPath ?: item.posterPath
                    if (posterUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF333333)),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Image(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_clapperboard),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF888888)),
                            )
                        }
                    } else {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            url = posterUrl,
                            aspectRatio = 2f / 3f,
                            scaleType = ImageView.ScaleType.CENTER_CROP,
                        )
                    }
                }

                // Request Button
                Button(
                    onClick = {
                        if (isRequested && !isAvailable && !isTv) {
                            navigationRepository.navigate(
                                org.jellyfin.androidtv.ui.navigation.Destinations.search(item.title),
                            )
                            return@Button
                        }
                        when {
                            isAvailable && item.jellyfinId != null -> {
                                val uuid = item.jellyfinId.toUUIDOrNull()
                                if (uuid != null) {
                                    navigationRepository.navigate(
                                        org.jellyfin.androidtv.ui.navigation.Destinations.itemDetails(uuid),
                                    )
                                } else {
                                    navigationRepository.navigate(
                                        org.jellyfin.androidtv.ui.navigation.Destinations.search(item.title),
                                    )
                                }
                            }
                            isAvailable -> navigationRepository.navigate(
                                org.jellyfin.androidtv.ui.navigation.Destinations.search(item.title),
                            )
                            isTv -> onShowSeasonDialog()
                            else -> onRequestClick(null)
                        }
                    },
                    enabled = buttonEnabled,
                    colors = buttonColors,
                    interactionSource = requestButtonInteraction,
                    modifier = Modifier
                        .width(posterWidth)
                        .focusRequester(requestButtonFocusRequester)
                        .border(
                            width = if (requestButtonFocused) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape,
                        ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isAvailable) {
                            androidx.compose.foundation.Image(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_play),
                                contentDescription = buttonText,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Text(
                                text = buttonText,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                softWrap = false,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                ),
                            )
                        }
                    }
                }

                // Trailer Button
                val trailerButtonInteraction = remember { MutableInteractionSource() }
                val trailerButtonFocused by trailerButtonInteraction.collectIsFocusedAsState()
                val trailerButtonColors = ButtonDefaults.colors(
                    containerColor = Color(0xFF1565C0),
                    contentColor = Color.White,
                    focusedContainerColor = Color(0xFF1976D2),
                    focusedContentColor = Color.White,
                )

                Button(
                    onClick = {
                        coroutineScope.launch {
                            playJellyseerrTrailer(context, apiClient, playbackLauncher, item, availableTitle)
                        }
                    },
                    enabled = trailerButtonEnabled,
                    colors = trailerButtonColors,
                    interactionSource = trailerButtonInteraction,
                    modifier = Modifier
                        .width(posterWidth)
                        .focusRequester(trailerButtonFocusRequester)
                        .border(
                            width = if (trailerButtonFocused) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape,
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MaterialIcon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_trailer),
                            contentDescription = trailerButtonText,
                            modifier = Modifier.size(20.dp),
                            tint = JellyfinTheme.colorScheme.onButton,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = trailerButtonText,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            softWrap = false,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }

            // Right Column: Title/Metadata Box + Collection Box | Description Box
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Top Row: Title/Metadata Box and Collection Box
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                ) {
                    // Title/Metadata Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x40000000))
                            .padding(12.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val year = details?.releaseDate?.take(4)
                            val titleWithYear = buildString {
                                append(details?.title ?: item.title)
                                year?.let { append(" ($it)") }
                            }
                            Text(
                                text = titleWithYear,
                                color = JellyfinTheme.colorScheme.onBackground,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            val runtime = details?.runtime
                            val rating = details?.voteAverage
                            val seasonCount = if (isTv) details?.seasons?.filter { it.seasonNumber > 0 }?.size else null

                            val metaParts = buildList {
                                if (isTv) {
                                    seasonCount?.let { count ->
                                        val seasonText = if (count == 1) {
                                            "1 " + stringResource(R.string.lbl_seasons)
                                        } else {
                                            "$count " + stringResource(R.string.lbl_seasons)
                                        }
                                        add(seasonText)
                                    }
                                } else {
                                    runtime?.let { add("${it} min") }
                                }
                                rating?.let { add(String.format("%.1f/10", it)) }
                            }

                            if (metaParts.isNotEmpty() || certification != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (certification != null) {
                                        val badgeColor = when (ageValue?.toIntOrNull()) {
                                            null -> Color(0xFF888888)
                                            0 -> Color(0xFFE0E0E0)
                                            6 -> Color(0xFFFDD835)
                                            12 -> Color(0xFF66BB6A)
                                            16 -> Color(0xFF1565C0)
                                            18 -> Color(0xFFD32F2F)
                                            else -> Color(0xFF888888)
                                        }
                                        val textColor = when (ageValue?.toIntOrNull()) {
                                            0, 6 -> Color.Black
                                            else -> Color.White
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeColor)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = certificationDisplay ?: certification,
                                                color = textColor,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }

                                    if (metaParts.isNotEmpty()) {
                                        if (certification != null) {
                                            Text(
                                                text = "|",
                                                color = JellyfinTheme.colorScheme.onBackground,
                                                fontSize = 12.sp,
                                            )
                                        }
                                        Text(
                                            text = metaParts.joinToString(" | "),
                                            color = JellyfinTheme.colorScheme.onBackground,
                                        )
                                    }
                                }
                            }

                            if (genres.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    val genreShape = RoundedCornerShape(8.dp)
                                    genres.forEachIndexed { index, genre ->
                                        val genreInteraction = remember { MutableInteractionSource() }
                                        val genreFocused by genreInteraction.collectIsFocusedAsState()
                                        Button(
                                            onClick = { onGenreClick(genre) },
                                            colors = ButtonDefaults.colors(
                                                containerColor = Color(0xFF424242),
                                                contentColor = Color.White,
                                                focusedContainerColor = Color(0xFF616161),
                                                focusedContentColor = Color.White,
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = genreShape,
                                            interactionSource = genreInteraction,
                                            modifier = (if (index == 0) Modifier.focusRequester(genreButtonFocusRequester) else Modifier)
                                                .border(
                                                    width = if (genreFocused) 3.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = genreShape,
                                                ),
                                        ) {
                                            Text(
                                                text = genre.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Collection Box (if exists)
                    details?.collection?.let { collection ->
                        val collectionImage = collection.backdropPath
                            ?: collection.posterPath
                            ?: details?.backdropPath
                            ?: item.backdropPath
                            ?: details?.posterPath
                            ?: item.posterPath
                        val collectionInteraction = remember { MutableInteractionSource() }
                        val collectionFocused by collectionInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .width(260.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .border(if (collectionFocused) 3.dp else 0.dp, Color.White, RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = collectionInteraction,
                                    indication = null,
								) {
									collectionLoading = true
									collectionError = null
									showCollectionDialog = true
									coroutineScope.launch {
										repository.getCollectionDetails(collection.id)
											.onSuccess { discovery ->
												collectionItems = markItemsWithRequests(discovery.results, ownRequests)
												collectionLoading = false
											}
											.onFailure { error ->
												collectionError = error.message
												collectionLoading = false
                                            }
                                    }
                                }
                                .focusable(true, collectionInteraction)
                                .background(Color(0xFF0F172A)),
                        ) {
                            if (!collectionImage.isNullOrBlank()) {
                                AsyncImage(
                                    modifier = Modifier.fillMaxSize(),
                                    url = collectionImage,
                                    aspectRatio = 16f / 9f,
                                    scaleType = ImageView.ScaleType.CENTER_CROP,
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0xCC0F172A),
                                                    Color(0xE60F172A),
                                                ),
                                            ),
                                        ),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.jellyseerr_collection_label, collection.name),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 14.sp,
                                        )
                                        Text(
                                            text = collection.name,
                                            color = JellyfinTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x33FFFFFF))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.jellyseerr_collection_show),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Description Box
                val overviewText = when {
                    !details?.overview.isNullOrBlank() -> details.overview!!
                    !item.overview.isNullOrBlank() -> item.overview!!
                    else -> stringResource(R.string.jellyseerr_no_description)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x66000000))
                        .padding(12.dp),
                ) {
                    Text(
                        text = overviewText,
                        color = JellyfinTheme.colorScheme.onBackground,
                        fontStyle = if (overviewText == stringResource(R.string.jellyseerr_no_description)) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    )
                }
            }
        }

        val cast = details?.credits?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(id = R.string.jellyseerr_cast_title),
                color = JellyfinTheme.colorScheme.onBackground,
                fontSize = 18.sp,
            )
            JellyseerrCastRow(
                cast = cast,
                onCastClick = onCastClick,
                firstCastFocusRequester = firstCastFocusRequester,
            )
        }
    }

    if (showCollectionDialog) {
        Dialog(
            onDismissRequest = { showCollectionDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
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
                    Text(
                        text = details?.collection?.name ?: stringResource(R.string.jellyseerr_collection_label, ""),
                        color = JellyfinTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )

                    when {
                        collectionLoading -> Text(text = stringResource(R.string.loading), color = JellyfinTheme.colorScheme.onBackground)
                        collectionError != null -> Text(text = collectionError ?: "", color = Color.Red)
                        collectionItems.isEmpty() -> Text(text = stringResource(R.string.lbl_no_items), color = JellyfinTheme.colorScheme.onBackground)
                        else -> {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(collectionItems) { collectionItem ->
									val badgeData = when {
										collectionItem.isAvailable -> Triple(stringResource(R.string.lbl_play), Color(0xFF2E7D32), Icons.Filled.Check)
										collectionItem.isPartiallyAvailable -> Triple(stringResource(R.string.jellyseerr_partially_available_label), Color(0xFF0097A7), Icons.Filled.Remove)
										collectionItem.isRequested -> Triple(stringResource(R.string.jellyseerr_requested_label), Color(0xFFAA5CC3), Icons.Filled.Schedule)
										else -> null
									}
                                    val itemInteraction = remember { MutableInteractionSource() }
                                    val itemFocused by itemInteraction.collectIsFocusedAsState()
                                    val scale by animateFloatAsState(
                                        targetValue = if (itemFocused) 1.08f else 1.0f,
                                        animationSpec = tween(durationMillis = 150),
                                        label = "collectionCardScale"
                                    )

                                    Column(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .clickable(
                                                interactionSource = itemInteraction,
                                                indication = null,
                                            ) {
                                                showCollectionDialog = false
                                                onCollectionItemClick(collectionItem)
                                            }
                                            .focusable(true, itemInteraction),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(200.dp)
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF1F2937))
                                                .border(
                                                    width = if (itemFocused) 3.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(12.dp),
                                                ),
                                        ) {
                                            if (!collectionItem.posterPath.isNullOrBlank()) {
                                                AsyncImage(
                                                    modifier = Modifier.fillMaxSize(),
                                                    url = collectionItem.posterPath!!,
                                                    aspectRatio = 2f / 3f,
                                                    scaleType = ImageView.ScaleType.CENTER_CROP,
                                                )
                                            }
                                            badgeData?.let { (badgeText, badgeColor, badgeIcon) ->
                                                Box(
                                                    modifier = Modifier
                                                        .padding(6.dp)
                                                        .align(Alignment.TopEnd)
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(badgeColor.copy(alpha = 0.95f)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    MaterialIcon(
                                                        imageVector = badgeIcon,
                                                        contentDescription = badgeText,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = collectionItem.title,
                                            color = JellyfinTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
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

    // initial focus handled above in LaunchedEffect keyed by item.id
}

