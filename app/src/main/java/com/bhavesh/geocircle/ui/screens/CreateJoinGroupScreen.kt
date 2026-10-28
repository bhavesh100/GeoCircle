package com.bhavesh.geocircle.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bhavesh.geocircle.ui.GeoCircleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJoinGroupScreen(
    initialTab: Int = 0,
    viewModel: GeoCircleViewModel,
    onBackClick: () -> Unit,
    onGroupSelected: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    var groupName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Create Group" else "Join Group") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Create Group") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Join Group") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                Text(
                    text = "Create a New Group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            val newGroup = viewModel.createGroup(groupName)
                            onGroupSelected(newGroup.id)
                        }
                    },
                    enabled = groupName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Group")
                }
            } else {
                Text(
                    text = "Join with Invite Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = {
                        inviteCode = it.uppercase()
                        joinError = null
                    },
                    label = { Text("Invite Code (e.g. GEO482)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = joinError != null,
                    supportingText = {
                        if (joinError != null) {
                            Text(joinError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val groupId = viewModel.joinGroup(inviteCode)
                        if (groupId != null) {
                            onGroupSelected(groupId)
                        } else {
                            joinError = "Please enter an invite code."
                        }
                    },
                    enabled = inviteCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Join Group")
                }
            }
        }
    }
}
