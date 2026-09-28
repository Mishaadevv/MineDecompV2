package com.minedecomp.core.mappings

import com.minedecomp.core.JarType
import com.minedecomp.core.Mappings

interface MappingProvider {
    val name: String
    /** False for fallback providers that return empty (identity) mappings. */
    val hasMappings: Boolean
        get() = true
    suspend fun supports(version: String): Boolean
    suspend fun fetchMappings(version: String, jarType: JarType): Mappings
}
