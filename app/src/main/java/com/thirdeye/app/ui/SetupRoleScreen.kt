package com.thirdeye.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thirdeye.app.language.AppTextKey
import com.thirdeye.app.language.nexusText
import com.thirdeye.app.setup.SetupRole

@Composable
fun SetupRoleScreen(
    onRoleSelected: (SetupRole) -> Unit
) {
    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Text(
                text = nexusText(AppTextKey.APP_NAME),
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = nexusText(AppTextKey.WELCOME),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = nexusText(AppTextKey.INITIAL_SETUP),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = nexusText(AppTextKey.SELECT_ROLE),
                style = MaterialTheme.typography.bodyLarge
            )

            Button(
                onClick = {
                    onRoleSelected(SetupRole.BLIND_USER)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = nexusText(AppTextKey.BLIND_USER)
                )
            }

            OutlinedButton(
                onClick = {
                    onRoleSelected(SetupRole.HELPER)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = nexusText(AppTextKey.HELPER)
                )
            }
        }
    }
}