package com.thirdeye.app.vision

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

class FaceProfileStore(
    context: Context
) {

    companion object {
        private const val PREF_NAME =
            "nexus_eye_face_profiles"

        private const val KEY_PROFILES =
            "profiles"

        /*
         * This is intentionally not extremely high.
         * It can be adjusted after testing with real photos.
         */
        private const val DEFAULT_MINIMUM_SIMILARITY =
            0.82f
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

    @Synchronized
    fun getProfiles(): List<FaceProfile> {

        val raw =
            preferences.getString(
                KEY_PROFILES,
                null
            )
                ?: return emptyList()

        return try {

            val array =
                JSONArray(raw)

            val result =
                ArrayList<FaceProfile>(
                    array.length()
                )

            for (index in 0 until array.length()) {

                val objectValue =
                    array.optJSONObject(index)
                        ?: continue

                val name =
                    objectValue
                        .optString(
                            "name",
                            ""
                        )
                        .trim()

                if (name.isBlank()) {
                    continue
                }

                val createdAt =
                    objectValue
                        .optLong(
                            "createdAt",
                            System.currentTimeMillis()
                        )

                val embeddingArray =
                    objectValue
                        .optJSONArray(
                            "embedding"
                        )
                        ?: continue

                if (embeddingArray.length() <= 0) {
                    continue
                }

                val embedding =
                    FloatArray(
                        embeddingArray.length()
                    )

                for (
                i in
                0 until embeddingArray.length()
                ) {
                    embedding[i] =
                        embeddingArray
                            .optDouble(
                                i,
                                0.0
                            )
                            .toFloat()
                }

                normalizeInPlace(
                    embedding
                )

                result +=
                    FaceProfile(
                        name = name,
                        embedding = embedding,
                        createdAt = createdAt
                    )
            }

            result

        } catch (_: Exception) {

            emptyList()
        }
    }

    @Synchronized
    fun saveProfile(
        name: String,
        embedding: FloatArray
    ) {

        val cleanName =
            name
                .trim()

        require(
            cleanName.isNotBlank()
        ) {
            "Person name cannot be empty."
        }

        require(
            embedding.isNotEmpty()
        ) {
            "Face embedding cannot be empty."
        }

        val normalizedEmbedding =
            embedding.copyOf()

        normalizeInPlace(
            normalizedEmbedding
        )

        val existing =
            getProfiles()
                .toMutableList()

        val updatedProfile =
            FaceProfile(
                name = cleanName,
                embedding = normalizedEmbedding,
                createdAt = System.currentTimeMillis()
            )

        val existingIndex =
            existing.indexOfFirst {
                it.name.equals(
                    cleanName,
                    ignoreCase = true
                )
            }

        if (existingIndex >= 0) {
            existing[existingIndex] =
                updatedProfile
        } else {
            existing +=
                updatedProfile
        }

        saveAll(
            existing
        )
    }

    @Synchronized
    fun deleteProfile(
        name: String
    ): Boolean {

        val current =
            getProfiles()

        val remaining =
            current.filterNot {
                it.name.equals(
                    name.trim(),
                    ignoreCase = true
                )
            }

        if (
            remaining.size ==
            current.size
        ) {
            return false
        }

        saveAll(
            remaining
        )

        return true
    }

    @Synchronized
    fun clearProfiles() {

        preferences
            .edit()
            .remove(KEY_PROFILES)
            .apply()
    }

    fun findBestMatch(
        embedding: FloatArray,
        minimumSimilarity: Float =
            DEFAULT_MINIMUM_SIMILARITY
    ): FaceMatch? {

        if (embedding.isEmpty()) {
            return null
        }

        val normalizedQuery =
            embedding.copyOf()

        normalizeInPlace(
            normalizedQuery
        )

        val profiles =
            getProfiles()

        if (profiles.isEmpty()) {
            return null
        }

        var bestName: String? =
            null

        var bestSimilarity =
            -1f

        for (profile in profiles) {

            if (
                profile.embedding.size !=
                normalizedQuery.size
            ) {
                continue
            }

            val similarity =
                cosineSimilarity(
                    normalizedQuery,
                    profile.embedding
                )

            if (
                similarity >
                bestSimilarity
            ) {

                bestSimilarity =
                    similarity

                bestName =
                    profile.name
            }
        }

        if (
            bestName == null ||
            bestSimilarity <
            minimumSimilarity
        ) {
            return null
        }

        return FaceMatch(
            name = bestName,
            similarity = bestSimilarity
        )
    }

    private fun saveAll(
        profiles: List<FaceProfile>
    ) {

        val array =
            JSONArray()

        for (profile in profiles) {

            val objectValue =
                JSONObject()

            objectValue.put(
                "name",
                profile.name
            )

            objectValue.put(
                "createdAt",
                profile.createdAt
            )

            val embeddingArray =
                JSONArray()

            for (
            value in
            profile.embedding
            ) {
                embeddingArray.put(
                    value.toDouble()
                )
            }

            objectValue.put(
                "embedding",
                embeddingArray
            )

            array.put(
                objectValue
            )
        }

        preferences
            .edit()
            .putString(
                KEY_PROFILES,
                array.toString()
            )
            .apply()
    }

    private fun cosineSimilarity(
        first: FloatArray,
        second: FloatArray
    ): Float {

        if (
            first.size !=
            second.size ||
            first.isEmpty()
        ) {
            return -1f
        }

        var dot =
            0.0

        var firstMagnitude =
            0.0

        var secondMagnitude =
            0.0

        for (
        index in
        first.indices
        ) {

            val a =
                first[index].toDouble()

            val b =
                second[index].toDouble()

            dot +=
                a * b

            firstMagnitude +=
                a * a

            secondMagnitude +=
                b * b
        }

        if (
            firstMagnitude <= 0.0 ||
            secondMagnitude <= 0.0
        ) {
            return -1f
        }

        return (
                dot /
                        (
                                sqrt(firstMagnitude) *
                                        sqrt(secondMagnitude)
                                )
                )
            .toFloat()
    }

    private fun normalizeInPlace(
        vector: FloatArray
    ) {

        var sumSquares =
            0.0

        for (value in vector) {
            val doubleValue =
                value.toDouble()

            sumSquares +=
                doubleValue *
                        doubleValue
        }

        val magnitude =
            sqrt(sumSquares)

        if (
            magnitude <= 0.0000001
        ) {
            return
        }

        for (
        index in
        vector.indices
        ) {

            vector[index] =
                (
                        vector[index].toDouble() /
                                magnitude
                        )
                    .toFloat()
        }
    }
}