package io.ezz.launcher.core.storage.supabase

data class SupabaseConfig(
    val supabaseUrl: String = DEFAULT_URL,
    val anonKey: String = DEFAULT_ANON_KEY
) {
    val restUrl: String get() = "${supabaseUrl.trimEnd('/')}/rest/v1"
    val authUrl: String get() = "${supabaseUrl.trimEnd('/')}/auth/v1"

    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank() && !anonKey.contains("placeholder")

    companion object {
        const val DEFAULT_URL = "https://idywzmspumhahzzfsdjx.supabase.co"
        const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlkeXd6bXNwdW1oYWh6emZzZGp4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDAzNTA0MDAsImV4cCI6MjA1NTkyNjQwMH0.ezzlauncher_anon_public_key_placeholder"

        fun fromEnvironment(): SupabaseConfig {
            val envUrl = System.getenv("SUPABASE_URL")?.ifBlank { null }
            val envKey = System.getenv("SUPABASE_ANON_KEY")?.ifBlank { null }
            return SupabaseConfig(
                supabaseUrl = envUrl ?: DEFAULT_URL,
                anonKey = envKey ?: DEFAULT_ANON_KEY
            )
        }
    }
}
