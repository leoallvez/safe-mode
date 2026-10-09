package com.poc.safemode.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.poc.safemode.domain.TrustedArea
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface TrustedAreaRepository {
    val areas: Flow<List<TrustedArea>>

    suspend fun upsert(area: TrustedArea)

    suspend fun delete(id: String)
}

private val Context.trustedAreaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trusted_area"
)

class DataStoreTrustedAreaRepository(context: Context) : TrustedAreaRepository {

    private val dataStore = context.applicationContext.trustedAreaDataStore

    override val areas: Flow<List<TrustedArea>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> TrustedAreaJson.decode(preferences[KEY_AREAS]) }

    override suspend fun upsert(area: TrustedArea) {
        dataStore.edit { preferences ->
            val current = TrustedAreaJson.decode(preferences[KEY_AREAS])
            val updated = if (current.any { it.id == area.id }) {
                current.map { if (it.id == area.id) area else it }
            } else {
                current + area
            }
            preferences[KEY_AREAS] = TrustedAreaJson.encode(updated)
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            val current = TrustedAreaJson.decode(preferences[KEY_AREAS])
            preferences[KEY_AREAS] = TrustedAreaJson.encode(current.filterNot { it.id == id })
        }
    }

    private companion object {
        val KEY_AREAS = stringPreferencesKey("areas_json")
    }
}
