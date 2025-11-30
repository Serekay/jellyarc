package org.jellyfin.androidtv.ui.jellyseerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.JellyseerrSearchItem
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text

/**
 * Reusable content section component that displays a title, a horizontal row of items,
 * and a "View All" card at the end.
 *
 * This component eliminates code duplication across different content sections
 * (Popular Movies, Popular TV, Upcoming, etc.)
 */
@Composable
internal fun JellyseerrContentSection(
	title: String,
	items: List<JellyseerrSearchItem>,
	scrollKey: String,
	scrollPosition: ScrollPosition?,
	onScrollPositionChange: (String, Int, Int) -> Unit,
	onItemClick: (JellyseerrSearchItem) -> Unit,
	onViewAllClick: () -> Unit,
	viewAllKey: String,
	itemFocusKey: (Int) -> String,
	focusRequesterForItem: (String) -> FocusRequester,
	focusRequesterForViewAll: (String) -> FocusRequester,
	onItemFocused: (String) -> Unit,
	onViewAllFocused: (String) -> Unit,
	modifier: Modifier = Modifier,
	sectionSpacing: androidx.compose.ui.unit.Dp = 5.dp,
	sectionInnerSpacing: androidx.compose.ui.unit.Dp = 6.dp,
	sectionTitleFontSize: androidx.compose.ui.unit.TextUnit = 26.sp,
	showViewAll: Boolean = true,
	contentHash: Int? = null,
) {
	// Use contentHash as a key to force recomposition when content changes
	val effectiveItems = remember(items, contentHash) { items }
	if (effectiveItems.isEmpty()) {
		Spacer(modifier = Modifier.size(sectionSpacing))
		Text(
			text = title,
			color = JellyfinTheme.colorScheme.onBackground,
			fontSize = sectionTitleFontSize,
		)
		Spacer(modifier = Modifier.size(sectionInnerSpacing))
		Text(
			text = stringResource(R.string.jellyseerr_no_results),
			modifier = Modifier.padding(horizontal = 24.dp),
			color = JellyfinTheme.colorScheme.onBackground,
		)
		return
	}

	Spacer(modifier = Modifier.size(sectionSpacing))

	Text(
		text = title,
		color = JellyfinTheme.colorScheme.onBackground,
		fontSize = sectionTitleFontSize,
	)

	Spacer(modifier = Modifier.size(sectionInnerSpacing))

	val listState = rememberLazyListState(
		initialFirstVisibleItemIndex = scrollPosition?.index ?: 0,
		initialFirstVisibleItemScrollOffset = scrollPosition?.offset ?: 0,
	)

	LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
		if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
			onScrollPositionChange(
				scrollKey,
				listState.firstVisibleItemIndex,
				listState.firstVisibleItemScrollOffset
			)
		}
	}

	LazyRow(
		state = listState,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		contentPadding = PaddingValues(horizontal = 24.dp),
		modifier = modifier
			.fillMaxWidth()
			.height(250.dp),
	) {
		val maxIndex = effectiveItems.lastIndex
		val extraItems = if (showViewAll) 1 else 0

		items(
			count = maxIndex + 1 + extraItems,
			key = { index ->
				if (index <= maxIndex) "${scrollKey}_${effectiveItems[index].id}"
				else "${scrollKey}_view_all"
			}
		) { index ->
			when {
				index in 0..maxIndex -> {
					val item = effectiveItems[index]
					val focusKey = itemFocusKey(item.id)

					JellyseerrSearchCard(
						item = item,
						onClick = { onItemClick(item) },
						focusRequester = focusRequesterForItem(focusKey),
						onFocus = { onItemFocused(focusKey) },
					)
				}

				showViewAll && index == maxIndex + 1 -> {
					val posterUrls = remember(effectiveItems) {
						effectiveItems.shuffled().take(4).mapNotNull { it.posterPath }
					}
					JellyseerrViewAllCard(
						onClick = onViewAllClick,
						posterUrls = posterUrls,
						focusRequester = focusRequesterForViewAll(viewAllKey),
						onFocus = { onViewAllFocused(viewAllKey) },
					)
				}
			}
		}
	}
}

/**
 * Specialized content section for genre cards.
 */
