package com.thirdeye.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.language.LanguageManager
import com.thirdeye.app.language.LanguageState
import com.thirdeye.app.language.NexusEyeLanguages
import com.thirdeye.app.language.NexusEyeLocalization
import com.thirdeye.app.security.NexusEyeSecureKeyStore
import com.thirdeye.app.vision.FaceProfileStore
import com.thirdeye.app.vision.FaceRecognitionEngine
import com.thirdeye.app.vision.ImageRecognitionService
import com.thirdeye.app.vision.VisionPhoto
import com.thirdeye.app.vision.VisionPhotoLibrary
import com.thirdeye.app.vision.VisionPhotoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun VisionScreen(
    context: Context,
    onBack: () -> Unit
) {

    val appContext =
        context.applicationContext

    val scope =
        rememberCoroutineScope()

    val photoLibrary =
        remember {
            VisionPhotoLibrary(
                appContext
            )
        }

    val profileStore =
        remember {
            FaceProfileStore(
                appContext
            )
        }

    val languageManager =
        remember {
            LanguageManager(
                appContext
            )
        }

    val ttsManager =
        remember {
            NexusEyeTtsManager(
                appContext
            )
        }

    val secureKeyStore =
        remember {
            NexusEyeSecureKeyStore(
                appContext
            )
        }

    val imageRecognitionService =
        remember {
            ImageRecognitionService(
                appContext
            )
        }

    val languageState by
    languageManager
        .languageState
        .collectAsState(
            initial =
                LanguageState(
                    appLanguage =
                        NexusEyeLanguages.English,
                    speechLanguage =
                        NexusEyeLanguages.English
                )
        )

    var faceRecognitionEngine by
    remember {
        mutableStateOf(
            null as FaceRecognitionEngine?
        )
    }

    var photos by
    remember {
        mutableStateOf(
            photoLibrary.listPhotos()
        )
    }

    var statusText by
    remember {
        mutableStateOf("")
    }

    var cameraUri by
    remember {
        mutableStateOf<Uri?>(null)
    }

    var aiProcessingPhotoId by
    remember {
        mutableStateOf<String?>(null)
    }

    var aiResultPhotoId by
    remember {
        mutableStateOf<String?>(null)
    }

    var aiResultText by
    remember {
        mutableStateOf("")
    }

    var aiResultLanguage by
    remember {
        mutableStateOf("")
    }

    var personProcessingPhotoId by
    remember {
        mutableStateOf<String?>(null)
    }

    var personResultPhotoId by
    remember {
        mutableStateOf<String?>(null)
    }

    var personResultText by
    remember {
        mutableStateOf("")
    }

    var personSavePhotoId by
    remember {
        mutableStateOf<String?>(null)
    }

    var personName by
    remember {
        mutableStateOf("")
    }

    /*
     * =========================================================
     * LANGUAGE
     * =========================================================
     */

    LaunchedEffect(
        languageState.speechLanguage
    ) {

        try {

            ttsManager.setLanguage(
                languageState.speechLanguage
            )

        } catch (_: Exception) {
        }
    }

    /*
     * =========================================================
     * SPEAK
     * =========================================================
     */

    fun speak(
        text: String
    ) {

        if (
            text.isBlank()
        ) {
            return
        }

        try {

            ttsManager.speakOnPhoneFallback(
                text =
                    text,
                language =
                    languageState.speechLanguage,
                onError = {
                        error ->
                    statusText =
                        error
                }
            )

        } catch (
            exception: Exception
        ) {

            statusText =
                exception.message
                    ?: text
        }
    }

    /*
     * =========================================================
     * REFRESH
     * =========================================================
     */

    fun refreshPhotos() {

        photos =
            photoLibrary.listPhotos()
    }

    /*
     * =========================================================
     * FACE ENGINE
     * =========================================================
     */

    fun getFaceEngine():
            FaceRecognitionEngine {

        val existing =
            faceRecognitionEngine

        if (
            existing != null
        ) {
            return existing
        }

        val created =
            FaceRecognitionEngine(
                appContext
            )

        faceRecognitionEngine =
            created

        return created
    }

    /*
     * =========================================================
     * GEMINI IMAGE RECOGNITION
     * =========================================================
     */

    fun describeImage(
        photo: VisionPhoto
    ) {

        if (
            aiProcessingPhotoId != null ||
            personProcessingPhotoId != null ||
            personSavePhotoId != null
        ) {
            return
        }

        if (
            !secureKeyStore
                .hasGeminiApiKey()
        ) {

            val message =
                visionText(
                    "ai_missing_key"
                )

            statusText =
                message

            speak(
                message
            )

            return
        }

        scope.launch {

            aiProcessingPhotoId =
                photo.id

            aiResultPhotoId =
                null

            aiResultText =
                ""

            aiResultLanguage =
                ""

            statusText =
                visionText(
                    "ai_analyzing"
                )

            try {

                val imageBytes =
                    withContext(
                        Dispatchers.IO
                    ) {

                        photo.file
                            .readBytes()
                    }

                if (
                    imageBytes.isEmpty()
                ) {

                    val message =
                        visionText(
                            "ai_image_empty"
                        )

                    statusText =
                        message

                    speak(
                        message
                    )

                    return@launch
                }

                val mimeType =
                    detectMimeType(
                        photo.file
                    )

                val speechLanguage =
                    languageState
                        .speechLanguage
                        .displayName

                val result =
                    imageRecognitionService
                        .recognizeImage(
                            imageBytes =
                                imageBytes,
                            mimeType =
                                mimeType,
                            language =
                                speechLanguage
                        )

                result
                    .onSuccess {
                            recognition ->

                        aiResultPhotoId =
                            photo.id

                        aiResultText =
                            recognition.answer

                        aiResultLanguage =
                            recognition.language

                        statusText =
                            visionText(
                                "ai_completed"
                            )

                        speak(
                            recognition.answer
                        )
                    }
                    .onFailure {
                            exception ->

                        val message =
                            exception
                                .message
                                ?.trim()
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: visionText(
                                    "ai_failed"
                                )

                        statusText =
                            message

                        speak(
                            message
                        )
                    }

            } catch (
                exception: Exception
            ) {

                val message =
                    exception
                        .message
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: visionText(
                            "ai_failed"
                        )

                statusText =
                    message

                speak(
                    message
                )

            } finally {

                aiProcessingPhotoId =
                    null
            }
        }
    }

    /*
     * =========================================================
     * PERSON IDENTIFICATION
     * =========================================================
     */

    fun identifyPerson(
        photo: VisionPhoto
    ) {

        if (
            aiProcessingPhotoId != null ||
            personProcessingPhotoId != null ||
            personSavePhotoId != null
        ) {
            return
        }

        scope.launch {

            personProcessingPhotoId =
                photo.id

            personResultPhotoId =
                null

            personResultText =
                ""

            statusText =
                visionText(
                    "person_analyzing"
                )

            try {

                val embeddings =
                    withContext(
                        Dispatchers.Default
                    ) {

                        getFaceEngine()
                            .analyzeFile(
                                photo.file
                            )
                    }

                if (
                    embeddings.isEmpty()
                ) {

                    val message =
                        "Stranger person ahead."

                    personResultPhotoId =
                        photo.id

                    personResultText =
                        message

                    statusText =
                        visionText(
                            "person_no_face"
                        )

                    speak(
                        message
                    )

                    return@launch
                }

                val recognizedNames =
                    ArrayList<String>()

                for (
                face in
                embeddings
                ) {

                    val match =
                        profileStore
                            .findBestMatch(
                                embedding =
                                    face.embedding
                            )

                    if (
                        match != null
                    ) {

                        if (
                            recognizedNames
                                .none {
                                    it.equals(
                                        match.name,
                                        ignoreCase =
                                            true
                                    )
                                }
                        ) {

                            recognizedNames +=
                                match.name
                        }
                    }
                }

                val resultText =
                    if (
                        recognizedNames
                            .isEmpty()
                    ) {

                        "Stranger person ahead."

                    } else {

                        personNamesSpeech(
                            recognizedNames
                        )
                    }

                personResultPhotoId =
                    photo.id

                personResultText =
                    resultText

                statusText =
                    visionText(
                        "person_completed"
                    )

                speak(
                    resultText
                )

            } catch (
                exception: Exception
            ) {

                val message =
                    exception
                        .message
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: visionText(
                            "person_failed"
                        )

                personResultPhotoId =
                    photo.id

                personResultText =
                    message

                statusText =
                    message

                speak(
                    message
                )

            } finally {

                personProcessingPhotoId =
                    null
            }
        }
    }

    /*
     * =========================================================
     * SAVE PERSON PROFILE
     * =========================================================
     */

    fun savePersonProfile(
        photo: VisionPhoto
    ) {

        if (
            aiProcessingPhotoId != null ||
            personProcessingPhotoId != null
        ) {
            return
        }

        val cleanName =
            personName.trim()

        if (
            cleanName.isBlank()
        ) {

            statusText =
                visionText(
                    "person_name_required"
                )

            speak(
                visionText(
                    "person_name_required"
                )
            )

            return
        }

        scope.launch {

            personSavePhotoId =
                photo.id

            statusText =
                visionText(
                    "person_saving"
                )

            try {

                val embeddings =
                    withContext(
                        Dispatchers.Default
                    ) {

                        getFaceEngine()
                            .analyzeFile(
                                photo.file
                            )
                    }

                when {

                    embeddings.isEmpty() -> {

                        val message =
                            visionText(
                                "person_no_face_save"
                            )

                        statusText =
                            message

                        speak(
                            message
                        )
                    }

                    embeddings.size > 1 -> {

                        val message =
                            visionText(
                                "person_multiple_faces"
                            )

                        statusText =
                            message

                        speak(
                            message
                        )
                    }

                    else -> {

                        val embedding =
                            embeddings
                                .first()
                                .embedding

                        profileStore
                            .saveProfile(
                                name =
                                    cleanName,
                                embedding =
                                    embedding
                            )

                        personName =
                            ""

                        statusText =
                            visionText(
                                "person_saved"
                            )
                                .replace(
                                    "{name}",
                                    cleanName
                                )

                        speak(
                            visionText(
                                "person_saved_speech"
                            )
                                .replace(
                                    "{name}",
                                    cleanName
                                )
                        )
                    }
                }

            } catch (
                exception: Exception
            ) {

                val message =
                    exception
                        .message
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: visionText(
                            "person_save_failed"
                        )

                statusText =
                    message

                speak(
                    message
                )

            } finally {

                personSavePhotoId =
                    null
            }
        }
    }

    /*
     * =========================================================
     * CAMERA
     * =========================================================
     */

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->

            val uri =
                cameraUri

            if (
                success &&
                uri != null
            ) {

                scope.launch {

                    try {

                        statusText =
                            visionText(
                                "saving"
                            )

                        photoLibrary.saveFromUri(
                            uri =
                                uri,
                            source =
                                VisionPhotoSource
                                    .PHONE_CAMERA
                        )

                        refreshPhotos()

                        statusText =
                            visionText(
                                "photo_saved"
                            )

                        speak(
                            visionText(
                                "photo_saved_speech"
                            )
                        )

                    } catch (
                        exception: Exception
                    ) {

                        val message =
                            exception.message
                                ?: visionText(
                                    "save_failed"
                                )

                        statusText =
                            message

                        speak(
                            message
                        )

                    } finally {

                        cameraUri =
                            null
                    }
                }

            } else {

                statusText =
                    visionText(
                        "camera_cancelled"
                    )

                cameraUri =
                    null
            }
        }

    /*
     * =========================================================
     * GALLERY
     * =========================================================
     */

    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (
                uri != null
            ) {

                scope.launch {

                    try {

                        statusText =
                            visionText(
                                "importing"
                            )

                        photoLibrary.saveFromUri(
                            uri =
                                uri,
                            source =
                                VisionPhotoSource
                                    .GALLERY
                        )

                        refreshPhotos()

                        statusText =
                            visionText(
                                "photo_imported"
                            )

                        speak(
                            visionText(
                                "photo_imported_speech"
                            )
                        )

                    } catch (
                        exception: Exception
                    ) {

                        val message =
                            exception.message
                                ?: visionText(
                                    "import_failed"
                                )

                        statusText =
                            message

                        speak(
                            message
                        )
                    }
                }

            } else {

                statusText =
                    visionText(
                        "gallery_cancelled"
                    )
            }
        }

    /*
     * =========================================================
     * CLEANUP
     * =========================================================
     */

    DisposableEffect(Unit) {

        onDispose {

            cameraUri =
                null

            try {
                faceRecognitionEngine
                    ?.close()
            } catch (_: Exception) {
            }

            try {
                ttsManager.stop()
            } catch (_: Exception) {
            }

            try {
                ttsManager.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    /*
     * =========================================================
     * MAIN UI
     * =========================================================
     */

    Scaffold { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        innerPadding
                    )
                    .padding(
                        20.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            Text(
                text =
                    visionText(
                        "title"
                    ),

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Text(
                text =
                    visionText(
                        "description"
                    ),

                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

            /*
             * =================================================
             * PERSON PROFILE NAME
             * =================================================
             */

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(
                            14.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Text(
                        text =
                            visionText(
                                "person_profile_title"
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            visionText(
                                "person_profile_description"
                            )
                    )

                    OutlinedTextField(
                        value =
                            personName,

                        onValueChange = {
                            personName =
                                it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine =
                            true,

                        label = {
                            Text(
                                text =
                                    visionText(
                                        "person_name_label"
                                    )
                            )
                        },

                        placeholder = {
                            Text(
                                text =
                                    visionText(
                                        "person_name_placeholder"
                                    )
                            )
                        },

                        keyboardOptions =
                            KeyboardOptions(
                                imeAction =
                                    ImeAction.Done
                            )
                    )
                }
            }

            /*
             * =================================================
             * CAMERA
             * =================================================
             */

            Button(
                onClick = {

                    if (
                        aiProcessingPhotoId != null ||
                        personProcessingPhotoId != null ||
                        personSavePhotoId != null
                    ) {
                        return@Button
                    }

                    try {

                        val captureDirectory =
                            File(
                                appContext.cacheDir,
                                "vision_capture"
                            )

                        if (
                            !captureDirectory
                                .exists()
                        ) {

                            captureDirectory.mkdirs()
                        }

                        val imageFile =
                            File.createTempFile(
                                "vision_capture_",
                                ".jpg",
                                captureDirectory
                            )

                        val uri =
                            FileProvider
                                .getUriForFile(
                                    appContext,
                                    "${appContext.packageName}.fileprovider",
                                    imageFile
                                )

                        cameraUri =
                            uri

                        cameraLauncher.launch(
                            uri
                        )

                    } catch (
                        exception: Exception
                    ) {

                        val message =
                            exception.message
                                ?: visionText(
                                    "camera_start_failed"
                                )

                        statusText =
                            message

                        speak(
                            message
                        )
                    }
                },

                enabled =
                    aiProcessingPhotoId == null &&
                            personProcessingPhotoId == null &&
                            personSavePhotoId == null,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        visionText(
                            "camera"
                        )
                )
            }

            /*
             * =================================================
             * GALLERY
             * =================================================
             */

            OutlinedButton(
                onClick = {

                    if (
                        aiProcessingPhotoId != null ||
                        personProcessingPhotoId != null ||
                        personSavePhotoId != null
                    ) {
                        return@OutlinedButton
                    }

                    galleryLauncher.launch(
                        "image/*"
                    )
                },

                enabled =
                    aiProcessingPhotoId == null &&
                            personProcessingPhotoId == null &&
                            personSavePhotoId == null,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        visionText(
                            "gallery"
                        )
                )
            }

            /*
             * =================================================
             * REFRESH
             * =================================================
             */

            OutlinedButton(
                onClick = {

                    refreshPhotos()

                    statusText =
                        visionText(
                            "refreshed"
                        )
                },

                enabled =
                    aiProcessingPhotoId == null &&
                            personProcessingPhotoId == null &&
                            personSavePhotoId == null,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        visionText(
                            "refresh"
                        )
                )
            }

            /*
             * =================================================
             * STATUS
             * =================================================
             */

            if (
                statusText.isNotBlank()
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            statusText,

                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    )
                }
            }

            /*
             * =================================================
             * LIBRARY COUNT
             * =================================================
             */

            Text(
                text =
                    visionText(
                        "library_count"
                    )
                        .replace(
                            "{count}",
                            photos.size.toString()
                        ),

                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            if (
                photos.isEmpty()
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            visionText(
                                "empty_library"
                            ),

                        modifier =
                            Modifier.padding(
                                16.dp
                            )
                    )
                }

            } else {

                LazyColumn(
                    modifier =
                        Modifier
                            .weight(
                                1f
                            ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {

                    items(
                        items =
                            photos,

                        key = {
                            it.id
                        }

                    ) { photo ->

                        VisionPhotoCard(
                            photo =
                                photo,

                            enabled =
                                aiProcessingPhotoId == null &&
                                        personProcessingPhotoId == null &&
                                        personSavePhotoId == null,

                            isAiProcessing =
                                aiProcessingPhotoId ==
                                        photo.id,

                            aiResult =
                                if (
                                    aiResultPhotoId ==
                                    photo.id
                                ) {
                                    aiResultText
                                } else {
                                    ""
                                },

                            aiLanguage =
                                if (
                                    aiResultPhotoId ==
                                    photo.id
                                ) {
                                    aiResultLanguage
                                } else {
                                    ""
                                },

                            isPersonProcessing =
                                personProcessingPhotoId ==
                                        photo.id,

                            personResult =
                                if (
                                    personResultPhotoId ==
                                    photo.id
                                ) {
                                    personResultText
                                } else {
                                    ""
                                },

                            isPersonSaving =
                                personSavePhotoId ==
                                        photo.id,

                            onDescribe =
                                {
                                    describeImage(
                                        photo
                                    )
                                },

                            onIdentifyPerson =
                                {
                                    identifyPerson(
                                        photo
                                    )
                                },

                            onSavePerson =
                                {
                                    savePersonProfile(
                                        photo
                                    )
                                },

                            onSpeakAiResult =
                                {
                                    if (
                                        aiResultPhotoId ==
                                        photo.id &&
                                        aiResultText
                                            .isNotBlank()
                                    ) {

                                        speak(
                                            aiResultText
                                        )
                                    }
                                },

                            onSpeakPersonResult =
                                {
                                    if (
                                        personResultPhotoId ==
                                        photo.id &&
                                        personResultText
                                            .isNotBlank()
                                    ) {

                                        speak(
                                            personResultText
                                        )
                                    }
                                },

                            onDelete =
                                {

                                    if (
                                        photoLibrary
                                            .deletePhoto(
                                                photo
                                            )
                                    ) {

                                        photos =
                                            photoLibrary
                                                .listPhotos()

                                        if (
                                            aiResultPhotoId ==
                                            photo.id
                                        ) {

                                            aiResultPhotoId =
                                                null

                                            aiResultText =
                                                ""

                                            aiResultLanguage =
                                                ""
                                        }

                                        if (
                                            personResultPhotoId ==
                                            photo.id
                                        ) {

                                            personResultPhotoId =
                                                null

                                            personResultText =
                                                ""
                                        }

                                        statusText =
                                            visionText(
                                                "deleted"
                                            )

                                    } else {

                                        statusText =
                                            visionText(
                                                "delete_failed"
                                            )
                                    }
                                }
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        2.dp
                    )
            )

            OutlinedButton(
                onClick = {

                    try {
                        ttsManager.stop()
                    } catch (_: Exception) {
                    }

                    onBack()
                },

                enabled =
                    aiProcessingPhotoId == null &&
                            personProcessingPhotoId == null &&
                            personSavePhotoId == null,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        visionText(
                            "back"
                        )
                )
            }
        }
    }
}

/*
 * =============================================================
 * PHOTO CARD
 * =============================================================
 */

@Composable
private fun VisionPhotoCard(
    photo: VisionPhoto,
    enabled: Boolean,
    isAiProcessing: Boolean,
    aiResult: String,
    aiLanguage: String,
    isPersonProcessing: Boolean,
    personResult: String,
    isPersonSaving: Boolean,
    onDescribe: () -> Unit,
    onIdentifyPerson: () -> Unit,
    onSavePerson: () -> Unit,
    onSpeakAiResult: () -> Unit,
    onSpeakPersonResult: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(
                    14.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                VisionPhotoThumbnail(
                    photo =
                        photo
                )

                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            4.dp
                        )
                ) {

                    Text(
                        text =
                            photoSourceText(
                                photo.source
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            "${photo.metadata.width} × ${photo.metadata.height}"
                    )

                    Text(
                        text =
                            formatFileSize(
                                photo.fileSizeBytes
                            )
                    )
                }
            }

            Text(
                text =
                    metadataText(
                        photo
                    ),

                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )

            /*
             * =================================================
             * GEMINI
             * =================================================
             */

            Button(
                onClick =
                    onDescribe,

                enabled =
                    enabled,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (
                            isAiProcessing
                        ) {
                            visionText(
                                "ai_analyzing_button"
                            )
                        } else {
                            visionText(
                                "describe_image"
                            )
                        }
                )
            }

            if (
                aiResult.isNotBlank()
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            ),

                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {

                        Text(
                            text =
                                visionText(
                                    "ai_result"
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Text(
                            text =
                                aiResult
                        )

                        if (
                            aiLanguage
                                .isNotBlank()
                        ) {

                            Text(
                                text =
                                    visionText(
                                        "ai_language"
                                    )
                                        .replace(
                                            "{language}",
                                            aiLanguage
                                        ),

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                        OutlinedButton(
                            onClick =
                                onSpeakAiResult,

                            enabled =
                                enabled,

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    visionText(
                                        "speak_result_again"
                                    )
                            )
                        }
                    }
                }
            }

            /*
             * =================================================
             * PERSON
             * =================================================
             */

            OutlinedButton(
                onClick =
                    onIdentifyPerson,

                enabled =
                    enabled,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (
                            isPersonProcessing
                        ) {
                            visionText(
                                "person_analyzing_button"
                            )
                        } else {
                            visionText(
                                "identify_person"
                            )
                        }
                )
            }

            Button(
                onClick =
                    onSavePerson,

                enabled =
                    enabled,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (
                            isPersonSaving
                        ) {
                            visionText(
                                "person_saving_button"
                            )
                        } else {
                            visionText(
                                "save_person_profile"
                            )
                        }
                )
            }

            if (
                personResult.isNotBlank()
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            ),

                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {

                        Text(
                            text =
                                visionText(
                                    "person_result"
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Text(
                            text =
                                personResult
                        )

                        OutlinedButton(
                            onClick =
                                onSpeakPersonResult,

                            enabled =
                                enabled,

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    visionText(
                                        "speak_person_again"
                                    )
                            )
                        }
                    }
                }
            }

            /*
             * =================================================
             * DELETE
             * =================================================
             */

            OutlinedButton(
                onClick =
                    onDelete,

                enabled =
                    enabled,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        visionText(
                            "delete"
                        )
                )
            }
        }
    }
}

