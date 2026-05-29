package io.github.tonyzhye.beancount.plugin

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PluginApiTest {

    @Test
    fun `should create plugin context`() {
        val entries = listOf(
            Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Test"
            )
        )
        val options = Options(filename = "test.beancount")
        val context = PluginContext(entries, options)

        assertEquals(entries, context.entries)
        assertEquals(options, context.options)
    }

    @Test
    fun `should filter entries by type`() {
        val entries = listOf(
            Open(meta = emptyMap(), date = LocalDate(2024, 1, 1), account = "Assets:Cash", currencies = listOf("USD")),
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 2), flag = "*", narration = "Test")
        )
        val context = PluginContext(entries, Options())

        val transactions = context.filterIsInstance<Transaction>()
        assertEquals(1, transactions.size)
        assertEquals("Test", transactions[0].narration)
    }

    @Test
    fun `should create plugin result with no changes`() {
        val entries = emptyList<Directive>()
        val result = PluginResult.noChange(entries)

        assertEquals(entries, result.entries)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `should combine plugin results`() {
        val entries1 = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "First")
        )
        val entries2 = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 2), flag = "*", narration = "Second")
        )

        val result1 = PluginResult(entries1, errors = listOf())
        val result2 = PluginResult(entries2, errors = listOf(), warnings = listOf())

        val combined = result1 + result2

        assertEquals(entries2, combined.entries) // Last entries win
        assertTrue(combined.errors.isEmpty())
        assertTrue(combined.warnings.isEmpty())
    }

    @Test
    fun `should register and resolve plugins`() {
        val registry = PluginRegistry()
        val plugin = TestPlugin("test-plugin")

        registry.register(plugin)

        assertEquals(1, registry.size)
        assertNotNull(registry.resolve("test-plugin"))
        assertTrue(registry.contains("test-plugin"))
    }

    @Test
    fun `should not allow duplicate plugin names`() {
        val registry = PluginRegistry()
        val plugin1 = TestPlugin("test")
        val plugin2 = TestPlugin("test")

        registry.register(plugin1)

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(plugin2)
        }
    }

    @Test
    fun `should not allow blank plugin names`() {
        val registry = PluginRegistry()
        val plugin = TestPlugin("")

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(plugin)
        }
    }

    @Test
    fun `should unregister plugins`() {
        val registry = PluginRegistry()
        val plugin = TestPlugin("test")

        registry.register(plugin)
        assertTrue(registry.unregister("test"))
        assertFalse(registry.unregister("test"))
        assertTrue(registry.isEmpty())
    }

    @Test
    fun `should get plugins by phase`() {
        val registry = PluginRegistry()
        val prePlugin = TestPlugin("pre", PluginPhase.PRE)
        val normalPlugin = TestPlugin("normal", PluginPhase.NORMAL)
        val postPlugin = TestPlugin("post", PluginPhase.POST)

        registry.register(prePlugin)
        registry.register(normalPlugin)
        registry.register(postPlugin)

        assertEquals(1, registry.pluginsByPhase(PluginPhase.PRE).size)
        assertEquals(1, registry.pluginsByPhase(PluginPhase.NORMAL).size)
        assertEquals(1, registry.pluginsByPhase(PluginPhase.POST).size)
    }

    @Test
    fun `should execute pipeline with phases`() {
        val pipeline = PluginPipeline()
        val prePlugin = TestPlugin("pre", PluginPhase.PRE) { ctx ->
            val newEntries = ctx.entries + Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 1),
                flag = "*",
                narration = "Pre"
            )
            PluginResult(newEntries)
        }
        val postPlugin = TestPlugin("post", PluginPhase.POST) { ctx ->
            val newEntries = ctx.entries + Transaction(
                meta = emptyMap(),
                date = LocalDate(2024, 1, 3),
                flag = "*",
                narration = "Post"
            )
            PluginResult(newEntries)
        }

        pipeline.addPhase(PluginPhase.PRE, prePlugin)
        pipeline.addPhase(PluginPhase.POST, postPlugin)

        val result = pipeline.execute(emptyList(), Options())

        assertEquals(2, result.entries.size)
        assertEquals("Pre", (result.entries[0] as Transaction).narration)
        assertEquals("Post", (result.entries[1] as Transaction).narration)
    }

    @Test
    fun `should handle plugin errors gracefully`() {
        val pipeline = PluginPipeline()
        val errorPlugin = object : BeancountPlugin {
            override val name = "error"
            override fun transform(context: PluginContext): PluginResult {
                throw RuntimeException("Test error")
            }
        }

        pipeline.addPhase(PluginPhase.NORMAL, errorPlugin)

        val result = pipeline.execute(emptyList(), Options(filename = "test.beancount"))

        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors[0].message.contains("error"))
    }

    @Test
    fun `should use DSL to build pipeline`() {
        val plugin = TestPlugin("test")

        val pipeline = pluginPipeline {
            normal(plugin)
        }

        val normalPlugins = pipeline.getPhase(PluginPhase.NORMAL)
        assertEquals(1, normalPlugins.size)
        assertEquals("test", normalPlugins[0].name)
    }

    @Test
    fun `should use adapter for old-style transforms`() {
        val oldStyleTransform: PluginTransform = { entries, _ ->
            Pair(entries, emptyList())
        }

        val plugin = PluginAdapter("legacy", PluginPhase.NORMAL, oldStyleTransform)
        val context = PluginContext(emptyList(), Options())

        val result = plugin.transform(context)

        assertTrue(result.errors.isEmpty())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `should create context from entries and options`() {
        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Test")
        )
        val options = Options()

        val context = entries.toPluginContext(options)

        assertEquals(entries, context.entries)
        assertEquals(options, context.options)
    }

    // Test helpers

    private class TestPlugin(
        override val name: String,
        override val phase: PluginPhase = PluginPhase.NORMAL,
        private val transformFn: ((PluginContext) -> PluginResult)? = null
    ) : BeancountPlugin {
        override fun transform(context: PluginContext): PluginResult {
            return transformFn?.invoke(context) ?: PluginResult.noChange(context.entries)
        }
    }
}
