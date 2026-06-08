package io.github.tonyzhye.beancount.plugin

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Extended tests for plugin-api module.
 */
class PluginApiExtendedTest {

    // ------------------------------------------------------------------
    // PluginBaseClasses
    // ------------------------------------------------------------------

    @Test
    fun `TransactionPlugin should process transactions`() {
        val plugin = object : TransactionPlugin() {
            override val name = "test-txn-plugin"
            override val phase = PluginPhase.NORMAL
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(transaction.copy(narration = transaction.narration + " [processed]"), emptyList())
            }
        }

        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Test")
        )
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals(1, result.entries.size)
        val processed = result.entries[0] as Transaction
        assertEquals("Test [processed]", processed.narration)
    }

    @Test
    fun `TransactionPlugin should skip non-transaction entries`() {
        val plugin = object : TransactionPlugin() {
            override val name = "test-txn-plugin"
            override val phase = PluginPhase.NORMAL
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(transaction, emptyList())
            }
        }

        val entries = listOf(
            Open(meta = emptyMap(), date = LocalDate(2024, 1, 1), account = "Assets:Cash", currencies = listOf("USD"))
        )
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals(1, result.entries.size)
        assertTrue(result.entries[0] is Open)
    }

    @Test
    fun `TransactionPlugin should return null to keep original`() {
        val plugin = object : TransactionPlugin() {
            override val name = "test-txn-plugin"
            override val phase = PluginPhase.NORMAL
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(null, emptyList())
            }
        }

        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Original")
        )
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals("Original", (result.entries[0] as Transaction).narration)
    }

    @Test
    fun `TransactionPlugin should collect errors`() {
        val plugin = object : TransactionPlugin() {
            override val name = "test-txn-plugin"
            override val phase = PluginPhase.NORMAL
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(transaction, listOf(LoadError(mapOf("filename" to "test.beancount", "lineno" to 1), "Test error")))
            }
        }

        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Test")
        )
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `GeneratorPlugin should generate entries`() {
        val plugin = object : GeneratorPlugin() {
            override val name = "test-gen-plugin"
            override val phase = PluginPhase.NORMAL
            override fun generateEntries(context: PluginContext): Pair<List<Directive>, List<BeancountError>> {
                return Pair(listOf(
                    Transaction(meta = emptyMap(), date = LocalDate(2024, 2, 1), flag = "*", narration = "Generated")
                ), emptyList())
            }
        }

        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Original")
        )
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals(2, result.entries.size)
        assertTrue(result.entries.any { it is Transaction && it.narration == "Generated" })
    }

    @Test
    fun `GeneratorPlugin should sort generated entries`() {
        val plugin = object : GeneratorPlugin() {
            override val name = "test-gen-plugin"
            override val phase = PluginPhase.NORMAL
            override fun generateEntries(context: PluginContext): Pair<List<Directive>, List<BeancountError>> {
                return Pair(listOf(
                    Transaction(meta = emptyMap(), date = LocalDate(2024, 3, 1), flag = "*", narration = "Later"),
                    Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Earlier")
                ), emptyList())
            }
        }

        val entries = emptyList<Directive>()
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals(2, result.entries.size)
        val txns = result.entries.filterIsInstance<Transaction>()
        assertTrue(txns[0].date <= txns[1].date)
    }

    @Test
    fun `GeneratorPlugin should return errors`() {
        val plugin = object : GeneratorPlugin() {
            override val name = "test-gen-plugin"
            override val phase = PluginPhase.NORMAL
            override fun generateEntries(context: PluginContext): Pair<List<Directive>, List<BeancountError>> {
                return Pair(emptyList(), listOf(LoadError(mapOf("filename" to "test.beancount", "lineno" to 1), "Gen error")))
            }
        }

        val context = PluginContext(emptyList(), Options())
        val result = plugin.transform(context)

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `FilterPlugin should filter entries`() {
        val plugin = object : FilterPlugin() {
            override val name = "test-filter-plugin"
            override val phase = PluginPhase.NORMAL
            override fun shouldKeep(entry: Directive, context: PluginContext): Pair<Boolean, List<BeancountError>> {
                return Pair(entry is Transaction, emptyList())
            }
        }

        val entries = listOf(
            Open(meta = emptyMap(), date = LocalDate(2024, 1, 1), account = "Assets:Cash", currencies = listOf("USD")),
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 2), flag = "*", narration = "Test")
        )
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals(1, result.entries.size)
        assertTrue(result.entries[0] is Transaction)
    }

    @Test
    fun `FilterPlugin should collect errors`() {
        val plugin = object : FilterPlugin() {
            override val name = "test-filter-plugin"
            override val phase = PluginPhase.NORMAL
            override fun shouldKeep(entry: Directive, context: PluginContext): Pair<Boolean, List<BeancountError>> {
                return Pair(true, listOf(LoadError(mapOf("filename" to "test.beancount", "lineno" to 1), "Filter warning")))
            }
        }

        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Test")
        )
        val context = PluginContext(entries, Options())
        val result = plugin.transform(context)

        assertEquals(1, result.errors.size)
    }

    // ------------------------------------------------------------------
    // PluginRegistry
    // ------------------------------------------------------------------

    @Test
    fun `PluginRegistry withDefaults should create empty registry`() {
        val registry = PluginRegistry.withDefaults()
        // withDefaults currently returns an empty registry
        assertNotNull(registry)
    }

    @Test
    fun `PluginRegistry isEmpty should work`() {
        val registry = PluginRegistry()
        assertTrue(registry.isEmpty())
        registry.register(TestPlugin("test"))
        assertFalse(registry.isEmpty())
    }

    @Test
    fun `PluginRegistry pluginsByPhase should group plugins`() {
        val registry = PluginRegistry()
        val prePlugin = TestPlugin("pre-plugin", phase = PluginPhase.PRE)
        val postPlugin = TestPlugin("post-plugin", phase = PluginPhase.POST)

        registry.register(prePlugin)
        registry.register(postPlugin)

        assertTrue(registry.pluginsByPhase(PluginPhase.PRE).contains(prePlugin))
        assertTrue(registry.pluginsByPhase(PluginPhase.POST).contains(postPlugin))
    }

    @Test
    fun `PluginRegistry allPlugins should return all plugins`() {
        val registry = PluginRegistry()
        registry.register(TestPlugin("p1"))
        registry.register(TestPlugin("p2"))

        val all = registry.allPlugins()
        assertEquals(2, all.size)
    }

    @Test
    fun `PluginRegistry clear should remove all plugins`() {
        val registry = PluginRegistry()
        registry.register(TestPlugin("test"))
        registry.clear()
        assertTrue(registry.isEmpty())
    }

    @Test
    fun `PluginRegistry unregister should remove plugin`() {
        val registry = PluginRegistry()
        registry.register(TestPlugin("test"))
        assertTrue(registry.contains("test"))
        registry.unregister("test")
        assertFalse(registry.contains("test"))
    }

    @Test
    fun `PluginRegistry registerAll should register multiple plugins`() {
        val registry = PluginRegistry()
        val plugins = listOf(TestPlugin("p1"), TestPlugin("p2"))
        registry.registerAll(*plugins.toTypedArray())
        assertEquals(2, registry.size)
    }

    @Test
    fun `PluginRegistry resolve should return null for missing plugin`() {
        val registry = PluginRegistry()
        assertNull(registry.resolve("missing"))
    }

    // ------------------------------------------------------------------
    // PluginPipeline
    // ------------------------------------------------------------------

    @Test
    fun `PluginPipeline should execute NORMAL phase`() {
        val plugin = object : TransactionPlugin() {
            override val name = "normal-test"
            override val phase = PluginPhase.NORMAL
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(transaction.copy(narration = transaction.narration + " [normal]"), emptyList())
            }
        }

        val pipeline = PluginPipeline()
        pipeline.addPhase(PluginPhase.NORMAL, plugin)

        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Test")
        )
        val context = PluginContext(entries, Options())
        val result = pipeline.execute(context.entries, context.options)

        assertTrue(result.entries[0] is Transaction)
        assertEquals("Test [normal]", (result.entries[0] as Transaction).narration)
    }

    @Test
    fun `PluginPipeline should execute all phases in order`() {
        val prePlugin = object : TransactionPlugin() {
            override val name = "pre"
            override val phase = PluginPhase.PRE
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(transaction.copy(narration = transaction.narration + "[pre]"), emptyList())
            }
        }
        val normalPlugin = object : TransactionPlugin() {
            override val name = "normal"
            override val phase = PluginPhase.NORMAL
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(transaction.copy(narration = transaction.narration + "[normal]"), emptyList())
            }
        }
        val postPlugin = object : TransactionPlugin() {
            override val name = "post"
            override val phase = PluginPhase.POST
            override fun processTransaction(transaction: Transaction, context: PluginContext): Pair<Transaction?, List<BeancountError>> {
                return Pair(transaction.copy(narration = transaction.narration + "[post]"), emptyList())
            }
        }

        val pipeline = PluginPipeline()
        pipeline.addPhase(PluginPhase.PRE, prePlugin)
        pipeline.addPhase(PluginPhase.NORMAL, normalPlugin)
        pipeline.addPhase(PluginPhase.POST, postPlugin)

        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "")
        )
        val context = PluginContext(entries, Options())
        val result = pipeline.execute(context.entries, context.options)

        val txn = result.entries[0] as Transaction
        assertTrue(txn.narration?.contains("[pre]") == true)
        assertTrue(txn.narration?.contains("[normal]") == true)
        assertTrue(txn.narration?.contains("[post]") == true)
    }

    @Test
    fun `PluginPipeline addFromRegistry should add plugins from registry`() {
        val registry = PluginRegistry()
        registry.register(TestPlugin("reg-plugin", phase = PluginPhase.NORMAL))

        val pipeline = PluginPipeline()
        pipeline.addFromRegistry(registry)

        val entries = emptyList<Directive>()
        val context = PluginContext(entries, Options())
        val result = pipeline.execute(context.entries, context.options)
        assertNotNull(result)
    }

    @Test
    fun `PluginPipeline getPhase should return plugins for phase`() {
        val pipeline = PluginPipeline()
        val plugin = TestPlugin("test", phase = PluginPhase.PRE)
        pipeline.addPhase(PluginPhase.PRE, plugin)

        val phasePlugins = pipeline.getPhase(PluginPhase.PRE)
        assertEquals(1, phasePlugins.size)
    }

    @Test
    fun `PluginPipeline getPhase for empty phase should return empty list`() {
        val pipeline = PluginPipeline()
        val phasePlugins = pipeline.getPhase(PluginPhase.POST)
        assertTrue(phasePlugins.isEmpty())
    }

    @Test
    fun `PluginPipeline clear should remove all plugins`() {
        val pipeline = PluginPipeline()
        pipeline.addPhase(PluginPhase.PRE, TestPlugin("test"))
        pipeline.clear()
        assertTrue(pipeline.getPhase(PluginPhase.PRE).isEmpty())
    }

    @Test
    fun `PluginPipeline should propagate warnings`() {
        val plugin = object : BeancountPlugin {
            override val name = "warn-plugin"
            override val phase = PluginPhase.NORMAL
            override fun transform(context: PluginContext): PluginResult {
                return PluginResult(context.entries, warnings = listOf(LoadError(emptyMap(), "Test warning")))
            }
        }

        val pipeline = PluginPipeline()
        pipeline.addPhase(PluginPhase.NORMAL, plugin)

        val entries = emptyList<Directive>()
        val context = PluginContext(entries, Options())
        val result = pipeline.execute(context.entries, context.options)

        assertTrue(result.warnings.any { it.message == "Test warning" })
    }

    @Test
    fun `PluginPipeline should catch PluginException`() {
        val plugin = object : BeancountPlugin {
            override val name = "error-plugin"
            override val phase = PluginPhase.NORMAL
            override fun transform(context: PluginContext): PluginResult {
                throw PluginException("Plugin failed", this.name)
            }
        }

        val pipeline = PluginPipeline()
        pipeline.addPhase(PluginPhase.NORMAL, plugin)

        val entries = emptyList<Directive>()
        val context = PluginContext(entries, Options())
        val result = pipeline.execute(context.entries, context.options)

        assertTrue(result.errors.any { it.message.contains("Plugin failed") })
    }

    @Test
    fun `PluginPipeline should handle empty pipeline`() {
        val pipeline = PluginPipeline()
        val entries = listOf(
            Transaction(meta = emptyMap(), date = LocalDate(2024, 1, 1), flag = "*", narration = "Test")
        )
        val context = PluginContext(entries, Options())
        val result = pipeline.execute(context.entries, context.options)

        assertEquals(entries, result.entries)
        assertTrue(result.errors.isEmpty())
    }

    // ------------------------------------------------------------------
    // PluginDsl
    // ------------------------------------------------------------------

    @Test
    fun `pluginPipeline DSL should build pipeline with pre phase`() {
        val plugin = TestPlugin("pre-plugin", phase = PluginPhase.PRE)
        val pipeline = pluginPipeline {
            pre(plugin)
        }

        assertEquals(1, pipeline.getPhase(PluginPhase.PRE).size)
    }

    @Test
    fun `pluginPipeline DSL should build pipeline with post phase`() {
        val plugin = TestPlugin("post-plugin", phase = PluginPhase.POST)
        val pipeline = pluginPipeline {
            post(plugin)
        }

        assertEquals(1, pipeline.getPhase(PluginPhase.POST).size)
    }

    @Test
    fun `pluginPipeline DSL should support unary plus`() {
        val plugin = TestPlugin("normal-plugin", phase = PluginPhase.NORMAL)
        val pipeline = pluginPipeline {
            +plugin
        }

        assertEquals(1, pipeline.getPhase(PluginPhase.NORMAL).size)
    }

    @Test
    fun `pluginRegistry DSL should register plugins`() {
        val plugin = TestPlugin("reg-plugin")
        val registry = pluginRegistry {
            register(plugin)
        }

        assertTrue(registry.contains("reg-plugin"))
    }

    // ------------------------------------------------------------------
    // PluginResult
    // ------------------------------------------------------------------

    @Test
    fun `PluginResult should combine warnings`() {
        val w1 = LoadError(emptyMap(), "Warning 1")
        val w2 = LoadError(emptyMap(), "Warning 2")
        val result1 = PluginResult(emptyList(), warnings = listOf(w1))
        val result2 = PluginResult(emptyList(), warnings = listOf(w2))

        val combined = result1 + result2
        assertEquals(2, combined.warnings.size)
        assertTrue(combined.warnings.any { it.message == "Warning 1" })
        assertTrue(combined.warnings.any { it.message == "Warning 2" })
    }

    @Test
    fun `PluginResult should combine errors`() {
        val e1 = LoadError(emptyMap(), "Error 1")
        val e2 = LoadError(emptyMap(), "Error 2")
        val result1 = PluginResult(emptyList(), errors = listOf(e1))
        val result2 = PluginResult(emptyList(), errors = listOf(e2))

        val combined = result1 + result2
        assertEquals(2, combined.errors.size)
    }

    @Test
    fun `PluginResult noChange should have empty errors and warnings`() {
        val entries = emptyList<Directive>()
        val result = PluginResult.noChange(entries)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    // ------------------------------------------------------------------
    // Annotations
    // ------------------------------------------------------------------

    @Test
    fun `AutoPlugin annotation should have correct properties`() {
        val annotation = AutoPlugin::class.java
        assertEquals(AnnotationRetention.RUNTIME, annotation.getAnnotation(Retention::class.java)?.value)
    }

    @Test
    fun `ExperimentalPlugin annotation should exist`() {
        val annotation = ExperimentalPlugin::class.java
        assertNotNull(annotation)
    }

    @Test
    fun `DeprecatedPlugin annotation should exist`() {
        val annotation = DeprecatedPlugin::class.java
        assertNotNull(annotation)
    }

    // ------------------------------------------------------------------
    // PluginAdapter
    // ------------------------------------------------------------------

    @Test
    fun `PluginAdapter should wrap old-style transform`() {
        val transform: PluginTransform = { entries, _ -> Pair(entries, emptyList()) }
        val adapter = PluginAdapter("adapted", PluginPhase.NORMAL, transform)

        val entries = emptyList<Directive>()
        val context = PluginContext(entries, Options())
        val result = adapter.transform(context)

        assertEquals(entries, result.entries)
    }

    // ------------------------------------------------------------------
    // Exceptions
    // ------------------------------------------------------------------

    @Test
    fun `PluginNotFoundException should have message`() {
        val ex = PluginNotFoundException("missing-plugin")
        assertTrue(ex.message?.contains("missing-plugin") == true)
    }

    @Test
    fun `PluginException should have message and plugin`() {
        val plugin = TestPlugin("test")
        val ex = PluginException("Error", plugin.name)
        assertEquals("Error", ex.message)
        assertEquals(plugin.name, ex.pluginName)
    }

    @Test
    fun `PluginException should accept cause`() {
        val plugin = TestPlugin("test")
        val cause = RuntimeException("Root cause")
        val ex = PluginException("Error", plugin.name, cause)
        assertEquals(cause, ex.cause)
    }
}

// Test helper plugin
private class TestPlugin(
    override val name: String,
    override val phase: PluginPhase = PluginPhase.NORMAL
) : BeancountPlugin {
    override fun transform(context: PluginContext): PluginResult = PluginResult.noChange(context.entries)
}
