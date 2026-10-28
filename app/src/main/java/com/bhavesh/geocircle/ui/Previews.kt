package com.bhavesh.geocircle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.bhavesh.geocircle.ui.screens.CreateJoinGroupScreen
import com.bhavesh.geocircle.ui.screens.GroupDetailsScreen
import com.bhavesh.geocircle.ui.screens.HomeScreen
import com.bhavesh.geocircle.ui.theme.GeoCircleTheme

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun HomeScreenPreview() {
    val vm = remember { GeoCircleViewModel() }
    GeoCircleTheme {
        HomeScreen(
            viewModel = vm,
            onCreateGroupClick = {},
            onJoinGroupClick = {},
            onGroupClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun CreateJoinGroupScreenPreview() {
    val vm = remember { GeoCircleViewModel() }
    GeoCircleTheme {
        CreateJoinGroupScreen(
            initialTab = 0,
            viewModel = vm,
            onBackClick = {},
            onGroupSelected = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun GroupDetailsScreenPreview() {
    val vm = remember { GeoCircleViewModel() }
    GeoCircleTheme {
        GroupDetailsScreen(
            groupId = "g1",
            viewModel = vm,
            onBackClick = {}
        )
    }
}
