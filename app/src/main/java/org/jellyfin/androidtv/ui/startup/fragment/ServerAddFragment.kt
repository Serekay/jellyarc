package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.ConnectingState
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.databinding.FragmentServerAddBinding
import org.jellyfin.androidtv.ui.startup.ServerAddViewModel
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.tailscale.TailscaleManager
import org.jellyfin.androidtv.util.getSummary
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.android.ext.android.inject
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import timber.log.Timber
import kotlinx.coroutines.runBlocking

class ServerAddFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ADDRESS = "server_address"
	}

	private val startupViewModel: ServerAddViewModel by viewModel()
	private val serverRepository: ServerRepository by inject()
	private var _binding: FragmentServerAddBinding? = null
	private val binding get() = _binding!!

	private var vpnPermissionCallback: ((Boolean) -> Unit)? = null

	private val vpnPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		vpnPermissionCallback?.invoke(result.resultCode == Activity.RESULT_OK)
		vpnPermissionCallback = null
	}

	private val serverAddressArgument get() = arguments?.getString(ARG_SERVER_ADDRESS)?.ifBlank { null }
	private var currentDialog: AlertDialog? = null
	private var useTailscale = false

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentServerAddBinding.inflate(inflater, container, false)

		with(binding.address) {
			setOnEditorActionListener { _, actionId, _ ->
				when (actionId) {
					EditorInfo.IME_ACTION_DONE -> {
						submitAddress()
						true
					}
					else -> false
				}
			}
		}

		// "Lokal verbinden" Button
		binding.connectLocal.setOnClickListener {
			Timber.d("User selected: Local connection")
			useTailscale = false
			showAddressInput()
		}

		// "Über Tailscale VPN" Button
		binding.connectTailscale.setOnClickListener {
			Timber.d("User selected: Tailscale VPN connection")
			useTailscale = true
			// Buttons disablen + Loading-Indikator
			setButtonsEnabled(false)
			binding.connectTailscale.text = "Verbinde mit Tailscale..."
			startTailscaleFlow()
		}

		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (serverAddressArgument != null) {
			binding.address.setText(serverAddressArgument)
			binding.address.isEnabled = false
			submitAddress()
		}

		startupViewModel.state.onEach { state ->
			when (state) {
				is ConnectingState -> {
					binding.address.isEnabled = false
					binding.connectLocal.isEnabled = false
					binding.connectTailscale.isEnabled = false
					binding.error.text = getString(R.string.server_connecting, state.address)
				}

				is UnableToConnectState -> {
					binding.address.isEnabled = true
					setButtonsEnabled(true)
					binding.error.text = getString(
						R.string.server_connection_failed_candidates,
						state.addressCandidates
							.map { "${it.key} - ${it.value.getSummary(requireContext())}" }
							.joinToString(prefix = "\n", separator = "\n")
					)
				}

				is ConnectedState -> {
					// WICHTIG: Tailscale-Flag SOFORT setzen, bevor wir zum nächsten Screen wechseln!
					// Sonst wird der Status in den Settings falsch angezeigt.
					if (useTailscale) {
						runBlocking {
							serverRepository.setTailscaleEnabled(state.id, true)
							Timber.d("Set tailscaleEnabled=true for server ${state.id}")
						}
					}
					parentFragmentManager.commit {
						replace<StartupToolbarFragment>(R.id.content_view)
						add<ServerFragment>(
							R.id.content_view,
							null,
							bundleOf(ServerFragment.ARG_SERVER_ID to state.id.toString())
						)
					}
				}

				null -> Unit
			}
		}.launchIn(lifecycleScope)
	}

	override fun onDestroyView() {
		super.onDestroyView()
		currentDialog?.dismiss()
		currentDialog = null
		_binding = null
	}

	private fun setButtonsEnabled(enabled: Boolean) {
		binding.connectLocal.isEnabled = enabled
		binding.connectTailscale.isEnabled = enabled
		if (enabled) {
			binding.connectTailscale.text = getString(R.string.connect_tailscale_button)
		}
	}

	private fun showAddressInput() {
		// Buttons ausblenden
		binding.connectionTypeLabel.isVisible = false
		binding.connectLocal.isVisible = false
		binding.connectTailscale.isVisible = false

		// Server-Adresse anzeigen
		binding.addressLabel.isVisible = true
		binding.address.isVisible = true
		binding.address.requestFocus()
	}

	/**
	 * Tailscale-Flow - zeigt Info dass Gerät aus Dashboard entfernt werden sollte, dann Flow starten
	 */
	private fun startTailscaleFlow() {
		Timber.d("Tailscale VPN requested - showing dashboard removal info")
		// Show info that device should be removed from dashboard first, then continue
		AlertDialog.Builder(requireContext())
			.setTitle(R.string.tailscale_already_logged_in_title)
			.setMessage(R.string.tailscale_already_logged_in_message)
			.setPositiveButton(R.string.lbl_ok) { _, _ ->
				// User acknowledged - now start the actual VPN flow
				continueWithTailscaleFlow()
			}
			.setNegativeButton(R.string.lbl_cancel) { _, _ ->
				setButtonsEnabled(true)
			}
			.setCancelable(false)
			.show()
	}

	/**
	 * Führt den eigentlichen Tailscale-Flow aus (VPN Permission, Login-Code, Warten, VPN-Start)
	 */
	private fun continueWithTailscaleFlow() {
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				Timber.d("=== TAILSCALE FLOW START ===")

				// 1. VPN-Permission sicherstellen
				val permissionGranted = ensureVpnPermission()
				if (!permissionGranted) {
					Timber.w("VPN permission not granted by user")
					setButtonsEnabled(true)
					Toast.makeText(requireContext(), R.string.tailscale_vpn_permission_needed, Toast.LENGTH_LONG).show()
					return@launch
				}

				// 2. Login-Code anfordern
				Timber.d("Step 1: Requesting login code...")
				val codeResult = TailscaleManager.requestLoginCode()
				if (codeResult.isFailure) {
					Timber.e(codeResult.exceptionOrNull(), "Failed to request login code")
					setButtonsEnabled(true)
					AlertDialog.Builder(requireContext())
						.setTitle(R.string.tailscale_error_title)
						.setMessage(getString(R.string.tailscale_login_code_failed, codeResult.exceptionOrNull()?.message))
						.setPositiveButton(R.string.lbl_ok, null)
						.show()
					return@launch
				}

				val code = codeResult.getOrThrow()
				Timber.d("Step 1: Got login code: $code")

				// 3. Dialog mit Code
				Timber.d("Step 2: Showing dialog with code")
				currentDialog = AlertDialog.Builder(requireContext())
					.setTitle(R.string.tailscale_connecting_title)
					.setMessage(getString(R.string.tailscale_connecting_message, code))
					.setCancelable(false)
					.show()

				// 4. Warte auf loginFinished Event
				Timber.d("Step 3: Waiting for loginFinished event (max 120 seconds)...")
				val loginFinished = TailscaleManager.waitUntilLoginFinished(timeoutMs = 120_000L)

				currentDialog?.dismiss()
				currentDialog = null

				if (loginFinished) {
					Timber.d("Step 4: Login finished successfully!")

					// 5. VPN starten
					Timber.d("Step 4: Starting VPN after successful login...")
					val vpnStarted = TailscaleManager.startVpn()
					if (!vpnStarted) {
						Timber.w("VPN start failed - permission needed")
						setButtonsEnabled(true)
						Toast.makeText(requireContext(), R.string.tailscale_vpn_permission_required, Toast.LENGTH_LONG).show()
						return@launch
					}

					// 6. Warte bis VPN verbunden ist
					Timber.d("Step 5: Waiting for VPN to connect...")
					val vpnConnected = TailscaleManager.waitUntilConnected(timeoutMs = 60_000L)

					if (vpnConnected) {
						Timber.d("Step 5: VPN connected successfully!")
						AlertDialog.Builder(requireContext())
							.setTitle(R.string.tailscale_connected_title)
							.setMessage(R.string.tailscale_connected_message)
							.setPositiveButton(R.string.lbl_ok) { dialog, _ ->
								dialog.dismiss()
								showAddressInput()
							}
							.show()
					} else {
						Timber.e("Step 5: VPN connection timeout!")
						setButtonsEnabled(true)
						AlertDialog.Builder(requireContext())
							.setTitle(R.string.tailscale_vpn_timeout_title)
							.setMessage(R.string.tailscale_vpn_timeout_message)
							.setPositiveButton(R.string.lbl_ok) { _, _ ->
								setButtonsEnabled(true)
							}
							.show()
					}
				} else {
					Timber.e("Step 4: TIMEOUT - loginFinished event never received")
					setButtonsEnabled(true)
					AlertDialog.Builder(requireContext())
						.setTitle(R.string.tailscale_login_timeout_title)
						.setMessage(R.string.tailscale_login_timeout_message)
						.setPositiveButton(R.string.tailscale_retry_button) { _, _ ->
							setButtonsEnabled(true)
						}
						.setNegativeButton(R.string.lbl_cancel) { _, _ ->
							setButtonsEnabled(true)
						}
						.show()
				}

				Timber.d("=== TAILSCALE FLOW END ===")
			} catch (e: Exception) {
				Timber.e(e, "Tailscale flow continuation failed with exception")
				currentDialog?.dismiss()
				setButtonsEnabled(true)
				Toast.makeText(requireContext(), getString(R.string.tailscale_error_generic, e.message), Toast.LENGTH_LONG).show()
			}
		}
	}


	/**
	 * Zeigt den systemweiten VPN-Permission-Dialog an, falls nötig, und wartet auf das Ergebnis.
	 */
	private suspend fun ensureVpnPermission(): Boolean = suspendCoroutine { cont ->
		val intent = VpnService.prepare(requireContext())
		if (intent == null) {
			cont.resume(true)
			return@suspendCoroutine
		}

		vpnPermissionCallback = { granted ->
			cont.resume(granted)
		}
		try {
			vpnPermissionLauncher.launch(intent)
		} catch (t: Throwable) {
			vpnPermissionCallback = null
			cont.resumeWithException(t)
		}
	}

	/**
	 * Normalisiert eine Server-Adresse und stellt sicher dass sie ein gültiges Format hat.
	 *
	 * Unterstützt:
	 * - Reine IPs: 192.168.1.1 → http://192.168.1.1
	 * - IPs mit Port: 192.168.1.1:8096 → http://192.168.1.1:8096
	 * - Hostnamen: server → http://server
	 * - Hostnamen mit Port: server:8096 → http://server:8096
	 * - URLs mit Schema: http://server.com → bleibt unverändert
	 */
	private fun normalizeServerAddress(address: String): String {
		var normalized = address.trim()

		// Schema hinzufügen falls nicht vorhanden
		if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
			normalized = "http://$normalized"
			Timber.d("normalizeServerAddress: Added http:// schema: $normalized")
		}

		// Entferne trailing slashes
		normalized = normalized.trimEnd('/')

		Timber.d("normalizeServerAddress: Final address: '$address' → '$normalized'")
		return normalized
	}

	private fun submitAddress() = when {
		binding.address.text.isNotBlank() -> {
			val address = binding.address.text.toString().trim()

			// Use centralized address normalization
			val normalizedAddress = normalizeServerAddress(address)
			startupViewModel.addServer(normalizedAddress)
		}
		else -> binding.error.setText(R.string.server_field_empty)
	}
}
