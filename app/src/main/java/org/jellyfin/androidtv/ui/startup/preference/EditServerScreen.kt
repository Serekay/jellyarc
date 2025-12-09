package org.jellyfin.androidtv.ui.startup.preference

import android.app.ProgressDialog
import android.content.Intent
import android.net.VpnService
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.tailscale.TailscaleManager
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.getValue
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

class EditServerScreen : OptionsFragment() {
	private val startupViewModel: StartupViewModel by activityViewModel()
	private val serverUserRepository: ServerUserRepository by inject()
	private var isSwitching = false
	private var progressDialog: ProgressDialog? = null

	override val rebuildOnResume = true

	private var vpnPermissionCallback: ((Boolean) -> Unit)? = null
	private var currentDialog: AlertDialog? = null

	private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		vpnPermissionCallback?.invoke(result.resultCode == android.app.Activity.RESULT_OK)
		vpnPermissionCallback = null
	}

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

	private fun showProgressDialog() {
		progressDialog = ProgressDialog.show(
			requireContext(),
			"",
			getString(R.string.tailscale_switching_progress),
			true
		)
	}
	
	private fun hideProgressDialog() {
		progressDialog?.dismiss()
		progressDialog = null
	}

	private fun showRestartDialog() {
		AlertDialog.Builder(requireContext())
			.setTitle(R.string.restart_required_title)
			.setMessage(R.string.restart_required_message)
			.setCancelable(false)
			.setPositiveButton(R.string.restart_now) { _, _ ->
				val context = requireActivity()
				val packageManager = context.packageManager
				val intent = packageManager.getLaunchIntentForPackage(context.packageName)
				val componentName = intent!!.component
				val mainIntent = Intent.makeRestartActivityTask(componentName)
				context.startActivity(mainIntent)
				Runtime.getRuntime().exit(0)
			}
			.show()
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
			timber.log.Timber.d("normalizeServerAddress: Added http:// schema: $normalized")
		}

		// Entferne trailing slashes
		normalized = normalized.trimEnd('/')

		timber.log.Timber.d("normalizeServerAddress: Final address: '$address' → '$normalized'")
		return normalized
	}

	private suspend fun startActivationFlow(server: Server, serverUUID: UUID) {
		try {
			// 1. Show progress while requesting code
			showProgressDialog()

			// 2. Get VPN permission
			val permissionGranted = ensureVpnPermission()
			if (!permissionGranted) {
				hideProgressDialog()
				Toast.makeText(requireContext(), R.string.tailscale_vpn_permission_needed, Toast.LENGTH_LONG).show()
				isSwitching = false
				rebuild()
				return
			}

			// 3. Request login code
			val codeResult = TailscaleManager.requestLoginCode()
			hideProgressDialog()

			if (codeResult.isFailure) {
				throw codeResult.exceptionOrNull() ?: Exception("Failed to get login code")
			}
			val code = codeResult.getOrThrow()

			// 4. Show code and wait for login
			currentDialog = AlertDialog.Builder(requireContext())
				.setTitle(R.string.tailscale_connecting_title)
				.setMessage(getString(R.string.tailscale_connecting_message, code))
				.setCancelable(false)
				.show()

			val loginFinished = TailscaleManager.waitUntilLoginFinished(timeoutMs = 120_000L)
			currentDialog?.dismiss()
			currentDialog = null

			if (!loginFinished) {
				throw Exception("Login timed out. Please make sure to authorize the device in your Tailscale dashboard.")
			}

			// 5. Start VPN and wait until connected
			showProgressDialog()
			TailscaleManager.startVpn()
			val vpnConnected = TailscaleManager.waitUntilConnected(timeoutMs = 60_000L)
			hideProgressDialog()

			if (!vpnConnected) {
				throw Exception("VPN connection timed out after login.")
			}

			// 6. Ask for new address and validate BEFORE saving
			showAddressInputDialog(
				server = server,
				useTailscale = true,  // VPN wird aktiviert
				onCancel = {
					// User cancelled address input
					isSwitching = false
					rebuild()
				},
				onAddressSet = { newAddress ->
					// Validation happens in showAddressInputDialog
					// Only called if validation succeeds
					startupViewModel.setServerAddress(serverUUID, newAddress)
					startupViewModel.setTailscaleEnabled(serverUUID, true)
					isSwitching = false
					showRestartDialog()
				})

		} catch (e: Exception) {
			currentDialog?.dismiss()
			currentDialog = null
			hideProgressDialog()
			Toast.makeText(requireContext(), getString(R.string.tailscale_error_generic, e.message), Toast.LENGTH_LONG).show()
			isSwitching = false
			rebuild()
		}
	}

	private fun showAddressInputDialog(
		server: Server,
		useTailscale: Boolean = server.tailscaleEnabled,
		onCancel: () -> Unit = {},
		onAddressSet: (String) -> Unit
	) {
		val editText = EditText(requireContext()).apply {
			hint = getString(R.string.edit_server_address_hint)
			// Leave empty - user must enter the full address from admin
		}
		val dialog = AlertDialog.Builder(requireContext())
			.setTitle(R.string.edit_server_address_title)
			.setMessage(R.string.edit_server_address_message)
			.setView(editText)
			.setPositiveButton(R.string.lbl_ok, null) // Set to null. We override the handler later.
			.setNegativeButton(R.string.lbl_cancel) { _, _ ->
				onCancel()
			}
			.setOnCancelListener {
				onCancel()
			}
			.create()

		dialog.setOnShowListener {
			val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
			positiveButton.setOnClickListener {
				val newAddress = editText.text.toString().trim()
				if (newAddress.isBlank()) {
					editText.error = getString(R.string.server_field_empty)
					return@setOnClickListener
				}

				// Reset error and show progress
				editText.error = null
				showProgressDialog()

				lifecycleScope.launch {
					// Use centralized address normalization
					val addressToTest = normalizeServerAddress(newAddress)

					// Validiere die Adresse (jellyfin.discovery.getAddressCandidates macht schon Smart-Parsing)
					val isValid = try {
						startupViewModel.testServerAddress(addressToTest)
					} catch (e: Exception) {
						timber.log.Timber.e(e, "Address validation failed")
						false
					}

					hideProgressDialog()

					if (isValid) {
						onAddressSet(addressToTest)
						dialog.dismiss()
					} else {
						AlertDialog.Builder(requireContext())
							.setTitle(R.string.server_connection_failed_title)
							.setMessage(R.string.server_connection_failed_message)
							.setPositiveButton(R.string.lbl_ok) { _, _ -> }
							.show()
					}
				}
			}
		}
		dialog.show()
	}

	override val screen by optionsScreen {
		val serverUUID = requireNotNull(
			requireArguments().getValue<UUID>(ARG_SERVER_UUID)
		) { "Server null or malformed uuid" }

		val server = requireNotNull(startupViewModel.getServer(serverUUID)) { "Server not found" }
		val users = serverUserRepository.getStoredServerUsers(server)

		title = server.name

		if (users.isNotEmpty()) {
			category {
				setTitle(R.string.pref_accounts)

				users.forEach { user ->
					link {
						title = user.name
						icon = R.drawable.ic_user

						val lastUsedDate = LocalDateTime.ofInstant(
							Instant.ofEpochMilli(user.lastUsed),
							ZoneId.systemDefault()
						)
						content = context.getString(
							R.string.lbl_user_last_used,
							lastUsedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
							lastUsedDate.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
						)

						withFragment<EditUserScreen>(bundleOf(
							EditUserScreen.ARG_SERVER_UUID to server.id,
							EditUserScreen.ARG_USER_UUID to user.id,
						))
					}
				}
			}
		}

		category {
			setTitle(R.string.tailscale_section_title)

			checkbox {
				type = org.jellyfin.androidtv.ui.preference.dsl.OptionsItemCheckbox.Type.SWITCH
				setTitle(R.string.tailscale_enable_title)
				setContent(R.string.tailscale_enable_summary_on, R.string.tailscale_enable_summary_off)
				bind {
					get { server.tailscaleEnabled }
					set { enabled ->
						if (isSwitching) return@set
						isSwitching = true
						if (enabled) {
							// ### TAILSCALE ACTIVATION FLOW ###
							// Show info that device should be removed from dashboard first, then continue
							timber.log.Timber.d("Tailscale activation requested - showing dashboard removal info")
							AlertDialog.Builder(requireContext())
								.setTitle(R.string.tailscale_already_logged_in_title)
								.setMessage(R.string.tailscale_already_logged_in_message)
								.setPositiveButton(R.string.lbl_ok) { _, _ ->
									// User acknowledged - now start the VPN activation flow
									lifecycleScope.launch {
										startActivationFlow(server, serverUUID)
									}
								}
								.setNegativeButton(R.string.lbl_cancel) { _, _ ->
									isSwitching = false
									rebuild()
								}
								.setCancelable(false)
								.show()
						} else {
							// ### TAILSCALE DEACTIVATION FLOW ###
							lifecycleScope.launch {
								try {
									showProgressDialog()
									TailscaleManager.stopVpn()
									hideProgressDialog()

									// Ask for new address and validate BEFORE saving
									showAddressInputDialog(
										server = server,
										useTailscale = false,  // VPN wird deaktiviert
										onCancel = {
											// User cancelled address input
											isSwitching = false
											rebuild()
										},
										onAddressSet = { newAddress ->
											// Validation happens in showAddressInputDialog
											// Only called if validation succeeds
											startupViewModel.setServerAddress(serverUUID, newAddress)
											startupViewModel.setTailscaleEnabled(serverUUID, false)
											isSwitching = false
											showRestartDialog()
										})
								} catch (e: Exception) {
									hideProgressDialog()
									Toast.makeText(requireContext(), getString(R.string.tailscale_error_generic, e.message), Toast.LENGTH_LONG).show()
									isSwitching = false
									rebuild()
								}
							}
						}
					}
					default { false }
				}
			}
		}

		category {
			setTitle(R.string.lbl_server)

			action {
				setTitle(R.string.lbl_remove_server)
				setContent(R.string.lbl_remove_users)
				icon = R.drawable.ic_delete
				onActivate = {
					startupViewModel.deleteServer(serverUUID)

					parentFragmentManager.popBackStack()
				}
			}
		}
	}


	companion object {
		const val ARG_SERVER_UUID = "server_uuid"
	}
}