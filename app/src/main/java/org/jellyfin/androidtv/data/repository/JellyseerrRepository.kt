package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.json.JSONObject
import timber.log.Timber

private const val TMDB_POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"
private const val TMDB_BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w780"
private const val TMDB_PROFILE_BASE_URL = "https://image.tmdb.org/t/p/w300"
private const val TMDB_GENRE_BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_COMPANY_LOGO_BASE_URL = "https://image.tmdb.org/t/p/w780"
private const val TMDB_COMPANY_LOGO_FILTER = "_filter(duotone,ffffff,bababa)"
private const val CACHE_TTL_SHORT = 60_000L
private const val CACHE_TTL_MEDIUM = 2 * 60_000L
private const val CACHE_TTL_LONG = 5 * 60_000L

private fun posterImageUrl(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "$TMDB_POSTER_BASE_URL$it" }
private fun backdropImageUrl(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "$TMDB_BACKDROP_BASE_URL$it" }
private fun profileImageUrl(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "$TMDB_PROFILE_BASE_URL$it" }
private fun genreBackdropImageUrl(path: String?, genreId: Int): String? {
	if (path.isNullOrBlank()) return null
	val colors = genreColorMap[genreId] ?: genreColorMap[0]!!
	return "${TMDB_GENRE_BACKDROP_BASE_URL}_filter(duotone,${colors.first},${colors.second})$path"
}

private fun companyLogoImageUrl(path: String?): String? =
	path?.takeIf { it.isNotBlank() }?.let { "$TMDB_COMPANY_LOGO_BASE_URL$TMDB_COMPANY_LOGO_FILTER$it" }

// Genre color mapping (dark, light) - matches Jellyseerr
private val genreColorMap: Map<Int, Pair<String, String>> = mapOf(
	0 to Pair("030712", "6B7280"),
	28 to Pair("991B1B", "FCA5A5"),
	12 to Pair("581C87", "D8B4FE"),
	16 to Pair("1E3A8A", "93C5FD"),
	35 to Pair("9A3412", "FDBA74"),
	80 to Pair("1E3A8A", "93C5FD"),
	99 to Pair("166534", "86EFAC"),
	18 to Pair("831843", "FBCFE8"),
	10751 to Pair("854D0E", "FDE047"),
	14 to Pair("0E7490", "67E8F9"),
	36 to Pair("9A3412", "FDBA74"),
	27 to Pair("030712", "6B7280"),
	10402 to Pair("1E3A8A", "93C5FD"),
	9648 to Pair("6B21A8", "C4B5FD"),
	10749 to Pair("831843", "FBCFE8"),
	878 to Pair("0E7490", "67E8F9"),
	10770 to Pair("991B1B", "FCA5A5"),
	53 to Pair("030712", "6B7280"),
	10752 to Pair("7F1D1D", "FCA5A5"),
	37 to Pair("9A3412", "FDBA74"),
	10759 to Pair("581C87", "D8B4FE"),
	10762 to Pair("1E3A8A", "93C5FD"),
	10763 to Pair("030712", "6B7280"),
	10764 to Pair("7C2D12", "FED7AA"),
	10765 to Pair("0E7490", "67E8F9"),
	10766 to Pair("831843", "FBCFE8"),
	10767 to Pair("166534", "86EFAC"),
	10768 to Pair("7F1D1D", "FCA5A5"),
)

@Serializable
private data class JellyseerrSearchResponse(
	val page: Int,
	val totalPages: Int,
	val totalResults: Int,
	val results: List<JellyseerrSearchItemDto>,
)

@Serializable
private data class JellyseerrGenreDiscoveryDto(
	val page: Int,
	val totalPages: Int,
	val totalResults: Int,
	val genre: JellyseerrGenre,
	val results: List<JellyseerrSearchItemDto>,
)

@Serializable
private data class JellyseerrCompanyDto(
	val id: Int,
	val name: String,
	val logoPath: String? = null,
	val homepage: String? = null,
	val description: String? = null,
)

@Serializable
private data class JellyseerrStudioDiscoveryDto(
	val page: Int,
	val totalPages: Int,
	val totalResults: Int,
	val studio: JellyseerrCompanyDto,
	val results: List<JellyseerrSearchItemDto>,
)

@Serializable
private data class JellyseerrNetworkDiscoveryDto(
	val page: Int,
	val totalPages: Int,
	val totalResults: Int,
	val network: JellyseerrCompanyDto,
	val results: List<JellyseerrSearchItemDto>,
)

@Serializable
private data class JellyseerrSearchItemDto(
	val id: Int,
	val mediaType: String,
	val title: String? = null,
	val name: String? = null,
	val overview: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val profilePath: String? = null,
	val releaseDate: String? = null,
	val firstAirDate: String? = null,
	val mediaInfo: JellyseerrMediaInfoDto? = null,
)

@Serializable
private data class JellyseerrUserResults(
	val results: List<JellyseerrUser>,
)

