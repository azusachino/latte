package com.azusachino.latte.data.update

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (prerelease == null && other.prerelease != null) return 1
        if (prerelease != null && other.prerelease == null) return -1
        if (prerelease != null && other.prerelease != null) return prerelease.compareTo(other.prerelease)
        return 0
    }

    companion object {
        fun parse(versionStr: String): SemanticVersion? {
            val clean = versionStr.trim().removePrefix("v").removePrefix("V")
            if (clean.isEmpty()) return null
            val parts = clean.split("-", limit = 2)
            val numParts = parts[0].split(".")
            val major = numParts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = numParts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = numParts.getOrNull(2)?.toIntOrNull() ?: 0
            val prerelease = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            return SemanticVersion(major, minor, patch, prerelease)
        }
    }
}