@Composable
internal fun JellyseerrGenreSection(
	title: String,
	genres: List<org.jellyfin.androidtv.data.repository.JellyseerrGenreSlider>,
	onGenreClick: (org.jellyfin.androidtv.data.repository.JellyseerrGenreSlider) -> Unit,
	genreFocusKey: (Int) -> String,
	focusRequesterForGenre: (String) -> FocusRequester,
	onGenreFocused: (String) -> Unit,
	modifier: Modifier = Modifier,
	sectionSpacing: androidx.compose.ui.unit.Dp = 5.dp,
	sectionInnerSpacing: androidx.compose.ui.unit.Dp = 6.dp,
	sectionTitleFontSize: androidx.compose.ui.unit.TextUnit = 26.sp,
) {
	if (genres.isEmpty()) return

	Spacer(modifier = Modifier.size(sectionSpacing))

	Text(
		text = title,
		color = JellyfinTheme.colorScheme.onBackground,
		fontSize = sectionTitleFontSize,
	)

	Spacer(modifier = Modifier.size(sectionInnerSpacing))

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		contentPadding = PaddingValues(horizontal = 24.dp),
		modifier = modifier
			.fillMaxWidth()
			.height(110.dp),
	) {
		items(
			count = genres.size,
			key = { index -> genres[index].id }
		) { index ->
			val genre = genres[index]
			val genreKey = genreFocusKey(genre.id)
			JellyseerrGenreCard(
				genre = genre,
				onClick = {
					onGenreFocused(genreKey)
					onGenreClick(genre)
				},
				focusRequester = focusRequesterForGenre(genreKey),
				onFocus = { onGenreFocused(genreKey) },
			)
		}
	}
}

/**
 * Specialized content section for company cards (studios/networks).
 */
@Composable
internal fun JellyseerrCompanySection(
	title: String,
	companies: List<org.jellyfin.androidtv.data.repository.JellyseerrCompany>,
	onCompanyClick: (org.jellyfin.androidtv.data.repository.JellyseerrCompany) -> Unit,
	companyFocusKey: (Int) -> String,
	focusRequesterForCompany: (String) -> FocusRequester,
	onCompanyFocused: (String) -> Unit,
	modifier: Modifier = Modifier,
	sectionSpacing: androidx.compose.ui.unit.Dp = 5.dp,
	sectionInnerSpacing: androidx.compose.ui.unit.Dp = 6.dp,
	sectionTitleFontSize: androidx.compose.ui.unit.TextUnit = 26.sp,
) {
	if (companies.isEmpty()) return

	Spacer(modifier = Modifier.size(sectionSpacing))

	Text(
		text = title,
		color = JellyfinTheme.colorScheme.onBackground,
		fontSize = sectionTitleFontSize,
	)

	Spacer(modifier = Modifier.size(sectionInnerSpacing))

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		contentPadding = PaddingValues(horizontal = 24.dp),
		modifier = modifier
			.fillMaxWidth()
			.height(110.dp),
	) {
		items(
			count = companies.size,
			key = { index -> companies[index].id }
		) { index ->
			val company = companies[index]
			val companyKey = companyFocusKey(company.id)
			JellyseerrCompanyCard(
				name = company.name,
				logoUrl = company.logoUrl,
				onClick = {
					onCompanyFocused(companyKey)
					onCompanyClick(company)
				},
				focusRequester = focusRequesterForCompany(companyKey),
				onFocus = { onCompanyFocused(companyKey) },
			)
		}
	}
}

/**
 * Specialized content section for recent requests.
 */
@Composable
internal fun JellyseerrRecentRequestsSection(
	title: String,
	requests: List<JellyseerrSearchItem>,
	onRequestClick: (JellyseerrSearchItem) -> Unit,
	requestFocusKey: (Int) -> String,
	focusRequesterForRequest: (String) -> FocusRequester,
	onRequestFocused: (String) -> Unit,
	modifier: Modifier = Modifier,
	sectionSpacing: androidx.compose.ui.unit.Dp = 5.dp,
	sectionInnerSpacing: androidx.compose.ui.unit.Dp = 6.dp,
	sectionTitleFontSize: androidx.compose.ui.unit.TextUnit = 26.sp,
	contentHash: Int? = null,
) {
	// Use contentHash as a key to force recomposition when content changes
	val effectiveRequests = remember(requests, contentHash) { requests }

	Spacer(modifier = Modifier.size(sectionSpacing))

	Text(
		text = title,
		color = JellyfinTheme.colorScheme.onBackground,
		fontSize = sectionTitleFontSize,
	)

	if (effectiveRequests.isEmpty()) {
		Spacer(modifier = Modifier.size(sectionInnerSpacing))
		Text(
			text = stringResource(R.string.jellyseerr_no_results),
			modifier = Modifier.padding(horizontal = 24.dp),
			color = JellyfinTheme.colorScheme.onBackground,
		)
		return
	}

	Spacer(modifier = Modifier.size(sectionInnerSpacing))

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		contentPadding = PaddingValues(horizontal = 24.dp),
		modifier = modifier
			.fillMaxWidth()
			.height(120.dp),
	) {
		items(
			count = effectiveRequests.size,
			key = { index -> "recent_request_${effectiveRequests[index].id}" }
		) { index ->
			val item = effectiveRequests[index]
			val requestKey = requestFocusKey(index)
			JellyseerrRecentRequestCard(
				item = item,
				onClick = {
					onRequestFocused(requestKey)
					onRequestClick(item)
				},
				focusRequester = focusRequesterForRequest(requestKey),
				onFocus = { onRequestFocused(requestKey) },
			)
		}
	}
}