@Serializable
private data class JellyseerrUser(
	val id: Int,
	val username: String? = null,
	val jellyfinUsername: String? = null,
	val jellyfinUserId: String? = null,
)

	class JellyseerrRepositoryImpl(
		private val userPreferences: UserPreferences,
		private val sessionRepository: SessionRepository,
		private val userRepository: UserRepository,
		private val serverRepository: ServerRepository,
		private val apiClient: ApiClient,
	) : JellyseerrRepository {

		private data class CacheEntry(val value: Any, val timestamp: Long)

		private val cache = mutableMapOf<String, CacheEntry>()

		private fun now() = System.currentTimeMillis()

		@Suppress("UNCHECKED_CAST")
		private fun <T> readCache(key: String, ttlMs: Long): T? {
			val entry = cache[key] ?: return null
			if (now() - entry.timestamp > ttlMs) {
				cache.remove(key)
				return null
			}
			return entry.value as? T
		}

		private fun writeCache(key: String, value: Any) {
			cache[key] = CacheEntry(value, now())
		}

		private fun invalidateCache(vararg prefixes: String) {
			if (prefixes.isEmpty()) return
			val keys = cache.keys.toList()
			keys.filter { key -> prefixes.any { prefix -> key.startsWith(prefix) } }
				.forEach { cache.remove(it) }
		}
	private data class TvAvailability(
		val isFullyAvailable: Boolean,
		val isPartiallyAvailable: Boolean,
	)

	private val tvAvailabilityCache = mutableMapOf<Int, TvAvailability>()

	private fun clearTvAvailabilityCache() {
		tvAvailabilityCache.clear()
	}

	private suspend fun lookupTvAvailability(tmdbId: Int): TvAvailability? {
		tvAvailabilityCache[tmdbId]?.let { return it }
		val result = getTvDetails(tmdbId)
		if (result.isFailure) return null

		val details = result.getOrNull() ?: return null
		val seasons = details.seasons.filter { it.seasonNumber > 0 }
		if (seasons.isEmpty()) return null

		val totalSeasons = seasons.size
		val availableSeasons = seasons.count { it.status == 5 }
		if (availableSeasons == 0) return null

		val availability = TvAvailability(
			isFullyAvailable = availableSeasons == totalSeasons,
			isPartiallyAvailable = availableSeasons < totalSeasons,
		)
		tvAvailabilityCache[tmdbId] = availability
		return availability
	}

	private data class Config(val baseUrl: String, val apiKey: String)

	private val client = OkHttpClient()
	private val json = Json {
		ignoreUnknownKeys = true
	}
	@Volatile
	private var cachedConfig: Config? = null

	private fun isPrivateHost(host: String): Boolean {
		val lower = host.lowercase()
		if (lower == "localhost") return true

		val parts = host.split(".").mapNotNull { it.toIntOrNull() }
		if (parts.size == 4) {
			val (a, b, _, _) = parts
			return when (a) {
				10 -> true
				172 -> b in 16..31
				192 -> b == 168
				169 -> b == 254
				127 -> true
				else -> false
			}
		}
		return false
	}

	private fun looksLikeTailscaleHost(host: String): Boolean {
		val lower = host.lowercase()
		return lower.startsWith("100.") ||
			lower.startsWith("fd7a:") ||
			lower.endsWith(".ts.net") ||
			lower.endsWith(".beta.tailscale.net")
	}

	private fun normalizeBaseUrl(baseUrl: String): String {
		val trimmed = baseUrl.trim().trimEnd('/')
		val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
		val host = parsed.host

		// Tailscale-spezifische Hosts niemals überschreiben
		if (looksLikeTailscaleHost(host)) return trimmed

		// Only rewrite obvious private/loopback hosts when we know we talk to Jellyfin over Tailscale
		if (!isPrivateHost(host)) return trimmed

		val jellyfinBase = apiClient.baseUrl?.toHttpUrlOrNull() ?: return trimmed
		val jellyfinHost = jellyfinBase.host
		if (!looksLikeTailscaleHost(jellyfinHost)) return trimmed

		val rewritten = parsed.newBuilder()
			.host(jellyfinHost)
			.build()
			.toString()
			.trimEnd('/')

		if (rewritten != trimmed) {
			Timber.i("Rewriting Jellyseerr base URL for Tailscale: $trimmed -> $rewritten")
		}

		return rewritten
	}

	private suspend fun chooseBestBase(defaultBase: String, tailscaleBase: String?): String {
		val tsBase = tailscaleBase?.takeIf { it.isNotBlank() } ?: return defaultBase

		// Use Tailscale base when the server is flagged for Tailscale
		// We trust the tailscaleEnabled flag - if VPN is active, use the Tailscale endpoint
		val preferTailscale = tailscaleEnabledForCurrentServer()
		return if (preferTailscale) {
			Timber.d("Using Tailscale Jellyseerr endpoint: $tsBase")
			tsBase
		} else {
			Timber.d("Using local Jellyseerr endpoint: $defaultBase")
			defaultBase
		}
	}

	private suspend fun tailscaleEnabledForCurrentServer(): Boolean {
		val serverId = sessionRepository.currentSession.value?.serverId ?: return false
		val server = serverRepository.getServer(serverId, eagerUpdate = false) ?: return false
		return server.tailscaleEnabled
	}

private fun mapSearchItemDtoToModel(dto: JellyseerrSearchItemDto): JellyseerrSearchItem {
	val posterUrl = posterImageUrl(dto.posterPath) ?: profileImageUrl(dto.profilePath)
	val backdropUrl = backdropImageUrl(dto.backdropPath)
	val profileUrl = profileImageUrl(dto.profilePath)
	val releaseDate = dto.releaseDate ?: dto.firstAirDate

		// Status aus mediaInfo extrahieren (von Jellyseerr)
		// Status 5 = Available, Status 4 = Partially Available
		val status = dto.mediaInfo?.status
		val isAvailable = status == 5
		val isPartiallyAvailable = status == 4
		val jellyfinId = dto.mediaInfo?.jellyfinMediaId ?: dto.mediaInfo?.jellyfinMediaId4k

		return JellyseerrSearchItem(
			id = dto.id,
			mediaType = dto.mediaType,
			title = (dto.title ?: dto.name).orEmpty(),
			overview = dto.overview,
			posterPath = posterUrl,
			backdropPath = backdropUrl,
			profilePath = profileUrl,
			releaseDate = releaseDate,
			isAvailable = isAvailable,
			isPartiallyAvailable = isPartiallyAvailable,
			jellyfinId = jellyfinId,
	)
}

private fun mapCompanyDtoToModel(dto: JellyseerrCompanyDto): JellyseerrCompany =
	JellyseerrCompany(
		id = dto.id,
		name = dto.name,
		logoUrl = companyLogoImageUrl(dto.logoPath),
		homepage = dto.homepage,
		description = dto.description,
	)

	// Cached mapping between current Jellyfin user and Jellyseerr user id
	@Volatile
	private var cachedUserId: Int? = null

	private fun getConfig(): Config? {
		cachedConfig?.let { current ->
			// Always refresh config from bridge plugin to ensure we use the correct endpoint
			// based on current Tailscale status. The plugin provides both endpoints,
			// and chooseBestBase() will select the right one.
			val refreshed = runBlocking { fetchBridgeConfig().getOrNull() }
			if (refreshed != null) {
				cachedConfig = refreshed
				Timber.d("Refreshed Jellyseerr config from bridge: ${refreshed.baseUrl}")
				return refreshed
			}

			// Fallback: use cached config if bridge refresh fails
			val normalized = normalizeBaseUrl(current.baseUrl).trimEnd('/')
			if (normalized != current.baseUrl) {
				Config(normalized, current.apiKey).also { cachedConfig = it }
				return cachedConfig
			}
			return current
		}

		val url = userPreferences[UserPreferences.jellyseerrUrl].trim()
		val apiKey = userPreferences[UserPreferences.jellyseerrApiKey].trim()

		if (url.isNotBlank() && apiKey.isNotBlank()) {
			val normalizedUrl = normalizeBaseUrl(url).trimEnd('/')
			return Config(normalizedUrl, apiKey).also { cachedConfig = it }
		}

		// Fallback: try to fetch config from Jellyfin via the Requests Bridge plugin
		val fetched = runBlocking { fetchBridgeConfig().getOrNull() }
		if (fetched != null) {
			val normalized = fetched.copy(baseUrl = normalizeBaseUrl(fetched.baseUrl).trimEnd('/'))
			userPreferences[UserPreferences.jellyseerrUrl] = normalized.baseUrl
			userPreferences[UserPreferences.jellyseerrApiKey] = normalized.apiKey
			cachedConfig = normalized
		}

		return cachedConfig
	}

	override suspend fun ensureConfig(): Result<Unit> = withContext(Dispatchers.IO) {
		if (getConfig() != null) return@withContext Result.success(Unit)
		fetchBridgeConfig().map { Unit }
	}

	private suspend fun fetchBridgeConfig(): Result<Config> = withContext(Dispatchers.IO) {
		val jellyfinBase = apiClient.baseUrl?.trimEnd('/')
			?: return@withContext Result.failure(IllegalStateException("No Jellyfin server configured"))
		val token = apiClient.accessToken?.takeIf { it.isNotBlank() }
			?: return@withContext Result.failure(IllegalStateException("No Jellyfin access token"))

		val authorization = AuthorizationHeaderBuilder.buildHeader(
			apiClient.clientInfo.name,
			apiClient.clientInfo.version,
			apiClient.deviceInfo.id,
			apiClient.deviceInfo.name,
			token,
		)

		fun Request.Builder.addAuth(): Request.Builder = header("Authorization", authorization)

		fun fetchBase(path: String): Result<String> = runCatching {
			val request = Request.Builder()
				.url("$jellyfinBase/plugins/requests/$path")
				.addAuth()
				.build()

			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val normalized = body.trimStart().removePrefix("\uFEFF")
				val jsonObj = JSONObject(normalized)
				val success = jsonObj.optBoolean("success", false)
				val apiBase = jsonObj.optString("apiBase").trim()
				if (!success || apiBase.isBlank()) {
					throw IllegalStateException("Jellyseerr base URL not configured in Requests Bridge ($path)")
				}
				apiBase.trimEnd('/')
			}
		}

		val baseResult = fetchBase("apibase")
		if (baseResult.isFailure) return@withContext Result.failure(baseResult.exceptionOrNull()!!)

		// Optional: Tailscale URL supplied by the plugin for VPN-only setups
		val tailscaleBase = fetchBase("tailscale/apibase").getOrNull()
		val apiBase = chooseBestBase(baseResult.getOrThrow(), tailscaleBase)

		val keyResult = runCatching {
			val request = Request.Builder()
				.url("$jellyfinBase/plugins/requests/apikey")
				.addAuth()
				.build()

			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val normalized = body.trimStart().removePrefix("\uFEFF")
				val jsonObj = JSONObject(normalized)
				val success = jsonObj.optBoolean("success", false)
				val apiKey = jsonObj.optString("apiKey").trim()
				if (!success || apiKey.isBlank()) {
					throw IllegalStateException("Jellyseerr API key not configured in Requests Bridge")
				}
				apiKey
			}
		}

		if (keyResult.isFailure) return@withContext Result.failure(keyResult.exceptionOrNull()!!)

		val normalizedBase = normalizeBaseUrl(apiBase).trimEnd('/')
		Result.success(Config(normalizedBase, keyResult.getOrThrow()))
	}

	private suspend fun resolveCurrentUserId(config: Config): Result<Int> = withContext(Dispatchers.IO) {
		cachedUserId?.let { return@withContext Result.success(it) }

		val session = sessionRepository.currentSession.value
			?: return@withContext Result.failure(IllegalStateException("No active Jellyfin session"))
		val jellyfinUserId = session.userId.toString()
		val jellyfinUsername = userRepository.currentUser.value?.name.orEmpty()

		// Jellyseerr supports a search query (q) over username/email/plex/jellyfin usernames.
		val encodedName = if (jellyfinUsername.isNotBlank()) {
			java.net.URLEncoder.encode(jellyfinUsername, Charsets.UTF_8.name())
		} else null

		val url = buildString {
			append(config.baseUrl)
			append("/api/v1/user?take=50")
			if (!encodedName.isNullOrBlank()) {
				append("&q=")
				append(encodedName)
			}
		}
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.build()

		runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrUserResults.serializer(), body)

				// Prefer exact match on Jellyfin user id
				val byId = result.results.firstOrNull {
					!it.jellyfinUserId.isNullOrBlank() &&
						it.jellyfinUserId.equals(jellyfinUserId, ignoreCase = true)
				}

				// Fallback: match on Jellyfin username / username
				val byName = if (!jellyfinUsername.isBlank()) {
					result.results.firstOrNull {
						it.jellyfinUsername?.equals(jellyfinUsername, ignoreCase = true) == true ||
							it.username?.equals(jellyfinUsername, ignoreCase = true) == true
					}
				} else null

				val user = byId ?: byName
					?: throw IllegalStateException("No Jellyseerr user linked to current Jellyfin user")

				cachedUserId = user.id
				user.id
			}
		}
	}

	override suspend fun markAvailableInJellyfin(items: List<JellyseerrSearchItem>): Result<List<JellyseerrSearchItem>> =
		withContext(Dispatchers.IO) {
			if (items.isEmpty()) return@withContext Result.success(emptyList())

			runCatching {
				items.map { item ->
					// Wenn bereits eine JellyfinId von Jellyseerr vorhanden ist, behalte alle Daten
					if (item.jellyfinId != null) return@map item

					val title = item.title.trim()
					if (title.isEmpty()) return@map item

					// Nur für verfügbare Items suchen wir die Jellyfin-ID für Navigation
					if (!item.isAvailable && !item.isPartiallyAvailable) return@map item

					// Bestimme den Item-Typ basierend auf mediaType
					val itemKind = when (item.mediaType) {
						"movie" -> BaseItemKind.MOVIE
						"tv" -> BaseItemKind.SERIES
						else -> return@map item
					}

					val request = GetItemsRequest(
						searchTerm = title,
						limit = 5,
						includeItemTypes = setOf(itemKind),
						recursive = true,
						enableTotalRecordCount = false,
					)

					val resultItems = try {
						apiClient.itemsApi.getItems(request).content.items
					} catch (error: ApiClientException) {
						Timber.w(error, "Failed to query Jellyfin for Jellyfin ID of \"%s\"", title)
						return@map item
					}

					// Finde exakten Match
					val matchedItem = resultItems?.firstOrNull { baseItem ->
						val name = baseItem.name?.trim().orEmpty()
						val original = baseItem.originalTitle?.trim().orEmpty()
						name.equals(title, ignoreCase = true) ||
							original.equals(title, ignoreCase = true)
					}

					if (matchedItem != null) {
						// Nur die Jellyfin-ID für Navigation ergänzen
						// Verfügbarkeitsinformationen bleiben aus Jellyseerr
						item.copy(
							jellyfinId = matchedItem.id.toString(),
						)
					} else {
						item
					}
				}
			}.onFailure {
				Timber.e(it, "Failed to lookup Jellyfin IDs for Jellyseerr items")
			}
		}

