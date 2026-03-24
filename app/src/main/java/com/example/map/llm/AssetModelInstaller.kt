package com.example.map.llm

import android.content.Context
import java.io.File
import java.security.MessageDigest

object AssetModelInstaller {
    data class InstalledModel(
        val file: File,
        val bytes: Long,
        val sha256: String,
    )

    /**
     * Копирует модель из assets (например `models/qwen2.5-1.5b-instruct-q4_k_m.gguf`)
     * во внутреннее хранилище приложения и возвращает путь к файлу.
     *
     * Это нужно потому что llama.cpp открывает модель по файловому пути.
     */
    fun ensureInstalled(
        context: Context,
        assetPath: String,
        targetFileName: String = assetPath.substringAfterLast('/'),
    ): InstalledModel {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(modelsDir, targetFileName)
        val targetHashFile = File(modelsDir, "$targetFileName.sha256")

        val currentAssetHash = context.assets.open(assetPath).use { input ->
            sha256Hex(input.readBytes())
        }

        val installedHash = targetHashFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        val needsInstall = !target.exists() || installedHash != currentAssetHash

        if (needsInstall) {
            // Пишем во временный файл, затем атомарно переименовываем.
            val tmp = File(modelsDir, "$targetFileName.tmp")
            if (tmp.exists()) tmp.delete()

            context.assets.open(assetPath).use { input ->
                tmp.outputStream().use { out ->
                    input.copyTo(out)
                }
            }

            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                // fallback (например, если renameTo не сработал из-за файловой системы)
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }

            targetHashFile.writeText(currentAssetHash)
        }

        return InstalledModel(
            file = target,
            bytes = target.length(),
            sha256 = currentAssetHash,
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}

