package com.poc.safemode.ui.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.ui.theme.SafeModeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AreaEditorContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableListOf<String>()

    private var grantClicks = 0

    private fun setContent(state: AreaEditorUiState, hasPermission: Boolean = true) {
        composeRule.setContent {
            SafeModeTheme {
                AreaEditorContent(
                    uiState = state,
                    hasLocationPermission = hasPermission,
                    snackbarHostState = SnackbarHostState(),
                    onBackClick = {},
                    onGrantPermissionClick = { grantClicks++ },
                    onNameChange = {},
                    onUseConnectedWifiClick = {},
                    onSearchNetworksClick = {},
                    onNearbyNetworkSelected = { selected += it.ssid },
                    onNearbySsidsDismissed = {},
                    onLatitudeChange = {},
                    onLongitudeChange = {},
                    onRadiusChange = {},
                    onCaptureLocationClick = {},
                    onSaveClick = {}
                )
            }
        }
    }

    @Test
    fun emptyFormDisablesSaveAndShowsTheWifiHint() {
        setContent(AreaEditorUiState())

        composeRule.onNodeWithText("Nova área").assertExists()
        composeRule.onNodeWithText("Salvar Área de Confiança").assertIsNotEnabled()
        composeRule
            .onNodeWithText("Use a rede conectada ou busque as redes próximas.")
            .assertExists()
    }

    @Test
    fun invalidLatitudeShowsErrorAndDisablesSave() {
        setContent(
            AreaEditorUiState(
                name = "Casa",
                ssid = "CasaWifi",
                bssids = setOf("aa:00:00:00:00:01"),
                latitude = "91",
                longitude = "-46.6333"
            )
        )

        composeRule.onNodeWithText("Entre -90 e 90").assertExists()
        composeRule.onNodeWithText("Salvar Área de Confiança").assertIsNotEnabled()
    }

    @Test
    fun validFormEnablesSave() {
        setContent(
            AreaEditorUiState(
                isEditing = true,
                name = "Casa",
                ssid = "CasaWifi",
                bssids = setOf("aa:00:00:00:00:01", "aa:00:00:00:00:02"),
                latitude = "-23,5505",
                longitude = "-46.6333"
            )
        )

        composeRule.onNodeWithText("Editar área").assertExists()
        composeRule
            .onNodeWithText("Segurança desconhecida · 2 pontos de acesso reconhecidos")
            .assertExists()
        composeRule.onNodeWithText("Salvar Área de Confiança").assertIsEnabled()
    }

    @Test
    fun pickingANearbyNetworkReportsIt() {
        setContent(
            AreaEditorUiState(
                nearbyNetworks = listOf(
                    NearbyWifi("CasaWifi", setOf("aa:00:00:00:00:01")),
                    NearbyWifi("Vizinho", setOf("ee:00:00:00:00:99"))
                )
            )
        )

        composeRule.onNodeWithText("Redes próximas").assertExists()
        composeRule.onNodeWithText("Vizinho").performClick()

        assertEquals(listOf("Vizinho"), selected)
    }

    @Test
    fun withoutPermissionEachCardOffersToGrantItInsteadOfItsActions() {
        setContent(AreaEditorUiState(), hasPermission = false)

        composeRule.onAllNodesWithText("Permitir Localização").assertCountEquals(2)
        composeRule.onNodeWithText("Usar Wi-Fi Conectado").assertDoesNotExist()
        composeRule.onNodeWithText("Buscar Outras Redes").assertDoesNotExist()
        composeRule.onNodeWithText("Capturar Local Atual").assertDoesNotExist()

        composeRule.onAllNodesWithText("Permitir Localização")[0].performClick()
        assertEquals(1, grantClicks)
    }

    @Test
    fun withPermissionTheActionsAreShown() {
        setContent(AreaEditorUiState(), hasPermission = true)

        composeRule.onNodeWithText("Permitir Localização").assertDoesNotExist()
        composeRule.onNodeWithText("Usar Wi-Fi Conectado").assertExists()
        composeRule.onNodeWithText("Buscar Outras Redes").assertExists()
        composeRule.onNodeWithText("Capturar Local Atual").assertExists()
    }

    @Test
    fun networkWithoutRegisteredAccessPointShowsErrorAndDisablesSave() {
        setContent(
            AreaEditorUiState(
                isEditing = true,
                name = "Casa",
                ssid = "CasaWifi",
                latitude = "-23.5505",
                longitude = "-46.6333"
            )
        )

        composeRule.onNodeWithText("Ponto de acesso não registrado.", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Salvar Área de Confiança").assertIsNotEnabled()
    }

    @Test
    fun openNetworkShowsItsTypeAndAWarningButCanStillBeSaved() {
        setContent(
            AreaEditorUiState(
                name = "Cafe",
                ssid = "Cafe_Livre",
                bssids = setOf("c0:00:00:00:00:01"),
                security = WifiSecurity.Open,
                latitude = "-23.5505",
                longitude = "-46.6333"
            )
        )

        composeRule
            .onNodeWithText("Aberta, sem senha · 1 ponto de acesso reconhecido")
            .assertExists()
        composeRule.onNodeWithText("Esta rede é fácil de falsificar", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Salvar Área de Confiança").assertIsEnabled()
    }

    @Test
    fun protectedNetworkShowsNoWarning() {
        setContent(
            AreaEditorUiState(
                name = "Casa",
                ssid = "CasaWifi",
                bssids = setOf("aa:00:00:00:00:01"),
                security = WifiSecurity.Personal,
                protocols = setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3)
            )
        )

        composeRule
            .onNodeWithText("Protegida por senha (WPA2/WPA3) · 1 ponto de acesso reconhecido")
            .assertExists()
        composeRule.onNodeWithText("Esta rede é fácil de falsificar", substring = true)
            .assertDoesNotExist()
    }
}
