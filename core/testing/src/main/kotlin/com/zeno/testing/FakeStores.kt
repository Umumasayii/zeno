package com.zeno.testing

import com.zeno.domain.model.ModelFileStore
import com.zeno.domain.settings.SettingsRepository
import com.zeno.domain.settings.ZenoSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.InputStream

class InMemorySettingsRepository(initial: ZenoSettings = ZenoSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<ZenoSettings> = state
    override suspend fun update(transform: (ZenoSettings) -> ZenoSettings) { state.value = transform(state.value) }
    override suspend fun resetToDefaults() { state.value = ZenoSettings() }
}

class InMemoryModelFileStore(val files: MutableMap<String, ByteArray> = mutableMapOf()) : ModelFileStore {
    override fun exists(name: String) = name in files
    override fun size(name: String) = files.getValue(name).size.toLong()
    override fun open(name: String): InputStream = ByteArrayInputStream(files.getValue(name))
}