/*
 * =============================================================
 * THUMBNAIL
 * =============================================================
 */

@Composable
private fun VisionPhotoThumbnail(
    photo: VisionPhoto
) {

    val bitmap by
    produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 =
            photo.file.absolutePath
    ) {

        value =
            withContext(
                Dispatchers.IO
            ) {

                decodeThumbnail(
                    photo.file
                )
            }
    }

    if (
        bitmap != null
    ) {

        Image(
            bitmap =
                bitmap!!
                    .asImageBitmap(),

            contentDescription =
                null,

            modifier =
                Modifier.size(
                    90.dp
                ),

            contentScale =
                ContentScale.Crop
        )

    } else {

        Card(
            modifier =
                Modifier.size(
                    90.dp
                )
        ) {

            Text(
                text =
                    visionText(
                        "no_preview"
                    ),

                modifier =
                    Modifier.padding(
                        10.dp
                    )
            )
        }
    }
}

/*
 * =============================================================
 * THUMBNAIL DECODING
 * =============================================================
 */

private fun decodeThumbnail(
    file: File
): android.graphics.Bitmap? {

    return try {

        val options =
            android.graphics.BitmapFactory
                .Options()
                .apply {

                    inSampleSize =
                        4

                    inPreferredConfig =
                        android.graphics.Bitmap.Config
                            .RGB_565
                }

        android.graphics.BitmapFactory
            .decodeFile(
                file.absolutePath,
                options
            )

    } catch (_: Exception) {

        null
    }
}