override suspend fun search(query: String, page: Int): Result<JellyseerrSearchResult> = withContext(Dispatchers.IO) {
	val config = getConfig()
		?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

	val cacheKey = "search:$query:$page"
	readCache<JellyseerrSearchResult>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }

	if (query.isBlank()) return@withContext Result.success(JellyseerrSearchResult(emptyList(), 1, 1, 0))

	// Encode spaces explicitly as %20 to match Jellyseerr expectations (avoid server 400 on '+')
	val encodedQuery = java.net.URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
	val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
	val url = "${config.baseUrl}/api/v1/search?query=$encodedQuery&page=$page"

		val request = Request.Builder()
			.url(url)
		.header("X-API-Key", config.apiKey)
		.header("X-API-User", userId.toString())
		.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrSearchResponse.serializer(), body)

				val supportedTypes = setOf("movie", "tv", "person", "collection")
				val mappedResults = result.results
					.filter { it.mediaType in supportedTypes }
					.map { mapSearchItemDtoToModel(it) }
				JellyseerrSearchResult(
					results = mappedResults,
					page = result.page,
					totalPages = result.totalPages,
					totalResults = result.totalResults,
				)
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to search Jellyseerr") }

		result
	}

	override suspend fun getOwnRequests(): Result<List<JellyseerrRequest>> = withContext(Dispatchers.IO) {
	val config = getConfig()
		?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

	val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
	val cacheKey = "requests:own:$userId"
	readCache<List<JellyseerrRequest>>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }
	val url = "${config.baseUrl}/api/v1/request?take=50&requestedBy=$userId&sort=modified&sortDirection=desc"
	val request = Request.Builder()
		.url(url)
		.header("X-API-Key", config.apiKey)
		.header("X-API-User", userId.toString())
		.build()

	val result = runCatching {
		client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

			val body = response.body?.string() ?: throw IllegalStateException("Empty body")
			val page = json.decodeFromString(JellyseerrRequestPage.serializer(), body)

			page.results.map { dto ->
				val title = dto.media?.title ?: dto.media?.name ?: ""
				val posterUrl = posterImageUrl(dto.media?.posterPath)
				val backdropUrl = backdropImageUrl(dto.media?.backdropPath)
				JellyseerrRequest(
					id = dto.id,
					status = dto.status,
					mediaType = dto.media?.mediaType,
					title = title,
					tmdbId = dto.media?.tmdbId,
					posterPath = posterUrl,
					backdropPath = backdropUrl,
				)
			}
		}
	}

	result.onSuccess { writeCache(cacheKey, it) }
		.onFailure { Timber.e(it, "Failed to load Jellyseerr requests") }

	result
}

