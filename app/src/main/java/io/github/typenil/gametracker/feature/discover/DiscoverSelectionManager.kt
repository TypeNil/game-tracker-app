package io.github.typenil.gametracker.feature.discover

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DiscoverSelectionManager(
    initialTab: DiscoverTab = DiscoverTab.FOR_YOU,
    initialRail: DiscoverRail = DiscoverRail.POPULAR_NOW,
) {
    private val _selectedTab = MutableStateFlow(initialTab)
    val selectedTab: StateFlow<DiscoverTab> = _selectedTab.asStateFlow()

    private val _selectedRail = MutableStateFlow(initialRail)
    val selectedRail: StateFlow<DiscoverRail> = _selectedRail.asStateFlow()

    fun selectTab(tab: DiscoverTab) {
        _selectedTab.value = tab
    }

    fun selectRail(rail: DiscoverRail, onRailSelected: (DiscoverRail) -> Unit = {}) {
        _selectedRail.value = rail
        onRailSelected(rail)
    }
}
