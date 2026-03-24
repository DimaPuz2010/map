package com.example.map.llm

enum class LlamaChatTemplate {
    /**
     * Use when model expects raw prompt (no role tags).
     */
    RAW,

    /**
     * Llama 3.x instruct format (GGUF fine-tunes usually accept it).
     */
    LLAMA3,

    /**
     * Phi-3/3.5 instruct format.
     */
    PHI3,

    /**
     * ChatML (Qwen and many others).
     */
    CHATML,
}