/*
 * =============================================================
 * MIME TYPE
 * =============================================================
 */

private fun detectMimeType(
    file: File
): String {

    return when (
        file.extension
            .lowercase(
                Locale.US
            )
    ) {

        "png" ->
            "image/png"

        "webp" ->
            "image/webp"

        "heic" ->
            "image/heic"

        "heif" ->
            "image/heif"

        "jpg",
        "jpeg" ->
            "image/jpeg"

        else ->
            "image/jpeg"
    }
}

/*
 * =============================================================
 * PERSON SPEECH
 * =============================================================
 */

private fun personNamesSpeech(
    names: List<String>
): String {

    return when (
        names.size
    ) {

        0 ->
            "Stranger person ahead."

        1 ->
            "${names.first()} is ahead."

        2 ->
            "${names[0]} and ${names[1]} are ahead."

        else -> {

            val beginning =
                names
                    .dropLast(1)
                    .joinToString(
                        separator = ", "
                    )

            "$beginning, and ${names.last()} are ahead."
        }
    }
}

/*
 * =============================================================
 * METADATA
 * =============================================================
 */

private fun metadataText(
    photo: VisionPhoto
): String {

    val metadata =
        photo.metadata

    val languageId =
        NexusEyeLocalization
            .currentAppLanguage
            .id

    val parts =
        mutableListOf<String>()

    if (
        !metadata.dateTaken
            .isNullOrBlank()
    ) {

        parts +=
            if (
                languageId == "hi"
            ) {

                "तारीख: ${metadata.dateTaken}"

            } else {

                "Date: ${metadata.dateTaken}"
            }
    }

    parts +=
        if (
            languageId == "hi"
        ) {

            "दिशा: ${metadata.orientation}"

        } else {

            "Orientation: ${metadata.orientation}"
        }

    if (
        !metadata.cameraMake
            .isNullOrBlank()
    ) {

        parts +=
            if (
                languageId == "hi"
            ) {

                "कैमरा: ${metadata.cameraMake}"

            } else {

                "Camera: ${metadata.cameraMake}"
            }
    }

    if (
        !metadata.cameraModel
            .isNullOrBlank()
    ) {

        parts +=
            if (
                languageId == "hi"
            ) {

                "मॉडल: ${metadata.cameraModel}"

            } else {

                "Model: ${metadata.cameraModel}"
            }
    }

    if (
        metadata.latitude != null &&
        metadata.longitude != null
    ) {

        parts +=
            if (
                languageId == "hi"
            ) {

                "स्थान उपलब्ध है।"

            } else {

                "Location metadata available."
            }
    }

    return parts.joinToString(
        separator = " • "
    )
}

