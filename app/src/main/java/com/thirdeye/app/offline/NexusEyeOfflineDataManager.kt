package com.thirdeye.app.offline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class NexusEyeOfflineDataStats(
    val dataVersion: String,
    val packagedEntryCount: Int,
    val customEntryCount: Int
) {
    val totalEntryCount: Int
        get() = packagedEntryCount + customEntryCount
}

data class NexusEyeOfflineQaEntry(
    val question: String,
    val answer: String
)

class NexusEyeOfflineDataManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val packagedData by lazy {
        loadPackagedData()
    }

    fun getStats(): NexusEyeOfflineDataStats {
        return NexusEyeOfflineDataStats(
            dataVersion = packagedData.version,
            packagedEntryCount = packagedData.entries.size,
            customEntryCount = loadCustomEntries().size
        )
    }

    fun findLocalAnswer(
        question: String
    ): String? {

        val normalizedQuestion =
            normalize(question)

        if (normalizedQuestion.isBlank()) {
            return null
        }

        val customMatch =
            loadCustomEntries()
                .firstOrNull {
                    normalize(it.question) ==
                            normalizedQuestion
                }

        if (customMatch != null) {
            return customMatch.answer
        }

        val packagedMatch =
            packagedData.entries
                .firstOrNull {
                    normalize(it.question) ==
                            normalizedQuestion
                }

        return packagedMatch?.answer
    }

    fun getPackagedEntries(): List<NexusEyeOfflineQaEntry> {
        return packagedData.entries
    }

    fun getCustomEntries(): List<NexusEyeOfflineQaEntry> {
        return loadCustomEntries()
    }

    fun saveCustomEntry(
        question: String,
        answer: String
    ): Boolean {

        val cleanQuestion =
            question.trim()

        val cleanAnswer =
            answer.trim()

        if (
            cleanQuestion.isBlank() ||
            cleanAnswer.isBlank()
        ) {
            return false
        }

        val entries =
            loadCustomEntries()
                .toMutableList()

        val normalizedQuestion =
            normalize(cleanQuestion)

        val existingIndex =
            entries.indexOfFirst {
                normalize(it.question) ==
                        normalizedQuestion
            }

        val newEntry =
            NexusEyeOfflineQaEntry(
                question = cleanQuestion,
                answer = cleanAnswer
            )

        if (existingIndex >= 0) {
            entries[existingIndex] =
                newEntry
        } else {
            entries +=
                newEntry
        }

        persistCustomEntries(entries)

        return true
    }

    fun removeCustomEntry(
        question: String
    ): Boolean {

        val normalizedQuestion =
            normalize(question)

        val entries =
            loadCustomEntries()
                .toMutableList()

        val removed =
            entries.removeAll {
                normalize(it.question) ==
                        normalizedQuestion
            }

        if (removed) {
            persistCustomEntries(entries)
        }

        return removed
    }

    fun clearCustomEntries(): Int {

        val entries =
            loadCustomEntries()

        if (entries.isEmpty()) {
            return 0
        }

        preferences
            .edit()
            .remove(KEY_CUSTOM_ENTRIES)
            .apply()

        return entries.size
    }

    fun hasCustomEntries(): Boolean {
        return loadCustomEntries().isNotEmpty()
    }

    fun getDataVersion(): String {
        return packagedData.version
    }

    private fun loadPackagedData(): PackagedData {

        return try {

            appContext
                .assets
                .open(ASSET_FILE_NAME)
                .bufferedReader()
                .use { reader ->

                    val rawJson =
                        reader.readText()

                    val root =
                        JSONObject(rawJson)

                    val version =
                        root.optString(
                            "version",
                            DEFAULT_VERSION
                        )

                    val entriesJson =
                        root.optJSONArray(
                            "entries"
                        )
                            ?: JSONArray()

                    val entries =
                        buildList {

                            for (
                            index in
                            0 until entriesJson.length()
                            ) {

                                val entryJson =
                                    entriesJson
                                        .optJSONObject(
                                            index
                                        )
                                        ?: continue

                                val question =
                                    entryJson
                                        .optString(
                                            "question"
                                        )
                                        .trim()

                                val answer =
                                    entryJson
                                        .optString(
                                            "answer"
                                        )
                                        .trim()

                                if (
                                    question.isNotBlank() &&
                                    answer.isNotBlank()
                                ) {
                                    add(
                                        NexusEyeOfflineQaEntry(
                                            question =
                                                question,
                                            answer =
                                                answer
                                        )
                                    )
                                }
                            }
                        }

                    PackagedData(
                        version = version,
                        entries = entries
                    )
                }

        } catch (_: Exception) {

            PackagedData(
                version =
                    DEFAULT_VERSION,
                entries =
                    emptyList()
            )
        }
    }

    private fun loadCustomEntries():
            List<NexusEyeOfflineQaEntry> {

        val rawJson =
            preferences.getString(
                KEY_CUSTOM_ENTRIES,
                null
            )
                ?: return emptyList()

        return try {

            val array =
                JSONArray(rawJson)

            buildList {

                for (
                index in
                0 until array.length()
                ) {

                    val entryJson =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    val question =
                        entryJson
                            .optString(
                                "question"
                            )
                            .trim()

                    val answer =
                        entryJson
                            .optString(
                                "answer"
                            )
                            .trim()

                    if (
                        question.isNotBlank() &&
                        answer.isNotBlank()
                    ) {
                        add(
                            NexusEyeOfflineQaEntry(
                                question =
                                    question,
                                answer =
                                    answer
                            )
                        )
                    }
                }
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    private fun persistCustomEntries(
        entries: List<NexusEyeOfflineQaEntry>
    ) {

        val array =
            JSONArray()

        entries.forEach { entry ->

            array.put(
                JSONObject()
                    .put(
                        "question",
                        entry.question
                    )
                    .put(
                        "answer",
                        entry.answer
                    )
            )
        }

        preferences
            .edit()
            .putString(
                KEY_CUSTOM_ENTRIES,
                array.toString()
            )
            .apply()
    }

    private fun normalize(
        value: String
    ): String {

        val withoutDiacritics =
            Normalizer
                .normalize(
                    value,
                    Normalizer.Form.NFD
                )
                .replace(
                    Regex("\\p{Mn}+"),
                    ""
                )

        return withoutDiacritics
            .lowercase(Locale.ROOT)
            .replace(
                Regex("[^\\p{L}\\p{N}]+"),
                " "
            )
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private data class PackagedData(
        val version: String,
        val entries:
        List<NexusEyeOfflineQaEntry>
    )

    companion object {

        private const val PREFS_NAME =
            "nexus_eye_offline_data"

        private const val KEY_CUSTOM_ENTRIES =
            "custom_entries_v1"

        private const val ASSET_FILE_NAME =
            "nexus_eye_offline_qa.json"

        private const val DEFAULT_VERSION =
            "1.0"
    }
}
