package com.example.map.llm

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import org.codeshipping.llamakotlin.LlamaModel
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal Kotlin-friendly wrapper around llama.cpp (via llama-kotlin-android).
 *
 * - Loads a GGUF model from [modelPath]
 * - Adds [systemPrompt] automatically once at the beginning of a session
 * - Provides both one-shot and streaming generation APIs
 */
class LocalLlamaClient(
    private val modelPath: String,
    private val template: LlamaChatTemplate = LlamaChatTemplate.CHATML,
    private val systemPrompt: String = DefaultSystemPrompt.TOUR_GUIDE_RU,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val config: LlamaConfigOverrides = LlamaConfigOverrides(),
) : Closeable {

    data class LlamaConfigOverrides(
        val contextSize: Int = 2048,
        val threads: Int = maxOf(1, Runtime.getRuntime().availableProcessors() / 2),
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val maxTokens: Int = 512,
        val gpuLayers: Int = 0,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
    )

    private val sessionStarted = AtomicBoolean(false)
    private val history = ArrayList<Message>(32)

    private var model: LlamaModel? = null

    sealed class Role(val wireName: String) {
        data object System : Role("system")
        data object User : Role("user")
        data object Assistant : Role("assistant")
    }

    data class Message(val role: Role, val content: String)

    suspend fun load() {
        if (model?.isLoaded == true) return
        model = LlamaModel.load(modelPath) {
            contextSize = config.contextSize
            threads = config.threads
            temperature = config.temperature
            topP = config.topP
            topK = config.topK
            repeatPenalty = config.repeatPenalty
            maxTokens = config.maxTokens
            gpuLayers = config.gpuLayers
            useMmap = config.useMmap
            useMlock = config.useMlock
        }
    }

    fun startNewSession() {
        model?.cancelGeneration()
        history.clear()
        sessionStarted.set(false)
    }

    suspend fun generate(userText: String): String {
        ensureSessionInitialized()
        val prompt = buildPromptWithNextUserMessage(userText)
        val out = requireModel().generate(prompt)
        appendTurn(userText = userText, assistantText = out)
        return out
    }

    fun generateStream(userText: String): Flow<String> = channelFlow {
        ensureSessionInitialized()
        val prompt = buildPromptWithNextUserMessage(userText)

        val model = requireModel()
        val sb = StringBuilder()

        model.generateStream(prompt).collect { token ->
            sb.append(token)
            send(token)
        }

        appendTurn(userText = userText, assistantText = sb.toString())
    }.flowOn(dispatcher)

    private fun appendTurn(userText: String, assistantText: String) {
        history.add(Message(Role.User, userText))
        history.add(Message(Role.Assistant, assistantText))
    }

    private fun ensureSessionInitialized() {
        if (sessionStarted.compareAndSet(false, true)) {
            if (systemPrompt.isNotBlank()) {
                history.add(Message(Role.System, systemPrompt.trim()))
            }
        }
    }

    private fun buildPromptWithNextUserMessage(userText: String): String {
        val messages = ArrayList<Message>(history.size + 1).apply {
            addAll(history)
            add(Message(Role.User, userText))
        }
        return when (template) {
            LlamaChatTemplate.RAW -> buildRaw(messages)
            LlamaChatTemplate.LLAMA3 -> buildLlama3(messages)
            LlamaChatTemplate.PHI3 -> buildPhi3(messages)
            LlamaChatTemplate.CHATML -> buildChatML(messages)
        }
    }

    private fun buildRaw(messages: List<Message>): String =
        messages.joinToString(separator = "\n\n") { "${it.role.wireName}: ${it.content}" } + "\n\nassistant:"

    private fun buildChatML(messages: List<Message>): String = buildString {
        for (m in messages) {
            append("<|im_start|>")
            append(m.role.wireName)
            append('\n')
            append(m.content)
            append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }

    private fun buildPhi3(messages: List<Message>): String = buildString {
        fun tag(role: Role): String = when (role) {
            Role.System -> "system"
            Role.User -> "user"
            Role.Assistant -> "assistant"
        }
        for (m in messages) {
            append("<|")
            append(tag(m.role))
            append("|>\n")
            append(m.content)
            append("<|end|>\n")
        }
        append("<|assistant|>\n")
    }

    private fun buildLlama3(messages: List<Message>): String = buildString {
        append("<|begin_of_text|>")
        for (m in messages) {
            append("<|start_header_id|>")
            append(m.role.wireName)
            append("<|end_header_id|>\n\n")
            append(m.content)
            append("<|eot_id|>")
        }
        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
    }

    private fun requireModel(): LlamaModel =
        model ?: error("LocalLlamaClient is not loaded. Call load() first.")

    override fun close() {
        model?.close()
        model = null
    }
}