/*
 * =============================================================
 * FILE SIZE
 * =============================================================
 */

private fun formatFileSize(
    bytes: Long
): String {

    if (
        bytes < 1024
    ) {

        return "$bytes B"
    }

    if (
        bytes < 1024 * 1024
    ) {

        return "${bytes / 1024} KB"
    }

    val megabytes =
        bytes.toDouble() /
                (1024.0 * 1024.0)

    return String.format(
        Locale.US,
        "%.1f MB",
        megabytes
    )
}

/*
 * =============================================================
 * PHOTO SOURCE
 * =============================================================
 */

private fun photoSourceText(
    source: VisionPhotoSource
): String {

    return when (
        source
    ) {

        VisionPhotoSource.PHONE_CAMERA ->
            visionText(
                "source_camera"
            )

        VisionPhotoSource.GALLERY ->
            visionText(
                "source_gallery"
            )

        VisionPhotoSource.OV7670 ->
            visionText(
                "source_ov7670"
            )
    }
}

/*
 * =============================================================
 * LOCALIZATION
 * =============================================================
 */

private fun visionText(
    key: String
): String {

    val languageId =
        NexusEyeLocalization
            .currentAppLanguage
            .id

    return when (
        languageId
    ) {

        "hi" -> {

            when (
                key
            ) {

                "title" ->
                    "NEXUS EYE विज़न"

                "description" ->
                    "फोटो लें या गैलरी से फोटो चुनें। AI विज़न और स्थानीय व्यक्ति पहचान उपलब्ध है।"

                "camera" ->
                    "कैमरा से फोटो लें"

                "gallery" ->
                    "गैलरी से फोटो चुनें"

                "refresh" ->
                    "लाइब्रेरी रिफ्रेश करें"

                "back" ->
                    "वापस"

                "delete" ->
                    "फोटो हटाएँ"

                "no_preview" ->
                    "प्रिव्यू नहीं"

                "empty_library" ->
                    "फोटो लाइब्रेरी अभी खाली है।"

                "photo_saved" ->
                    "फोटो लाइब्रेरी में सुरक्षित है।"

                "photo_saved_speech" ->
                    "फोटो सुरक्षित हो गई।"

                "photo_imported" ->
                    "फोटो लाइब्रेरी में आयात की गई।"

                "photo_imported_speech" ->
                    "फोटो आयात हो गई।"

                "saving" ->
                    "फोटो सुरक्षित की जा रही है..."

                "importing" ->
                    "फोटो आयात की जा रही है..."

                "camera_cancelled" ->
                    "कैमरा बंद कर दिया गया।"

                "gallery_cancelled" ->
                    "गैलरी चयन रद्द किया गया।"

                "camera_start_failed" ->
                    "कैमरा शुरू नहीं हो सका।"

                "save_failed" ->
                    "फोटो सुरक्षित नहीं की जा सकी।"

                "import_failed" ->
                    "फोटो आयात नहीं की जा सकी।"

                "refreshed" ->
                    "लाइब्रेरी अपडेट हो गई।"

                "deleted" ->
                    "फोटो हटा दी गई।"

                "delete_failed" ->
                    "फोटो हटाई नहीं जा सकी।"

                "source_camera" ->
                    "फोन कैमरा"

                "source_gallery" ->
                    "गैलरी"

                "source_ov7670" ->
                    "OV7670"

                "library_count" ->
                    "कुल फोटो: {count}"

                "ai_missing_key" ->
                    "Gemini API key सेट नहीं है।"

                "ai_image_empty" ->
                    "फोटो खाली है या पढ़ी नहीं जा सकी।"

                "ai_analyzing" ->
                    "फोटो का AI विश्लेषण किया जा रहा है..."

                "ai_analyzing_button" ->
                    "फोटो का विश्लेषण हो रहा है..."

                "ai_completed" ->
                    "फोटो का AI विश्लेषण पूरा हो गया।"

                "ai_failed" ->
                    "फोटो का AI विश्लेषण नहीं हो सका।"

                "describe_image" ->
                    "फोटो का वर्णन करें"

                "ai_result" ->
                    "AI द्वारा दिया गया वर्णन"

                "ai_language" ->
                    "भाषा: {language}"

                "speak_result_again" ->
                    "वर्णन फिर से सुनाएँ"

                "person_profile_title" ->
                    "स्थानीय व्यक्ति पहचान"

                "person_profile_description" ->
                    "किसी व्यक्ति का नाम सेव करें। प्रोफ़ाइल स्थानीय डिवाइस पर रखी जाएगी।"

                "person_name_label" ->
                    "व्यक्ति का नाम"

                "person_name_placeholder" ->
                    "जैसे: राहुल"

                "person_name_required" ->
                    "पहले व्यक्ति का नाम दर्ज करें।"

                "person_analyzing" ->
                    "व्यक्ति की पहचान की जा रही है..."

                "person_analyzing_button" ->
                    "पहचान हो रही है..."

                "person_completed" ->
                    "व्यक्ति पहचान पूरी हुई।"

                "person_failed" ->
                    "व्यक्ति पहचान नहीं हो सकी।"

                "identify_person" ->
                    "व्यक्ति की पहचान करें"

                "save_person_profile" ->
                    "व्यक्ति प्रोफ़ाइल सेव करें"

                "person_saving_button" ->
                    "प्रोफ़ाइल सेव हो रही है..."

                "person_saving" ->
                    "व्यक्ति प्रोफ़ाइल सेव की जा रही है..."

                "person_no_face" ->
                    "चेहरा नहीं मिला।"

                "person_no_face_save" ->
                    "इस फोटो में कोई चेहरा नहीं मिला।"

                "person_multiple_faces" ->
                    "प्रोफ़ाइल सेव करने के लिए फोटो में केवल एक चेहरा होना चाहिए।"

                "person_saved" ->
                    "{name} की प्रोफ़ाइल सेव हो गई।"

                "person_saved_speech" ->
                    "{name} की प्रोफ़ाइल सेव हो गई।"

                "person_save_failed" ->
                    "व्यक्ति प्रोफ़ाइल सेव नहीं हो सकी।"

                "person_result" ->
                    "व्यक्ति पहचान परिणाम"

                "speak_person_again" ->
                    "पहचान फिर से सुनाएँ"

                else ->
                    ""
            }

        }

        else -> {

            when (
                key
            ) {

                "title" ->
                    "NEXUS EYE Vision"

                "description" ->
                    "Capture a photo or select one from the gallery. AI Vision and local person recognition are available."

                "camera" ->
                    "Capture with Camera"

                "gallery" ->
                    "Import from Gallery"

                "refresh" ->
                    "Refresh Library"

                "back" ->
                    "Back"

                "delete" ->
                    "Delete Photo"

                "no_preview" ->
                    "No preview"

                "empty_library" ->
                    "The photo library is empty."

                "photo_saved" ->
                    "Photo saved to the library."

                "photo_saved_speech" ->
                    "Photo saved."

                "photo_imported" ->
                    "Photo imported into the library."

                "photo_imported_speech" ->
                    "Photo imported."

                "saving" ->
                    "Saving photo..."

                "importing" ->
                    "Importing photo..."

                "camera_cancelled" ->
                    "Camera was cancelled."

                "gallery_cancelled" ->
                    "Gallery selection was cancelled."

                "camera_start_failed" ->
                    "Could not start the camera."

                "save_failed" ->
                    "Could not save the photo."

                "import_failed" ->
                    "Could not import the photo."

                "refreshed" ->
                    "Library refreshed."

                "deleted" ->
                    "Photo deleted."

                "delete_failed" ->
                    "Could not delete the photo."

                "source_camera" ->
                    "Phone Camera"

                "source_gallery" ->
                    "Gallery"

                "source_ov7670" ->
                    "OV7670"

                "library_count" ->
                    "Total photos: {count}"

                "ai_missing_key" ->
                    "Gemini API key is not configured."

                "ai_image_empty" ->
                    "The image is empty or could not be read."

                "ai_analyzing" ->
                    "Analyzing the image with AI..."

                "ai_analyzing_button" ->
                    "Analyzing Image..."

                "ai_completed" ->
                    "AI image analysis completed."

                "ai_failed" ->
                    "Image AI analysis failed."

                "describe_image" ->
                    "Describe Image"

                "ai_result" ->
                    "AI Description"

                "ai_language" ->
                    "Language: {language}"

                "speak_result_again" ->
                    "Speak Description Again"

                "person_profile_title" ->
                    "Local Person Recognition"

                "person_profile_description" ->
                    "Save a person's name. The profile is stored locally on this device."

                "person_name_label" ->
                    "Person name"

                "person_name_placeholder" ->
                    "For example: Rahul"

                "person_name_required" ->
                    "Enter the person's name first."

                "person_analyzing" ->
                    "Identifying the person..."

                "person_analyzing_button" ->
                    "Identifying..."

                "person_completed" ->
                    "Person identification completed."

                "person_failed" ->
                    "Person identification failed."

                "identify_person" ->
                    "Identify Person"

                "save_person_profile" ->
                    "Save Person Profile"

                "person_saving_button" ->
                    "Saving Profile..."

                "person_saving" ->
                    "Saving person profile..."

                "person_no_face" ->
                    "No face was found."

                "person_no_face_save" ->
                    "No face was found in this photo."

                "person_multiple_faces" ->
                    "A profile can be saved only when the photo contains one face."

                "person_saved" ->
                    "Profile for {name} was saved."

                "person_saved_speech" ->
                    "The profile for {name} was saved."

                "person_save_failed" ->
                    "The person profile could not be saved."

                "person_result" ->
                    "Person Recognition Result"

                "speak_person_again" ->
                    "Speak Recognition Again"

                else ->
                    ""
            }
        }
    }
}