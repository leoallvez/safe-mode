package com.poc.safemode.ui.areas

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.ui.theme.SafeModeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AreasContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val home = TrustedArea("1", "Casa", "CasaWifi", -23.5505, -46.6333, 150, emptySet())
    private val office = TrustedArea("2", "Trabalho", "Corp", -23.5614, -46.6559, 250, emptySet())

    private val clicked = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    private fun setContent(state: AreasUiState) {
        composeRule.setContent {
            SafeModeTheme {
                AreasContent(
                    uiState = state,
                    snackbarHostState = SnackbarHostState(),
                    onNewAreaClick = {},
                    onAreaClick = { clicked += it },
                    onDeleteConfirmed = { deleted += it },
                    onTestStatusClick = {}
                )
            }
        }
    }

    @Test
    fun emptyListShowsTheHint() {
        setContent(AreasUiState(isLoading = false))

        composeRule
            .onNodeWithText("Nenhuma área de confiança cadastrada.", substring = true)
            .assertExists()
    }

    @Test
    fun tappingAnAreaOpensIt() {
        setContent(AreasUiState(areas = listOf(home, office), isLoading = false))

        composeRule.onNodeWithText("Trabalho").performClick()

        assertEquals(listOf("2"), clicked)
    }

    @Test
    fun deleteOnlyHappensAfterConfirmation() {
        setContent(AreasUiState(areas = listOf(home, office), isLoading = false))

        composeRule.onNodeWithContentDescription("Excluir Casa").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(emptyList<String>(), deleted)

        composeRule.onNodeWithContentDescription("Excluir Casa").performClick()
        composeRule.onNodeWithText("Excluir").performClick()
        assertEquals(listOf("1"), deleted)
    }
}