override suspend fun getRecentRequests(): Result<List<JellyseerrSearchItem>> = withContext(Dispatchers.IO) {
	val config = getConfig()
		?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

	val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
	val cacheKey = "requests:recent:$userId"
	readCache<List<JellyseerrSearchItem>>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }
	// Alle Anfragen vom aktuellen User anzeigen - verwende requestedBy Parameter, take=100 fc r alle
	val url = "${config.baseUrl}/api/v1/request?requestedBy=${userId}&take=100&sort=modified&skip=0"
	val request = Request.Builder()
		.url(url)
		.header("X-API-Key", config.apiKey)
		.header("X-API-User", userId.toString())
		.build()

	val result = runCatching {
		client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

			val body = response.body?.string() ?: throw IllegalStateException("Empty body")
			val page = json.decodeFromString(JellyseerrRequestPage.serializer(), body)

			Timber.d("Jellyseerr recent requests: found ${page.results.size} requests")

			page.results.mapNotNull { dto ->
				val tmdbId = dto.media?.tmdbId ?: return@mapNotNull null
				val mediaType = dto.media?.mediaType ?: return@mapNotNull null

				val detailsUrl = if (mediaType == "movie") {
					"${config.baseUrl}/api/v1/movie/$tmdbId"
				} else {
					"${config.baseUrl}/api/v1/tv/$tmdbId"
				}

				val detailsRequest = Request.Builder()
					.url(detailsUrl)
					.header("X-API-Key", config.apiKey)
					.header("X-API-User", userId.toString())
					.build()

				try {
					client.newCall(detailsRequest).execute().use { detailsResponse ->
						if (!detailsResponse.isSuccessful) {
							Timber.w("Failed to load details for $mediaType $tmdbId: HTTP ${detailsResponse.code}")
							return@mapNotNull null
						}

						val detailsBody = detailsResponse.body?.string() ?: return@mapNotNull null
						val details = json.decodeFromString(JellyseerrMovieDetails.serializer(), detailsBody)

						val posterUrl = posterImageUrl(details.posterPath)
						val backdropUrl = backdropImageUrl(details.backdropPath)
						val dateValue = details.releaseDate ?: details.firstAirDate
						val titleValue = details.title ?: details.name ?: ""

						JellyseerrSearchItem(
							id = tmdbId,
							mediaType = mediaType,
							title = titleValue,
							overview = details.overview,
							posterPath = posterUrl,
							backdropPath = backdropUrl,
							releaseDate = dateValue,
							isRequested = dto.status != null && dto.status != 5,
							isAvailable = dto.status == 5,
							isPartiallyAvailable = false,
							requestId = dto.id,
							requestStatus = dto.status,
						)
					}
				} catch (e: Exception) {
					Timber.e(e, "Failed to load details for $mediaType $tmdbId")
					null
				}
			}
		}
	}

	result.onSuccess { writeCache(cacheKey, it) }
		.onFailure { Timber.e(it, "Failed to load Jellyseerr recent requests") }

	result
}

	override suspend fun createRequest(item: JellyseerrSearchItem, seasons: List<Int>?): Result<Unit> = withContext(Dispatchers.IO) {
			val config = getConfig()
				?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

			val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }

		val body = JellyseerrCreateRequestBody(
			mediaType = item.mediaType,
			mediaId = item.id,
			userId = userId,
			seasons = seasons,
		)

		val jsonBody = json.encodeToString(JellyseerrCreateRequestBody.serializer(), body)
		val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

		val url = "${config.baseUrl}/api/v1/request"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.post(requestBody)
			.build()

			runCatching {
				client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
				}
			}.onSuccess {
				clearTvAvailabilityCache()
				invalidateCache(
					"requests:",
					"search:",
					"movieDetails:",
					"tvDetails:",
					"collection:",
					"person:",
					"personCredits:",
					"episodes:",
					"genres:",
				)
			}.onFailure {
				Timber.e(it, "Failed to create Jellyseerr request")
			}
		}

	override suspend fun cancelRequest(requestId: Int): Result<Unit> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }

		val url = "${config.baseUrl}/api/v1/request/$requestId"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.delete()
			.build()

			runCatching {
				client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
				}
			}.onSuccess {
				clearTvAvailabilityCache()
				invalidateCache(
					"requests:",
					"search:",
					"movieDetails:",
					"tvDetails:",
					"collection:",
					"person:",
					"personCredits:",
					"episodes:",
					"genres:",
				)
			}.onFailure {
				Timber.e(it, "Failed to cancel Jellyseerr request")
			}
		}

	override suspend fun discoverTrending(page: Int): Result<List<JellyseerrSearchItem>> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:trending:$page"
		readCache<List<JellyseerrSearchItem>>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/trending?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrSearchResponse.serializer(), body)

				result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr trending titles") }

		result
	}

	override suspend fun discoverMovies(page: Int): Result<List<JellyseerrSearchItem>> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:movies:$page"
		readCache<List<JellyseerrSearchItem>>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/movies?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrSearchResponse.serializer(), body)

				result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr discover movies") }

		result
	}

	override suspend fun discoverTv(page: Int): Result<List<JellyseerrSearchItem>> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:tv:$page"
		readCache<List<JellyseerrSearchItem>>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/tv?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrSearchResponse.serializer(), body)

				result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr discover tv") }

		result
	}

	override suspend fun discoverUpcomingMovies(page: Int): Result<List<JellyseerrSearchItem>> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:upcoming:movie:$page"
		readCache<List<JellyseerrSearchItem>>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/movies/upcoming?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrSearchResponse.serializer(), body)

				result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr upcoming movies") }

		result
	}

	override suspend fun discoverUpcomingTv(page: Int): Result<List<JellyseerrSearchItem>> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:upcoming:tv:$page"
		readCache<List<JellyseerrSearchItem>>(cacheKey, CACHE_TTL_SHORT)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/tv/upcoming?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrSearchResponse.serializer(), body)

				result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr upcoming tv") }

		result
	}

	override suspend fun discoverMoviesByGenre(genreId: Int, page: Int): Result<JellyseerrGenreDiscovery> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:genre:movie:$genreId:$page"
		readCache<JellyseerrGenreDiscovery>(cacheKey, CACHE_TTL_MEDIUM)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/movies/genre/$genreId?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrGenreDiscoveryDto.serializer(), body)

				val mappedResults = result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }

					JellyseerrGenreDiscovery(
						genre = result.genre,
						results = mappedResults,
						page = result.page,
						totalPages = result.totalPages,
						totalResults = result.totalResults,
					)
				}
			}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr movies by genre $genreId") }

		result
		}

	override suspend fun discoverTvByGenre(genreId: Int, page: Int): Result<JellyseerrGenreDiscovery> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:genre:tv:$genreId:$page"
		readCache<JellyseerrGenreDiscovery>(cacheKey, CACHE_TTL_MEDIUM)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/tv/genre/$genreId?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrGenreDiscoveryDto.serializer(), body)

				val mappedResults = result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }

					JellyseerrGenreDiscovery(
						genre = result.genre,
						results = mappedResults,
						page = result.page,
						totalPages = result.totalPages,
						totalResults = result.totalResults,
					)
				}
			}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr series by genre $genreId") }

		result
		}

	override suspend fun discoverMoviesByStudio(studioId: Int, page: Int): Result<JellyseerrCompanyDiscovery> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:studio:$studioId:$page"
		readCache<JellyseerrCompanyDiscovery>(cacheKey, CACHE_TTL_MEDIUM)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/movies/studio/$studioId?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrStudioDiscoveryDto.serializer(), body)

				val mappedResults = result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }

					JellyseerrCompanyDiscovery(
						company = mapCompanyDtoToModel(result.studio),
						results = mappedResults,
						page = result.page,
						totalPages = result.totalPages,
						totalResults = result.totalResults,
					)
				}
			}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr movies by studio $studioId") }

		result
		}

	override suspend fun discoverTvByNetwork(networkId: Int, page: Int): Result<JellyseerrCompanyDiscovery> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "discover:network:$networkId:$page"
		readCache<JellyseerrCompanyDiscovery>(cacheKey, CACHE_TTL_MEDIUM)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/discover/tv/network/$networkId?page=$page"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val result = json.decodeFromString(JellyseerrNetworkDiscoveryDto.serializer(), body)

				val mappedResults = result.results
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { mapSearchItemDtoToModel(it) }

					JellyseerrCompanyDiscovery(
						company = mapCompanyDtoToModel(result.network),
						results = mappedResults,
						page = result.page,
						totalPages = result.totalPages,
						totalResults = result.totalResults,
					)
				}
			}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr series by network $networkId") }

		result
		}

