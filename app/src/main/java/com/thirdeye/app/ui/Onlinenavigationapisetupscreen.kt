package com.thirdeye.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thirdeye.app.security.NexusEyeApiCredentialStore

@Composable
fun OnlineNavigationApiSetupScreen(
    onComplete: () -> Unit
) {

    val context = androidx.compose.ui.platform.LocalContext.current

    val credentialStore = remember {
        NexusEyeApiCredentialStore(context)
    }

    var apiKey by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "NEXUS EYE",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Map & Online Navigation Setup",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text =
                    "Enter your OpenRouteService API key to enable online route calculation. NEXUS EYE keeps the key in protected Android Keystore-backed storage. Offline navigation remains available without this key.",
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    errorMessage = ""
                },
                label = { Text("OpenRouteService API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                )
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val cleanKey = apiKey.trim()

                    if (cleanKey.isBlank()) {
                        errorMessage = "Enter the API key to continue setup."
                        return@Button
                    }

                    try {
                        credentialStore.saveOnlineNavigationApiKey(cleanKey)
                        onComplete()
                    } catch (exception: Exception) {
                        errorMessage =
                            exception.message
                                ?: "The navigation API key could not be saved."
                    }
                }
            ) {
                Text("SAVE MAP API KEY")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text =
                    "You can replace this key later from Settings → API Settings & Protected Credentials.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
