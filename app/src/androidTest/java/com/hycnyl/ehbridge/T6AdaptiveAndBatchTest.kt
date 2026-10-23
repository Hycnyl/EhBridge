package com.hycnyl.ehbridge

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Debug
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hycnyl.ehbridge.converter.BatchConverter
import com.hycnyl.ehbridge.metadata.SpiderInfoCodec
import com.hycnyl.ehbridge.model.*
import com.hycnyl.ehbridge.scanner.GalleryScanner
import com.hycnyl.ehbridge.storage.SafStore
import com.hycnyl.ehbridge.ui.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/** Exercises the real Android UI clock and accessibility tree, without replacing Compose's dispatcher. */
@RunWith(AndroidJUnit4::class)
class T6AdaptiveAndBatchTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as BridgeApp
    private val evidence get() = File(app.filesDir, "test-evidence").apply { mkdirs() }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun waitUntil(timeout: Long = 60_000, label: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (!runCatching { condition() }.getOrElse { if (it is IllegalStateException) false else throw it }) {
            if (SystemClock.elapsedRealtime() > until) fail("Timed out: $label")
            SystemClock.sleep(50)
        }
    }
    private fun note(value: String) { instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "T6: $value\n") }) }
    private fun recreate() {
        waitUntil(label = "stable Activity recreation") {
            if (activity.scenario.state != androidx.lifecycle.Lifecycle.State.RESUMED) return@waitUntil false
            activity.scenario.recreate()
            true
        }
        instrumentation.waitForIdleSync()
    }
    private fun fontScale(scale: Float) {
        shell("settings put system font_scale $scale")
        waitUntil(label = "font scale $scale") {
            var current = 0f
            activity.scenario.onActivity { current = it.resources.configuration.fontScale }
            kotlin.math.abs(current - scale) < .05f
        }
        instrumentation.waitForIdleSync()
    }
    private fun viewport(width: Int, height: Int) {
        // The AVD clamps forced pixel width to 640. Density sets the requested *dp* width.
        val dpi = if (width > 640) 80 else 160
        shell("wm density $dpi")
        shell("wm size ${width * dpi / 160}x${height * dpi / 160}")
        // wm triggers Activity recreation itself; wait for it rather than racing it.
        waitUntil(label = "window width $width dp") {
            if (activity.scenario.state != androidx.lifecycle.Lifecycle.State.RESUMED) return@waitUntil false
            var current = 0
            activity.scenario.onActivity { current = it.resources.configuration.screenWidthDp }
            kotlin.math.abs(current - width) < 10
        }
        instrumentation.waitForIdleSync()
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }
    private fun text(label: String) = find { it.text?.toString()?.trim()?.replace(Regex("\\s+"), " ") == label.trim().replace(Regex("\\s+"), " ") }
    private fun click(label: String, description: Boolean = false) {
        waitUntil(label = "click $label") {
            var node = if (description) find { it.contentDescription?.toString() == label } else text(label)
            while (node != null) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return@waitUntil true
                node = node.parent
            }
            false
        }
        instrumentation.waitForIdleSync()
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(evidence, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun tree(name: String) = DocumentsContract.buildTreeDocumentUri("com.hycnyl.ehbridge.fixtures", "root/$name")

    /** Focused rerun after storage changes; uses a fresh tiny fixture and the real SAF provider. */
    private fun verifyCurrentStorage() {
        val saf = SafStore(app)
        fun expect(code: Code, action: () -> Unit) {
            try { action(); fail("Unsafe roots were accepted: $code") }
            catch (failure: BridgeFailure) { assertEquals(code, failure.issue.code) }
        }
        val input = saf.root(tree("input"))
        expect(Code.ROOTS_OVERLAP) { saf.requireSeparateRoots(input, input) }
        val nested = saf.children(input).first { it.directory }
        expect(Code.ROOTS_OVERLAP) { saf.requireSeparateRoots(input, nested) }
        val alias = input.copy(id = android.net.Uri.parse(input.id).buildUpon().authority("different.provider").build().toString())
        expect(Code.ROOT_RELATION_UNKNOWN) { saf.requireSeparateRoots(input, alias) }
        fun external(id: String): Node {
            val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", id)
            return Node(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), "alias", true)
        }
        expect(Code.ROOTS_OVERLAP) { saf.requireSeparateRoots(external("home:Chosen"), external("primary:Documents/Chosen/child")) }

        val fixtures = File(app.filesDir, "fixtures")
        fun fresh(name: String): Pair<File, Node> {
            val folder = File(fixtures, name).apply { deleteRecursively(); mkdirs() }
            return folder to saf.root(tree(name))
        }
        val (a, aRoot) = fresh("verify-source")
        val (b, bRoot) = fresh("verify-packed")
        val (restored, restoredRoot) = fresh("verify-restored")
        val (copied, copiedRoot) = fresh("verify-copied")
        val name = "60333-Colon-safe"
        val source = File(a, name).apply { mkdir() }
        File(source, ".ehviewer").writeBytes(byteArrayOf())
        File(source, "thumb.foo").writeBytes(byteArrayOf())
        val xml = "<ComicInfo><Web>https://e-hentai.org/g/60333/0123456789/</Web><PageCount>36</PageCount></ComicInfo>".toByteArray()
        File(source, "ComicInfo.xml").writeBytes(xml)
        val page = byteArrayOf(0, 7, -1)
        File(source, "00000003.odd:extension").writeBytes(page)
        File(source, "00000008.any").writeBytes(byteArrayOf())
        fun run(from: Node, to: Node, target: Layout) = BatchConverter(saf).run(from, to, GalleryScanner(saf).scan(from, to, target, Control()), Control()).single()
        assertEquals(Outcome.CONVERTED, run(aRoot, bRoot, Layout.CBZ).outcome)
        assertEquals(Outcome.CONVERTED, run(bRoot, restoredRoot, Layout.LOOSE).outcome)
        val info = SpiderInfoCodec.decode(File(restored, "$name/.ehviewer").readBytes())
        assertEquals(36, info.pages); assertEquals("0123456789", info.token); assertTrue(info.pTokenMap.isEmpty())
        assertArrayEquals(xml, File(restored, "$name/ComicInfo.xml").readBytes())
        assertArrayEquals(page, File(restored, "$name/00000003.odd:extension").readBytes())
        val archive = File(b, "$name/60333.cbz").readBytes()
        assertEquals(Outcome.SKIPPED, run(aRoot, bRoot, Layout.CBZ).outcome)
        assertArrayEquals(archive, File(b, "$name/60333.cbz").readBytes())
        File(source, "extra").mkdir()
        File(source, "extra/nested.txt").writeText("preserve this")
        assertEquals(Outcome.COPIED, run(aRoot, copiedRoot, Layout.LOOSE).outcome)
        assertEquals("preserve this", File(copied, "$name/extra/nested.txt").readText())
        assertEquals(0, File(copied, "$name/.ehviewer").length())
        assertFalse(fixtures.walkTopDown().any { it.name.endsWith(".partial") || it.name == ".ehbridge-incomplete" })
        note("current storage: aliases rejected, colon suffix preserved, SAF pack/unpack/CBOR and recursive copy/no-overwrite passed")
    }

    @Test fun T6_windowsStateForegroundTaskAnd200Galleries() {
        if (InstrumentationRegistry.getArguments().getString("verifyOnly") == "true") { verifyCurrentStorage(); return }
        val repo = app.repository
        try {
            instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            shell("settings put system screen_off_timeout 1800000")
            repo.language("en")
            viewport(420, 940)
            fontScale(1f)
            recreate()
            waitUntil(label = "phone controls") { text("Input folder") != null }
            screenshot("phone-en.png")
            note("phone window and controls confirmed")
            viewport(740, 1000)
            screenshot("medium-en.png")
            viewport(1280, 900)
            waitUntil(label = "two columns") {
                val left = text("Input folder") ?: return@waitUntil false
                val right = text("Gallery overview") ?: return@waitUntil false
                val a = Rect(); val b = Rect()
                left.getBoundsInScreen(a); right.getBoundsInScreen(b)
                b.left > a.right
            }
            screenshot("tablet-en.png")
            note("medium and wide windows confirmed; two-column geometry verified")
            repo.language("zh-CN")
            recreate()
            waitUntil(label = "Chinese interface") { text("画廊总览") != null }
            screenshot("tablet-zh.png")
            viewport(420, 940)
            fontScale(2f)
            waitUntil(label = "200 percent font and reachable scan control") {
                val scan = text("扫描")
                if (scan != null && scan.isVisibleToUser) return@waitUntil true
                find { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                false
            }
            screenshot("phone-zh-font200.png")
            fontScale(1f)
            repo.language("en")
            recreate()
            note("phone, medium, tablet, Chinese/English and font scale checks passed")

            val fixtures = File(app.filesDir, "fixtures").apply { mkdirs() }
            val input = File(fixtures, "input").apply { mkdirs() }
            val output = File(fixtures, "output").apply { deleteRecursively(); mkdir() }
            val restore = File(fixtures, "restore").apply { deleteRecursively(); mkdir() }
            val xml = "<ComicInfo><PageCount>100</PageCount></ComicInfo>".toByteArray()
            for (gallery in 1..200) {
                val folder = File(input, "${60000 + gallery}-Synthetic 画廊 $gallery").apply { mkdir() }
                if (folder.listFiles()?.size == 103 && File(folder, "00000100.any").exists()) continue
                File(folder, ".ehviewer").writeText("opaque source state")
                File(folder, "ComicInfo.xml").writeBytes(xml)
                File(folder, "thumb.foo").writeText("synthetic cover")
                for (page in 1..100) File(folder, page.toString().padStart(8, '0') + ".any").writeText("fake page $gallery:$page")
            }
            File(input, "not-a-gallery.txt").writeText("must be skipped")
            assertEquals(20000, input.walkTopDown().count { it.extension == "any" })
            note("200 galleries / 20000 page files ready")
            repo.select(true, tree("input")); repo.select(false, tree("output")); repo.target(Layout.CBZ)
            val scanStart = SystemClock.elapsedRealtime()
            repo.scan()
            waitUntil(180_000, "SAF scan") { repo.state.value.phase != Phase.SCANNING }
            val scanMs = SystemClock.elapsedRealtime() - scanStart
            assertEquals(Phase.READY, repo.state.value.phase)
            assertNull(repo.state.value.error)
            assertEquals(201, repo.state.value.plans.size)
            note("SAF scan completed in $scanMs ms")
            val inputSelection = repo.state.value.input
            val planned = repo.state.value.plans
            recreate()
            assertEquals(inputSelection, repo.state.value.input)
            assertSame(planned, repo.state.value.plans)
            val pssBefore = Debug.getPss()
            val batchStart = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { repo.begin() }
            waitUntil(label = "foreground service start") { repo.batchStarts == 1 }
            assertEquals(Phase.RUNNING, repo.state.value.phase)
            recreate()
            assertEquals(inputSelection, repo.state.value.input)
            viewport(1280, 900)
            assertEquals(1, repo.batchStarts)
            val pssDuring = Debug.getPss()
            note("foreground batch running; Activity recreated and window resized")
            click("Options", description = true)
            click("Help & compatibility")
            click("Close")
            shell("input keyevent 26")
            waitUntil(600_000, "screen-off foreground batch") { repo.state.value.phase != Phase.RUNNING }
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            val batchMs = SystemClock.elapsedRealtime() - batchStart
            val pssAfter = Debug.getPss()
            note("foreground batch completed in $batchMs ms")
            assertEquals(Phase.FINISHED, repo.state.value.phase)
            assertEquals(1, repo.batchStarts)
            assertEquals(200, repo.state.value.results.values.count { it.outcome == Outcome.CONVERTED })
            assertEquals(1, repo.state.value.results.values.count { it.outcome == Outcome.SKIPPED })
            assertEquals(0, repo.state.value.results.values.count { it.outcome == Outcome.FAILED })
            assertEquals(200, output.listFiles()!!.size)
            for (folder in output.listFiles()!!) {
                assertEquals(3, folder.listFiles()!!.size)
                val archive = folder.listFiles()!!.single { it.extension == "cbz" }
                ZipFile(archive).use { zip ->
                    assertEquals(101, zip.size())
                    assertArrayEquals(File(folder, "ComicInfo.xml").readBytes(), zip.getInputStream(zip.getEntry("ComicInfo.xml")).readBytes())
                }
            }
            assertFalse(output.walkTopDown().any { it.name.endsWith(".partial") || it.name == ".ehbridge-incomplete" })
            assertEquals(20000, input.walkTopDown().count { it.extension == "any" })
            screenshot("tablet-results-en.png")

            val saf = SafStore(app)
            val bRoot = saf.root(tree("output"))
            val aRoot = saf.root(tree("restore"))
            val first = GalleryScanner(saf).scan(bRoot, aRoot, Layout.LOOSE, Control()).first()
            val result = BatchConverter(saf).run(bRoot, aRoot, listOf(first), Control()).single()
            assertEquals(Outcome.CONVERTED, result.outcome)
            val restored = File(restore, first.source.name)
            val info = SpiderInfoCodec.decode(File(restored, ".ehviewer").readBytes())
            assertEquals(100, info.pages); assertNull(info.token); assertTrue(info.pTokenMap.isEmpty())
            assertArrayEquals(File(input, "${first.source.name}/00000003.any").readBytes(), File(restored, "00000003.any").readBytes())
            note("SAF B to A round trip and persisted CBOR verification passed")
            File(evidence, "performance.json").writeText("""{
              "androidApi":30,"emulator":"x86_64, software TCG, 2 cores, 2048 MB",
              "galleries":200,"pageFiles":20000,"invalidRootItems":1,
              "scanMs":$scanMs,"batchMs":$batchMs,
              "pssBeforeKiB":$pssBefore,"pssDuringKiB":$pssDuring,"pssAfterKiB":$pssAfter,
              "converted":200,"skipped":1,"failed":0,"batchStarts":1,
              "remainingPartialFolders":0,"safRoundTrip":true,
              "rotationDuringBatch":true,"screenOffDuringBatch":true,
              "phoneWidthDp":420,"mediumWidthDp":740,"tabletWidthDp":1280,"fontScaleChecked":2.0
            }""".trimIndent())
        } finally {
            if (repo.state.value.busy) repo.cancel()
            shell("settings put system font_scale 1.0")
            repo.language("en")
            shell("input keyevent 224")
        }
    }
}