@Serializable
private data class MediaInfo(
	val certification: String? = null,
	val seasons: List<MediaInfoSeason> = emptyList(),
	val requests: List<MediaInfoRequest> = emptyList(),
)

	@Serializable
	private data class MediaInfoSeason(
		val seasonNumber: Int,
		val status: Int? = null,
		val episodes: List<MediaInfoEpisode> = emptyList(),
	)

	@Serializable
	private data class MediaInfoEpisode(
		val id: Int,
		val episodeNumber: Int,
		val status: Int? = null,
	)

	@Serializable
	private data class MediaInfoRequest(
		val id: Int,
		val status: Int? = null,
		val is4k: Boolean = false,
		val seasons: List<MediaInfoRequestSeason> = emptyList(),
	)

	@Serializable
	private data class MediaInfoRequestSeason(
		val id: Int,
		val seasonNumber: Int,
		val status: Int? = null,
	)

@Serializable
private data class ReleaseResult(
	val iso_3166_1: String,
	val release_dates: List<ReleaseDate> = emptyList(),
)

@Serializable
private data class ReleaseDate(
	val certification: String? = null,
	val type: Int? = null,
)

@Serializable
private data class Releases(
	val results: List<ReleaseResult> = emptyList(),
)

@Serializable
private data class JellyseerrCollectionRaw(
	val id: Int,
	val name: String,
	val backdrop_path: String? = null,
	val poster_path: String? = null,
)

@Serializable
private data class JellyseerrMovieDetailsRaw(
	val id: Int,
	val title: String? = null,
	val name: String? = null,
	val originalTitle: String? = null,
	val overview: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val releaseDate: String? = null,
	val firstAirDate: String? = null,
	val runtime: Int? = null,
	val voteAverage: Double? = null,
	val certification: String? = null,
	val genres: List<JellyseerrGenre> = emptyList(),
	val credits: JellyseerrCredits? = null,
	val mediaInfo: MediaInfo? = null,
	val releases: Releases? = null,
	// Jellyseerr can expose this in different casings, so support all of them
	@SerialName("belongsToCollection")
	val belongsToCollection: JellyseerrCollectionRaw? = null,
	@SerialName("belongs_to_collection")
	val legacyBelongsToCollection: JellyseerrCollectionRaw? = null,
	@SerialName("collection")
	val collection: JellyseerrCollectionRaw? = null,
) 

@Serializable
private data class ContentRating(
	val iso_3166_1: String,
	val rating: String? = null,
)

@Serializable
private data class ContentRatings(
	val results: List<ContentRating> = emptyList(),
)

@Serializable
private data class JellyseerrTvDetailsRaw(
	val id: Int,
	val name: String? = null,
	val overview: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val firstAirDate: String? = null,
	val episodeRunTime: List<Int> = emptyList(),
	val voteAverage: Double? = null,
	val certification: String? = null,
	val genres: List<JellyseerrGenre> = emptyList(),
	val credits: JellyseerrCredits? = null,
	val seasons: List<JellyseerrSeason> = emptyList(),
	val mediaInfo: MediaInfo? = null,
	val content_ratings: ContentRatings? = null,
)

@Serializable
private data class JellyseerrSeasonDetailsRaw(
	val id: Int,
	val name: String? = null,
	val overview: String? = null,
	val posterPath: String? = null,
	val seasonNumber: Int,
	val episodeCount: Int? = null,
	val airDate: String? = null,
	val status: Int? = null,
	val episodes: List<JellyseerrEpisodeRaw> = emptyList(),
)

@Serializable
private data class JellyseerrEpisodeRaw(
	val id: Int,
	val name: String? = null,
	val overview: String? = null,
	val stillPath: String? = null,
	val posterPath: String? = null,
	val episodeNumber: Int? = null,
	val seasonNumber: Int? = null,
	val airDate: String? = null,
	val missing: Boolean = false,
)

