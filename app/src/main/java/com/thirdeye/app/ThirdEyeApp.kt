package com.thirdeye.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.thirdeye.app.language.LocalNexusEyeLanguage
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.ui.theme.ThirdEyeTheme

@Composable
fun ThirdEyeApp(
    appLanguage: NexusEyeLanguage,
    content: @Composable () -> Unit
) {

    ThirdEyeTheme {

        CompositionLocalProvider(
            LocalNexusEyeLanguage provides
                    appLanguage
        ) {

            content()
        }
    }
}