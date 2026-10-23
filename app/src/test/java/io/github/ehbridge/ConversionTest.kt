package io.github.ehbridge

import io.github.ehbridge.converter.BatchConverter
import io.github.ehbridge.metadata.*
import io.github.ehbridge.model.*
import io.github.ehbridge.scanner.GalleryScanner
import io.github.ehbridge.storage.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class LocalStore(private val renameSupported: Boolean = true) : Store {
    fun node(file: File) = Node(file.absolutePath, file.name, file.isDirectory, file.length())
    override fun children(parent: Node) = File(parent.id).listFiles()!!.map(::node)
    override fun read(file: Node) = File(file.id).inputStream()
    override fun write(file: Node) = File(file.id).outputStream()
    override fun mkdir(parent: Node, name: String): Node = File(parent.id, name).let { check(it.mkdir()); node(it) }
    override fun create(parent: Node, name: String): Node = File(parent.id, name).let { check(it.createNewFile()); node(it) }
    override fun rename(node: Node, name: String): Node? = if (!renameSupported) null else {
        val target = File(File(node.id).parentFile, name)
        Files.move(File(node.id).toPath(), target.toPath())
        node(target)
    }
    override fun delete(node: Node) { check(File(node.id).deleteRecursively()) }
    override fun archive(node: Node, control: Control): Archive = object : Archive {
        override val zip = ZipFile(node.id)
        override fun close() = zip.close()
    }
    override fun requireSeparateRoots(input: Node, output: Node) {
        val a = File(input.id).canonicalFile.toPath()
        val b = File(output.id).canonicalFile.toPath()
        if (a.startsWith(b) || b.startsWith(a)) throw BridgeFailure(Issue(Code.ROOTS_OVERLAP))
    }
}

class ConversionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val store = LocalStore()
    private val page3 = byteArrayOf(0, 1, 2, -1)
    private val page8 = "not an image".toByteArray()
    private fun loose(root: File, name: String, xml: ByteArray = "<ComicInfo><PageCount>36</PageCount></ComicInfo>".toByteArray()): File {
        val folder = File(root, name).apply { mkdir() }
        File(folder, ".ehviewer").writeBytes("opaque non-CBOR source".toByteArray())
        File(folder, "ComicInfo.xml").writeBytes(xml)
        File(folder, "ThUmB.foo").writeBytes(byteArrayOf(9, 8, 7))
        File(folder, "00000003.jpg").writeBytes(page3)
        File(folder, "00000008.any-extension").writeBytes(page8)
        return folder
    }
    private fun zipped(root: File, name: String, outer: String = "<ComicInfo/>", inner: String = outer, entries: List<Pair<String, ByteArray>> = listOf("00000003.jpg" to page3, "00000008.foo" to page8)): File {
        val folder = File(root, name).apply { mkdir() }
        File(folder, "ComicInfo.xml").writeText(outer)
        File(folder, "thumb").writeBytes(byteArrayOf())
        ZipOutputStream(File(folder, "${name.substringBefore('-')}.CBZ").outputStream()).use { zip ->
            for ((entry, bytes) in listOf("ComicInfo.xml" to inner.toByteArray()) + entries) {
                zip.putNextEntry(ZipEntry(entry)); zip.write(bytes); zip.closeEntry()
            }
        }
        return folder
    }
    private fun plans(input: File, output: File, target: Layout, backing: Store = store) = GalleryScanner(backing).scan(store.node(input), store.node(output), target, Control())
    private fun run(input: File, output: File, target: Layout, backing: Store = store): List<Result> = BatchConverter(backing).run(store.node(input), store.node(output), plans(input, output, target, backing), Control())
    private fun snapshot(root: File) = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes().toList() }

    @Test fun T1_missingPagesPackPreservesNamesBytesAndXml() {
        val input = temporary.newFolder("t1-in")
        val output = temporary.newFolder("t1-out")
        val xml = "\uFEFF<ComicInfo><PageCount>99</PageCount><Title>测试 &amp; text</Title></ComicInfo>".toByteArray()
        val source = loose(input, "10001-甲- Unicode title", xml)
        File(source, "00000008.png").writeBytes(byteArrayOf())
        File(source, "00000009.odd:extension").writeBytes(page3)
        File(source, ".nomedia").writeText("junk retained only for same-layout copies")
        val before = snapshot(input)
        val plan = plans(input, output, Layout.CBZ).single()
        assertEquals(Operation.PACK, plan.operation)
        assertEquals(listOf(3, 8, 8, 9), plan.pages.map { it.number })
        assertEquals(Outcome.CONVERTED, run(input, output, Layout.CBZ).single().outcome)
        val result = File(output, source.name)
        assertEquals(setOf("10001.cbz", "ThUmB.foo", "ComicInfo.xml"), result.list()!!.toSet())
        assertArrayEquals(xml, File(result, "ComicInfo.xml").readBytes())
        ZipFile(File(result, "10001.cbz")).use { zip ->
            assertEquals(listOf("ComicInfo.xml", "00000003.jpg", "00000008.any-extension", "00000008.png", "00000009.odd:extension"), zip.entries().asSequence().map { it.name }.toList())
            assertArrayEquals(xml, zip.getInputStream(zip.getEntry("ComicInfo.xml")).readBytes())
            assertArrayEquals(page3, zip.getInputStream(zip.getEntry("00000003.jpg")).readBytes())
            assertArrayEquals(page8, zip.getInputStream(zip.getEntry("00000008.any-extension")).readBytes())
            assertTrue(zip.entries().asSequence().all { it.method == ZipEntry.DEFLATED })
        }
        assertEquals(before, snapshot(input))
    }

    @Test fun T2_minimalSpiderInfoTokenFallbackAndRealCbor() {
        val input = temporary.newFolder("t2-in")
        val output = temporary.newFolder("t2-out")
        val token = "a12345678b"
        fun xml(gid: Int, access: String = token, count: Int = 36) = "<ComicInfo><Web>https://exhentai.org/g/$gid/$access/?a=1&amp;b=2</Web><PageCount>$count</PageCount></ComicInfo>"
        zipped(input, "20001-valid", xml(20001, token.uppercase()))
        zipped(input, "20002-no-token", "<ComicInfo><PageCount>36</PageCount></ComicInfo>")
        zipped(input, "20003-malformed", "not XML")
        zipped(input, "20004-fallback", "<ComicInfo/>", xml(20004))
        zipped(input, "20005-conflict", xml(20005), xml(20005, "b12345678a"))
        zipped(input, "20006-wrong-gid", xml(99999), "<ComicInfo><PageCount>3</PageCount></ComicInfo>")
        zipped(input, "20007-xxe", "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><ComicInfo><Web>&e;</Web></ComicInfo>")
        zipped(input, "20008-too-many", "<ComicInfo><PageCount>100001</PageCount></ComicInfo>")
        val results = run(input, output, Layout.LOOSE).associateBy { it.name }
        for ((name, result) in results) {
            if (name.startsWith("20008")) continue
            assertEquals(name, Outcome.CONVERTED, result.outcome)
            val folder = File(output, name)
            val bytes = File(folder, ".ehviewer").readBytes()
            assertFalse(bytes.toString(Charsets.UTF_8).trimStart().startsWith("{"))
            val info = SpiderInfoCodec.decode(bytes)
            assertEquals(name.substringBefore('-').toLong(), info.gid)
            assertTrue(info.pTokenMap.isEmpty())
            assertEquals(0, info.startPage); assertEquals(-1, info.previewPages); assertEquals(-1, info.previewPerPage); assertNull(info.upgradeFrom)
            assertEquals(if (name.startsWith("20001") || name.startsWith("20004") || name.startsWith("20005")) token else null, info.token)
            assertEquals(name, if (name.startsWith("20001") || name.startsWith("20002") || name.startsWith("20004") || name.startsWith("20005") || name.startsWith("20006")) 36 else 8, info.pages)
            assertArrayEquals(File(input, "$name/ComicInfo.xml").readBytes(), File(folder, "ComicInfo.xml").readBytes())
            assertArrayEquals(page3, File(folder, "00000003.jpg").readBytes())
            assertArrayEquals(page8, File(folder, "00000008.foo").readBytes())
        }
        assertTrue(results["20005-conflict"]!!.warnings.any { it.code == Code.TOKEN_SOURCE_CONFLICT })
        assertTrue(results["20004-fallback"]!!.warnings.any { it.code == Code.COMICINFO_DIFFERENT })
        assertTrue(results["20003-malformed"]!!.warnings.any { it.code == Code.TOKEN_UNAVAILABLE })
        assertEquals(Code.UNSAFE_PAGE_INDEX, results["20008-too-many"]!!.issues.single().code)
        assertFalse(File(output, "20008-too-many").exists())
        val original = SpiderInfo(31L, token, 36, hashMapOf(0 to "0123456789", 35 to "9876543210"), previewPerPage = 20)
        assertEquals(original, SpiderInfoCodec.decode(SpiderInfoCodec.encode(original)))
        assertNull(ComicInfoMetadata.parse("<Web>https://evil.example/g/31/$token/</Web>".toByteArray(), 31).token)
        assertEquals(token, ComicInfoMetadata.parse("<Web>/g/31/$token/</Web>".toByteArray(), 31).token)
    }

    @Test fun T3_sameFormatCopiesAllBytesAndNestedDirectories() {
        val aInput = temporary.newFolder("t3-a-in")
        val aOutput = temporary.newFolder("t3-a-out")
        val a = loose(aInput, "30001-A", "malformed XML is opaque".toByteArray())
        File(a, ".nomedia").writeText("system junk")
        File(a, "extra/inside/empty").mkdirs()
        File(a, "extra/inside/data.bin").writeBytes(byteArrayOf(4, 3, 2))
        assertEquals(Outcome.COPIED, run(aInput, aOutput, Layout.LOOSE).single().outcome)
        assertEquals(snapshot(aInput), snapshot(aOutput))
        assertTrue(File(aOutput, "30001-A/extra/inside/empty").isDirectory)
        val bInput = temporary.newFolder("t3-b-in")
        val bOutput = temporary.newFolder("t3-b-out")
        val b = zipped(bInput, "30002-B", "invalid outer XML", "different inner XML", listOf("00000003.foo" to page3, ".DS_Store" to page8))
        File(b, "nested/empty").mkdirs()
        File(b, "nested/random.bin").writeText("preserved")
        assertEquals(Outcome.COPIED, run(bInput, bOutput, Layout.CBZ).single().outcome)
        assertEquals(snapshot(bInput), snapshot(bOutput))
    }

    @Test fun T4_mixedRootClassifiesAndExplainsEachInvalidItem() {
        val input = temporary.newFolder("t4-in")
        val output = temporary.newFolder("t4-out")
        loose(input, "40001-A")
        zipped(input, "40002-B", "not parseable XML")
        loose(input, "40003-missing").resolve("ComicInfo.xml").delete()
        loose(input, "40004-unknown").resolve("notes.txt").writeText("do not lose this")
        val mixed = loose(input, "40005-ambiguous")
        File(mixed, "40005.cbz").writeText("archive")
        loose(input, "40006-cover").resolve("thumb.webp").writeText("duplicate")
        val wrong = zipped(input, "40007-wrong")
        File(wrong, "40007.CBZ").renameTo(File(wrong, "999.CBZ"))
        loose(input, "40008-occupied")
        File(output, "40008-occupied").mkdir()
        loose(input, "40009-empty-extra").resolve("empty.txt").createNewFile()
        loose(input, "40010-extreme").resolve("99999999999999999999999999.foo").writeText("a")
        File(input, "bad title").mkdir()
        File(input, "README.txt").writeText("root file")
        File(input, ".nomedia").createNewFile()
        val results = plans(input, output, Layout.CBZ).associateBy { it.source.name }
        assertEquals(Operation.PACK, results["40001-A"]!!.operation)
        assertEquals(Operation.COPY, results["40002-B"]!!.operation)
        assertEquals(Operation.PACK, results["40009-empty-extra"]!!.operation)
        val expected = mapOf("40003-missing" to Code.MISSING_REQUIRED_FILE, "40004-unknown" to Code.UNMAPPED_EXTRA_DATA, "40005-ambiguous" to Code.LAYOUT_AMBIGUOUS, "40006-cover" to Code.LAYOUT_AMBIGUOUS, "40007-wrong" to Code.GID_MISMATCH, "40008-occupied" to Code.OUTPUT_EXISTS, "40010-extreme" to Code.UNSAFE_PAGE_INDEX, "bad title" to Code.INVALID_GALLERY_ITEM, "README.txt" to Code.INVALID_GALLERY_ITEM)
        expected.forEach { (name, code) -> assertEquals(name, code, results[name]!!.issues.single().code); assertEquals(Operation.SKIP, results[name]!!.operation) }
        assertFalse(results.containsKey(".nomedia"))
        assertEquals("notes.txt", results["40004-unknown"]!!.issues.single().file)
        val executed = run(input, output, Layout.CBZ)
        assertEquals(3, executed.count { it.outcome == Outcome.COPIED || it.outcome == Outcome.CONVERTED })
        val duplicates = temporary.newFolder("t4-duplicate-input")
        val duplicateOutput = temporary.newFolder("t4-duplicate-output")
        loose(duplicates, "41001-first"); loose(duplicates, "41001-second")
        val duplicateStore = object : Store by store {
            override fun children(parent: Node) = store.children(parent).map { if (parent.id == duplicates.absolutePath) it.copy(name = "41001-shared") else it }
        }
        val duplicateResults = run(duplicates, duplicateOutput, Layout.CBZ, duplicateStore)
        assertEquals(listOf(Outcome.CONVERTED, Outcome.SKIPPED), duplicateResults.map { it.outcome })
        assertEquals(2, duplicateResults.associateBy { it.sourceId }.size)
    }

    @Test fun T5_zipSafetyConflictsWriteFailureCancellationAndIsolation() {
        val input = temporary.newFolder("t5-in")
        val output = temporary.newFolder("t5-out")
        zipped(input, "50001-traversal", entries = listOf("../escape.jpg" to page3))
        zipped(input, "50002-nested", entries = listOf("sub/00000003.jpg" to page3))
        zipped(input, "50005-drive", entries = listOf("C:escape.jpg" to page3))
        val duplicate = zipped(input, "50003-duplicate", entries = listOf("00000001.jpg" to page3, "00000002.jpg" to page8))
        val zip = File(duplicate, "50003.CBZ")
        val patched = zip.readBytes().toString(Charsets.ISO_8859_1).replace("00000002.jpg", "00000001.jpg").toByteArray(Charsets.ISO_8859_1)
        zip.writeBytes(patched)
        val encrypted = zipped(input, "50004-encrypted")
        val enc = File(encrypted, "50004.CBZ")
        val encBytes = enc.readBytes()
        for (i in 0 until encBytes.size - 10) if (encBytes[i] == 0x50.toByte() && encBytes[i+1] == 0x4b.toByte()) {
            if (encBytes[i+2] == 1.toByte() && encBytes[i+3] == 2.toByte()) encBytes[i+8] = (encBytes[i+8].toInt() or 1).toByte()
            if (encBytes[i+2] == 3.toByte() && encBytes[i+3] == 4.toByte()) encBytes[i+6] = (encBytes[i+6].toInt() or 1).toByte()
        }
        enc.writeBytes(encBytes)
        plans(input, output, Layout.LOOSE).forEach { assertEquals(it.source.name, Code.ARCHIVE_UNSAFE, it.issues.single().code) }
        val cleanIn = temporary.newFolder("t5-clean-in")
        val cleanOut = temporary.newFolder("t5-clean-out")
        loose(cleanIn, "51001-fail"); loose(cleanIn, "51002-continue")
        val broken = object : Store by store {
            override fun write(file: Node): java.io.OutputStream {
                if (file.name == "51001.cbz") throw IOException("simulated disk full")
                return store.write(file)
            }
        }
        val before = snapshot(cleanIn)
        val failures = run(cleanIn, cleanOut, Layout.CBZ, broken)
        assertEquals(listOf(Outcome.FAILED, Outcome.CONVERTED), failures.map { it.outcome })
        assertEquals(before, snapshot(cleanIn))
        assertEquals(listOf("51002-continue"), cleanOut.list()!!.toList())
        val original = snapshot(cleanOut)
        assertEquals(Outcome.SKIPPED, run(cleanIn, cleanOut, Layout.CBZ).last().outcome)
        original.forEach { (name, bytes) -> assertEquals(bytes, File(cleanOut, name).readBytes().toList()) }
        val cancelOut = temporary.newFolder("t5-cancel-out")
        val control = Control()
        val cancelled = BatchConverter(store).run(store.node(cleanIn), store.node(cancelOut), plans(cleanIn, cancelOut, Layout.CBZ), control, onProgress = { if (it.filesDone == 1) control.cancel() })
        assertEquals(1, cancelled.size)
        assertEquals(Code.CANCELLED, cancelled.single().issues.single().code)
        assertTrue(cancelOut.list()!!.isEmpty())
        assertEquals(before, snapshot(cleanIn))
        try { plans(cleanIn, File(cleanIn, "child").apply { mkdir() }, Layout.CBZ); fail("Nested output accepted") }
        catch (e: BridgeFailure) { assertEquals(Code.ROOTS_OVERLAP, e.issue.code) }
        val fallbackOut = temporary.newFolder("t5-fallback")
        val fallbackStore = LocalStore(renameSupported = false)
        assertTrue(run(cleanIn, fallbackOut, Layout.CBZ, fallbackStore).all { it.outcome == Outcome.CONVERTED || it.outcome == Outcome.SKIPPED })
        assertFalse(fallbackOut.walkTopDown().any { it.name == ".ehbridge-incomplete" || it.name.endsWith(".partial") })
    }
}