override suspend fun getMovieDetails(tmdbId: Int): Result<JellyseerrMovieDetails> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "movieDetails:$tmdbId"
		readCache<JellyseerrMovieDetails>(cacheKey, CACHE_TTL_MEDIUM)?.let { cached ->
			val castOk = cached.credits?.cast?.all { !it.profilePath.isNullOrBlank() } ?: true
			if (!cached.posterPath.isNullOrBlank() && castOk) {
				return@withContext Result.success(cached)
			}
			// Poster/Cast-Bilder fehlen im Cache -> frisch laden
			Timber.d("Jellyseerr getMovieDetails cache skipped because of missing images")
		}

		val url = "${config.baseUrl}/api/v1/movie/$tmdbId"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				Timber.d("Jellyseerr getMovieDetails raw response: $body")
				val raw = json.decodeFromString(JellyseerrMovieDetailsRaw.serializer(), body)
				Timber.d("Jellyseerr getMovieDetails decoded: certification='${raw.certification}', mediaInfo.certification='${raw.mediaInfo?.certification}'")

				val mappedCredits = raw.credits?.let { credits ->
					val mappedCast = credits.cast.map { castMember ->
						val profile = profileImageUrl(castMember.profilePath)
						castMember.copy(profilePath = profile)
					}
					credits.copy(cast = mappedCast)
				}

				// Extract certification from releases structure (TMDB format)
				// Get user's country code from system locale (e.g., "DE" for Germany)
				val userCountry = java.util.Locale.getDefault().country.uppercase()

				// Log all available certifications for debugging
				raw.releases?.results?.forEach { release ->
					release.release_dates.forEach { releaseDate ->
						if (releaseDate.certification?.isNotBlank() == true) {
							Timber.d("Jellyseerr getMovieDetails available certification: ${release.iso_3166_1} = '${releaseDate.certification}'")
						}
					}
				}

				// Try to find certification for user's region first
				val certificationFromReleases = raw.releases?.results
					?.find { it.iso_3166_1 == userCountry }
					?.release_dates
					?.find { it.certification?.isNotBlank() == true }
					?.certification
					?: run {
						// Fallback to US certification
						raw.releases?.results
							?.find { it.iso_3166_1 == "US" }
							?.release_dates
							?.find { it.certification?.isNotBlank() == true }
							?.certification
					}
					?: run {
						// Final fallback: any certification available
						raw.releases?.results
							?.flatMap { it.release_dates }
							?.find { it.certification?.isNotBlank() == true }
							?.certification
					}

				val finalCertification = raw.certification
					?: certificationFromReleases
					?: raw.mediaInfo?.certification

				Timber.i("Jellyseerr getMovieDetails for ${raw.title}: userCountry='$userCountry', finalCertification='$finalCertification'")
				val rawCollection = raw.belongsToCollection ?: raw.legacyBelongsToCollection ?: raw.collection
				Timber.d("Jellyseerr getMovieDetails collection raw: $rawCollection")

				// Map collection if present (support multiple casing variants from the API)
				val collection = rawCollection?.let { collectionRaw ->
					Timber.i("Jellyseerr getMovieDetails mapping collection: id=${collectionRaw.id}, name='${collectionRaw.name}'")
					JellyseerrCollection(
						id = collectionRaw.id,
						name = collectionRaw.name,
						backdropPath = backdropImageUrl(collectionRaw.backdrop_path),
						posterPath = posterImageUrl(collectionRaw.poster_path),
					)
				}

				JellyseerrMovieDetails(
					id = raw.id,
					title = raw.title ?: raw.name,
					name = raw.name,
					originalTitle = raw.originalTitle,
					overview = raw.overview,
					posterPath = posterImageUrl(raw.posterPath),
					backdropPath = backdropImageUrl(raw.backdropPath),
					releaseDate = raw.releaseDate,
					firstAirDate = raw.firstAirDate,
					runtime = raw.runtime,
					voteAverage = raw.voteAverage,
					certification = finalCertification,
					genres = raw.genres,
					credits = mappedCredits ?: raw.credits,
					collection = collection,
				)
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr movie details") }

		result
	}

