package dev.sz.aid

import java.io.PrintStream

/**
 * Writes LLM output to [out], wrapping reasoning text in Markdown blockquotes.
 *
 * State machine: the writer tracks whether it is currently inside a REASONING
 * or CONTENT block. Transitions between blocks insert a blank line separator.
 * Multiple reasoning blocks are allowed (interleaved with content).
 */
class ResultWriter(private val out: PrintStream = System.out) {

    private var currentBlock: Block? = null

    fun writeReasoning(reasoning: String) {
        if (currentBlock == Block.CONTENT) {
            out.println() // close the current content line
            out.println() // blank line
        }
        if (currentBlock != Block.REASONING) {
            out.println("> **Reasoning**")
            out.println(">")
            out.print("> ")
            currentBlock = Block.REASONING
        }
        // Each newline inside reasoning becomes a new blockquote line
        out.print(reasoning.replace("\n", "\n> "))
        out.flush() // explicit flushing
    }

    fun writeContent(content: String) {
        if (currentBlock == Block.REASONING) {
            out.println() // close the current reasoning line
            out.println() // blank line
        }
        if (currentBlock != Block.CONTENT) {
            currentBlock = Block.CONTENT
        }
        out.print(content)
        out.flush() // explicit flushing
    }

    /**
     * Finalizes output formatting.
     */
    fun finish() {
        if (currentBlock != null) {
            out.println() // just close the current line
        }
    }

    private enum class Block {
        REASONING,
        CONTENT,
    }
}