override suspend fun getTvDetails(tmdbId: Int): Result<JellyseerrMovieDetails> = withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
		val cacheKey = "tvDetails:$tmdbId"
		readCache<JellyseerrMovieDetails>(cacheKey, CACHE_TTL_MEDIUM)?.let { cached ->
			val castOk = cached.credits?.cast?.all { !it.profilePath.isNullOrBlank() } ?: true
			if (!cached.posterPath.isNullOrBlank() && castOk) {
				return@withContext Result.success(cached)
			}
			Timber.d("Jellyseerr getTvDetails cache skipped because of missing images")
		}

		val url = "${config.baseUrl}/api/v1/tv/$tmdbId"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.header("X-API-User", userId.toString())
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val raw = json.decodeFromString(JellyseerrTvDetailsRaw.serializer(), body)

				val mappedCredits = raw.credits?.let { credits ->
					val mappedCast = credits.cast.map { castMember ->
						val profile = profileImageUrl(castMember.profilePath)
						castMember.copy(profilePath = profile)
					}
					credits.copy(cast = mappedCast)
				}

				// Merge status from mediaInfo into seasons
				// Wie Jellyseerr: Extrahiere angefragte Seasons aus requests
				val requestedSeasonNumbers = (raw.mediaInfo?.requests ?: emptyList())
					.filter { request ->
						request.is4k == false &&
						request.status != null &&
						request.status != 3 // 3 = DECLINED
					}
					.flatMap { request -> request.seasons.map { it.seasonNumber } }
					.toSet()

				// Status map für verfügbare Seasons (mit Episode-Details)
				val mediaInfoSeasons = raw.mediaInfo?.seasons ?: emptyList()
				val availableStatusMap = mediaInfoSeasons.associate { it.seasonNumber to it }

				val mappedSeasons = raw.seasons.map { season ->
					val mediaInfoSeason = availableStatusMap[season.seasonNumber]
					val availabilityStatus = mediaInfoSeason?.status
					val isRequested = requestedSeasonNumbers.contains(season.seasonNumber)

					// Prüfe Episode-Level-Verfügbarkeit
					val episodes = mediaInfoSeason?.episodes ?: emptyList()
					val availableEpisodes = episodes.count { it.status == 5 }
					val totalEpisodes = season.episodeCount ?: episodes.size
					val hasPartialEpisodes = availableEpisodes > 0 && availableEpisodes < totalEpisodes

					// Status-Logik wie in Jellyseerr:
					// - Wenn alle Episoden verfügbar (5), verwende 5
					// - Wenn teilweise verfügbar (mindestens eine Episode), verwende 4
					// - Wenn angefragt und nicht verfügbar, verwende Request-Status (z.B. 2 = Approved)
					// - Sonst null (nicht angefragt, nicht verfügbar)
					val finalStatus = when {
						availabilityStatus == 5 -> 5 // Fully Available
						hasPartialEpisodes -> 4 // Partially Available (some episodes available)
						isRequested -> 2 // Requested/Approved (wird als "angefragt" angezeigt)
						else -> null // Nicht angefragt - ignoriere den alten mediaInfo.seasons Status
					}

					season.copy(
						posterPath = posterImageUrl(season.posterPath),
						status = finalStatus,
					)
				}

				// Extract certification from content_ratings structure (TMDB format for TV)
				// Get user's country code from system locale (e.g., "DE" for Germany)
				val userCountry = java.util.Locale.getDefault().country.uppercase()

				// Log all available certifications for debugging
				raw.content_ratings?.results?.forEach { rating ->
					if (rating.rating?.isNotBlank() == true) {
						Timber.d("Jellyseerr getTvDetails available certification: ${rating.iso_3166_1} = '${rating.rating}'")
					}
				}

				// Try to find certification for user's region first
				val certificationFromRatings = raw.content_ratings?.results
					?.find { it.iso_3166_1 == userCountry }
					?.rating
					?: run {
						// Fallback to US certification
						raw.content_ratings?.results
							?.find { it.iso_3166_1 == "US" }
							?.rating
					}
					?: run {
						// Final fallback: any certification available
						raw.content_ratings?.results
							?.find { it.rating?.isNotBlank() == true }
							?.rating
					}

				val finalCertification = raw.certification
					?: certificationFromRatings
					?: raw.mediaInfo?.certification

				Timber.i("Jellyseerr getTvDetails for ${raw.name}: userCountry='$userCountry', finalCertification='$finalCertification'")

				JellyseerrMovieDetails(
					id = raw.id,
					title = raw.name,
					originalTitle = null,
					overview = raw.overview,
					posterPath = posterImageUrl(raw.posterPath),
					backdropPath = backdropImageUrl(raw.backdropPath),
					releaseDate = raw.firstAirDate,
					runtime = raw.episodeRunTime.firstOrNull(),
					voteAverage = raw.voteAverage,
					certification = finalCertification,
					genres = raw.genres,
					credits = mappedCredits ?: raw.credits,
					seasons = mappedSeasons,
				)
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr tv details") }

		result
	}

	override suspend fun getSeasonEpisodes(tmdbId: Int, seasonNumber: Int): Result<List<JellyseerrEpisode>> =
		withContext(Dispatchers.IO) {
			val config = getConfig()
				?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

			val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
			val cacheKey = "episodes:$tmdbId:$seasonNumber"
			readCache<List<JellyseerrEpisode>>(cacheKey, CACHE_TTL_MEDIUM)?.let { return@withContext Result.success(it) }

			// Erst TV-Details laden um den Seriennamen zu bekommen
			val tvDetailsUrl = "${config.baseUrl}/api/v1/tv/$tmdbId"
			val tvDetailsRequest = Request.Builder()
				.url(tvDetailsUrl)
 				.header("X-API-Key", config.apiKey)
 				.header("X-API-User", userId.toString())
 				.build()

 			var seriesName: String? = null
 			try {
 				client.newCall(tvDetailsRequest).execute().use { response ->
 					if (response.isSuccessful) {
 						val body = response.body?.string()
 						if (body != null) {
 							val tvDetails = json.decodeFromString(JellyseerrTvDetailsRaw.serializer(), body)
 							seriesName = tvDetails.name
 						}
 					}
 				}
 			} catch (e: Exception) {
 				Timber.w(e, "Failed to load TV details for series name")
 			}

 			// Prüfe Episode-Verfügbarkeit direkt in Jellyfin
 			val availableEpisodeMap = mutableMapOf<Int, String>() // episodeNumber -> jellyfinId
 			if (!seriesName.isNullOrBlank()) {
 				try {
 					// Suche Serie in Jellyfin
 					val seriesRequest = GetItemsRequest(
 						searchTerm = seriesName,
 						limit = 5,
 						includeItemTypes = setOf(BaseItemKind.SERIES),
 						recursive = true,
 						enableTotalRecordCount = false,
 					)
 					val seriesItems = apiClient.itemsApi.getItems(seriesRequest).content.items

 					val matchedSeries = seriesItems?.firstOrNull { baseItem ->
 						val name = baseItem.name?.trim().orEmpty()
 						name.equals(seriesName, ignoreCase = true)
 					}

 					if (matchedSeries != null) {
 						// Lade Episoden für diese Staffel aus Jellyfin
 						val episodesRequest = GetItemsRequest(
 							parentId = matchedSeries.id,
 							includeItemTypes = setOf(BaseItemKind.EPISODE),
 							recursive = true,
 							enableTotalRecordCount = false,
 						)
 						val jellyfinEpisodes = apiClient.itemsApi.getItems(episodesRequest).content.items

 						jellyfinEpisodes?.forEach { ep ->
 							if (ep.parentIndexNumber == seasonNumber && ep.indexNumber != null) {
 								availableEpisodeMap[ep.indexNumber!!] = ep.id.toString()
 							}
 						}
 					}
 				} catch (e: Exception) {
 					Timber.w(e, "Failed to check episode availability in Jellyfin")
 				}
 			}

 			val url = "${config.baseUrl}/api/v1/tv/$tmdbId/season/$seasonNumber"
			val request = Request.Builder()
				.url(url)
				.header("X-API-Key", config.apiKey)
				.header("X-API-User", userId.toString())
				.build()

			val result = runCatching {
				client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

					val body = response.body?.string() ?: throw IllegalStateException("Empty body")
					val raw = json.decodeFromString(JellyseerrSeasonDetailsRaw.serializer(), body)

 					raw.episodes.map { episode ->
 						val imageUrl = episode.stillPath?.let(::posterImageUrl)
 							?: episode.posterPath?.let(::posterImageUrl)

 						val epNumber = episode.episodeNumber ?: 0
 						val jellyfinEpisodeId = availableEpisodeMap[epNumber]
 						val isEpisodeAvailable = jellyfinEpisodeId != null

 						JellyseerrEpisode(
 							id = episode.id,
 							name = episode.name,
 							overview = episode.overview,
 							imageUrl = imageUrl,
 							episodeNumber = episode.episodeNumber,
 							seasonNumber = episode.seasonNumber,
 							airDate = episode.airDate,
 							isMissing = episode.missing,
 							isAvailable = isEpisodeAvailable,
							jellyfinId = jellyfinEpisodeId,
						)
					}
				}
			}

			result.onSuccess { writeCache(cacheKey, it) }
				.onFailure { Timber.e(it, "Failed to load Jellyseerr episodes for tv $tmdbId season $seasonNumber") }

			result
		}

		override suspend fun getCollectionDetails(collectionId: Int): Result<JellyseerrGenreDiscovery> =
			withContext(Dispatchers.IO) {
				val config = getConfig()
					?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

				val userId = resolveCurrentUserId(config).getOrElse { return@withContext Result.failure(it) }
				val cacheKey = "collection:$collectionId"
				readCache<JellyseerrGenreDiscovery>(cacheKey, CACHE_TTL_MEDIUM)?.let { cached ->
					val postersOk = cached.results.all { !it.posterPath.isNullOrBlank() }
					if (postersOk) return@withContext Result.success(cached)
					Timber.d("Jellyseerr getCollectionDetails cache skipped because of missing posters")
				}

				val url = "${config.baseUrl}/api/v1/collection/$collectionId"
				val request = Request.Builder()
					.url(url)
					.header("X-API-Key", config.apiKey)
					.header("X-API-User", userId.toString())
					.build()

				val result = runCatching {
					client.newCall(request).execute().use { response ->
						if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

					val body = response.body?.string() ?: throw IllegalStateException("Empty body")

					// Parse collection response
					val collectionData = json.parseToJsonElement(body).jsonObject
					val name = collectionData["name"]?.jsonPrimitive?.content ?: "Collection"
					val parts = collectionData["parts"]?.jsonArray?.map { part ->
						val partObj = part.jsonObject
						val id = partObj["id"]?.jsonPrimitive?.int ?: 0
						val title = partObj["title"]?.jsonPrimitive?.content ?: ""
						val overview = partObj["overview"]?.jsonPrimitive?.contentOrNull
						val posterPath = partObj["posterPath"]?.jsonPrimitive?.contentOrNull?.let { posterImageUrl(it) }
						val backdropPath = partObj["backdropPath"]?.jsonPrimitive?.contentOrNull?.let { backdropImageUrl(it) }
						val releaseDate = partObj["releaseDate"]?.jsonPrimitive?.contentOrNull
						val mediaInfo = partObj["mediaInfo"]?.jsonObject
						val jellyfinId = mediaInfo?.get("jellyfinMediaId")?.jsonPrimitive?.contentOrNull

						// Status can live on mediaInfo.status, mediaInfo.requests[x].status or root requests[x].status
						fun statusFromRequests(requests: kotlinx.serialization.json.JsonArray?): Int? {
							val non4k = requests
								?.firstNotNullOfOrNull { req ->
									val obj = req.jsonObject
									val is4k = obj["is4k"]?.jsonPrimitive
										?.contentOrNull
										?.equals("true", ignoreCase = true) == true
									val statusVal = obj["status"]?.jsonPrimitive?.intOrNull
									if (!is4k) statusVal else null
								}
							val anyStatus = requests
								?.firstNotNullOfOrNull { req ->
									req.jsonObject["status"]?.jsonPrimitive?.intOrNull
								}
							return non4k ?: anyStatus
						}

						val mediaInfoStatus = mediaInfo
							?.get("status")
							?.jsonPrimitive
							?.intOrNull

						val mediaInfoRequests = mediaInfo?.get("requests")?.jsonArray
						val rootRequests = partObj["requests"]?.jsonArray

						val requestStatusFromMediaInfo = statusFromRequests(mediaInfoRequests)
						val requestStatusFromRoot = statusFromRequests(rootRequests)

						val status = mediaInfoStatus ?: requestStatusFromMediaInfo ?: requestStatusFromRoot
						val hasAnyRequest = mediaInfoRequests?.isNotEmpty() == true || rootRequests?.isNotEmpty() == true
						val requestId = rootRequests
							?.firstOrNull()
							?.jsonObject
							?.get("id")
							?.jsonPrimitive
							?.intOrNull
							?: mediaInfoRequests
								?.firstOrNull()
								?.jsonObject
								?.get("id")
								?.jsonPrimitive
								?.intOrNull

						val isAvailable = status == 5
						val isPartiallyAvailable = status == 4
						val isRequested = (status != null && status != 5 && status != 4 && status != 3) || hasAnyRequest

						JellyseerrSearchItem(
							id = id,
							mediaType = "movie",
							title = title,
							overview = overview,
							posterPath = posterPath,
							backdropPath = backdropPath,
							releaseDate = releaseDate,
							isAvailable = isAvailable,
							isPartiallyAvailable = isPartiallyAvailable,
							isRequested = isRequested,
							requestStatus = status,
							requestId = requestId,
							jellyfinId = jellyfinId,
						)
					} ?: emptyList()

					JellyseerrGenreDiscovery(
						genre = JellyseerrGenre(id = collectionId, name = name),
						results = parts,
						page = 1,
						totalPages = 1,
						totalResults = parts.size,
						)
					}
				}

				result.onSuccess { writeCache(cacheKey, it) }
					.onFailure { Timber.e(it, "Failed to load Jellyseerr collection $collectionId") }

				result
			}

override suspend fun getPersonDetails(personId: Int): Result<JellyseerrPersonDetails> =
    withContext(Dispatchers.IO) {
        val config = getConfig()
            ?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

        val cacheKey = "person:$personId"
        readCache<JellyseerrPersonDetails>(cacheKey, CACHE_TTL_LONG)?.let { return@withContext Result.success(it) }

        val url = "${config.baseUrl}/api/v1/person/$personId"
        val request = Request.Builder()
            .url(url)
            .header("X-API-Key", config.apiKey)
            .build()

        val result = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body?.string() ?: throw IllegalStateException("Empty body")
                val raw = json.decodeFromString(JellyseerrPersonDetails.serializer(), body)

                raw.copy(
                    profilePath = profileImageUrl(raw.profilePath),
                )
            }
        }

        result.onSuccess { writeCache(cacheKey, it) }
            .onFailure { Timber.e(it, "Failed to load Jellyseerr person details") }

        result
    }

override suspend fun getPersonCredits(personId: Int): Result<List<JellyseerrSearchItem>> =
	withContext(Dispatchers.IO) {
		val config = getConfig()
			?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

		val cacheKey = "personCredits:$personId"
		readCache<List<JellyseerrSearchItem>>(cacheKey, CACHE_TTL_LONG)?.let { return@withContext Result.success(it) }

		val url = "${config.baseUrl}/api/v1/person/$personId/combined_credits"
		val request = Request.Builder()
			.url(url)
			.header("X-API-Key", config.apiKey)
			.build()

		val result = runCatching {
			client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
				val body = response.body?.string() ?: throw IllegalStateException("Empty body")
				val combined = json.decodeFromString(JellyseerrCombinedCredits.serializer(), body)

				combined.cast
					.filter { it.mediaType == "movie" || it.mediaType == "tv" }
					.map { credit ->
						val posterUrl = posterImageUrl(credit.posterPath)
						val backdropUrl = backdropImageUrl(credit.backdropPath)
						val releaseDate = credit.releaseDate ?: credit.firstAirDate
						val status = credit.mediaInfo?.status
						val isAvailable = status == 5
						val isPartiallyAvailable = status == 4
						val jellyfinId = credit.mediaInfo?.jellyfinMediaId ?: credit.mediaInfo?.jellyfinMediaId4k
						JellyseerrSearchItem(
							id = credit.id,
							mediaType = credit.mediaType ?: "movie",
							title = (credit.title ?: credit.name).orEmpty(),
							overview = credit.overview,
							posterPath = posterUrl,
							backdropPath = backdropUrl,
							releaseDate = releaseDate,
							isAvailable = isAvailable,
							isPartiallyAvailable = isPartiallyAvailable,
							jellyfinId = jellyfinId,
						)
					}
			}
		}

		result.onSuccess { writeCache(cacheKey, it) }
			.onFailure { Timber.e(it, "Failed to load Jellyseerr person credits") }

		result
	}

override suspend fun getMovieGenres(): Result<List<JellyseerrGenreSlider>> =
		withContext(Dispatchers.IO) {
			val config = getConfig()
				?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

			readCache<List<JellyseerrGenreSlider>>("genres:movie", CACHE_TTL_LONG)?.let { return@withContext Result.success(it) }

			val url = "${config.baseUrl}/api/v1/discover/genreslider/movie"
			val request = Request.Builder()
				.url(url)
				.header("X-API-Key", config.apiKey)
				.build()

			val result = runCatching {
				client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
					val body = response.body?.string() ?: throw IllegalStateException("Empty body")
					val genres = json.decodeFromString<List<JellyseerrGenreSliderItem>>(body)

					genres.map { genre ->
						// Verwende das 5. Backdrop (Index 4) wie in Jellyseerr, oder das erste verfügbare
						val backdropPath = genre.backdrops.getOrNull(4)
							?: genre.backdrops.getOrNull(0)
						JellyseerrGenreSlider(
							id = genre.id,
							name = genre.name,
							backdropUrl = genreBackdropImageUrl(backdropPath, genre.id),
						)
					}
				}
			}

			result.onSuccess { writeCache("genres:movie", it) }
				.onFailure { Timber.e(it, "Failed to load Jellyseerr movie genres") }

			result
		}

override suspend fun getTvGenres(): Result<List<JellyseerrGenreSlider>> =
		withContext(Dispatchers.IO) {
			val config = getConfig()
				?: return@withContext Result.failure(IllegalStateException("Jellyseerr not configured"))

			readCache<List<JellyseerrGenreSlider>>("genres:tv", CACHE_TTL_LONG)?.let { return@withContext Result.success(it) }

			val url = "${config.baseUrl}/api/v1/discover/genreslider/tv"
			val request = Request.Builder()
				.url(url)
				.header("X-API-Key", config.apiKey)
				.build()

			val result = runCatching {
				client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
					val body = response.body?.string() ?: throw IllegalStateException("Empty body")
					val genres = json.decodeFromString<List<JellyseerrGenreSliderItem>>(body)

					genres.map { genre ->
						// Verwende das 5. Backdrop (Index 4) wie in Jellyseerr, oder das erste verfügbare
						val backdropPath = genre.backdrops.getOrNull(4)
							?: genre.backdrops.getOrNull(0)
						JellyseerrGenreSlider(
							id = genre.id,
							name = genre.name,
							backdropUrl = genreBackdropImageUrl(backdropPath, genre.id),
						)
					}
				}
			}

			result.onSuccess { writeCache("genres:tv", it) }
				.onFailure { Timber.e(it, "Failed to load Jellyseerr TV genres") }

			result
		}
}
