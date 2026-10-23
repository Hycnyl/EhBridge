package com.hycnyl.ehbridge.converter

import com.hycnyl.ehbridge.metadata.*
import com.hycnyl.ehbridge.model.*
import com.hycnyl.ehbridge.scanner.*
import com.hycnyl.ehbridge.storage.*
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

class BatchConverter(private val store: Store) {
    fun run(input: Node, output: Node, plans: List<Plan>, control: Control, onProgress: (Progress) -> Unit = {}, onResult: (Result) -> Unit = {}): List<Result> {
        store.requireSeparateRoots(input, output)
        val results = mutableListOf<Result>()
        val seenNames = HashSet<String>()
        val seenGids = HashSet<Long>()
        for ((index, plan) in plans.withIndex()) {
            if (control.isCancelled) break
            var files = 0
            fun progress() = onProgress(Progress(index, plans.size, plan.source.name, files, if (plan.operation == Operation.COPY) 0 else plan.pages.size))
            progress()
            val result = (if (!seenNames.add(plan.source.name) || (plan.gid != null && !seenGids.add(plan.gid)))
                Result(plan.source.name, Outcome.SKIPPED, listOf(Issue(Code.OUTPUT_EXISTS, plan.source.name)))
            else execute(plan, output, control) { files++; progress() }).copy(sourceId = plan.source.id)
            results.add(result)
            onResult(result)
            onProgress(Progress(index + 1, plans.size, plan.source.name, files, if (plan.operation == Operation.COPY) 0 else plan.pages.size))
        }
        return results
    }

    private fun execute(plan: Plan, output: Node, control: Control, onFile: () -> Unit): Result {
        if (plan.operation == Operation.SKIP) return Result(plan.source.name, Outcome.SKIPPED, plan.issues, plan.warnings)
        val warnings = plan.warnings.toMutableList()
        val stage = StagedOutput(store, output, plan.source.name)
        var result: Result
        try {
            control.check()
            // Recheck the source shape at execution time. Existing files are never rewritten by repairs.
            val target = if (plan.operation == Operation.PACK) Layout.CBZ else if (plan.operation == Operation.UNPACK) Layout.LOOSE else plan.layout
            val fresh = GalleryScanner(store).inspect(plan.source, target, control)
            if (fresh.issues.isNotEmpty()) throw BridgeFailure(fresh.issues.first())
            if (fresh.layout != plan.layout || fresh.pages.map { it.name } != plan.pages.map { it.name } || fresh.cover?.name != plan.cover?.name || fresh.xml?.name != plan.xml?.name)
                throw BridgeFailure(Issue(Code.SOURCE_CHANGED))
            val destination = atFile(plan.source.name) { stage.begin() }
            when (plan.operation) {
                Operation.COPY -> {
                    store.copyTree(plan.source, destination, control) { onFile() }
                    warnings.addAll(repairCopiedXml(fresh, destination, control))
                }
                Operation.PACK -> warnings.addAll(pack(fresh, destination, control, onFile))
                Operation.UNPACK -> warnings.addAll(unpack(fresh, destination, control, onFile))
                Operation.SKIP -> error("unreachable")
            }
            atFile(plan.source.name) { stage.commit(control) }
            result = Result(plan.source.name, if (plan.operation == Operation.COPY) Outcome.COPIED else Outcome.CONVERTED, warnings = warnings)
        } catch (_: CancellationException) {
            result = Result(plan.source.name, Outcome.SKIPPED, listOf(Issue(Code.CANCELLED)), warnings)
        } catch (e: BridgeFailure) {
            result = Result(plan.source.name, if (e.issue.code == Code.OUTPUT_EXISTS) Outcome.SKIPPED else Outcome.FAILED, listOf(e.issue), warnings)
        } catch (_: Exception) {
            result = Result(plan.source.name, Outcome.FAILED, listOf(Issue(Code.READ_WRITE_FAILURE)), warnings)
        }
        return result.copy(warnings = (result.warnings + stage.cleanup()).distinct())
    }

    private fun pack(plan: Plan, destination: Node, control: Control, onFile: () -> Unit): List<Issue> {
        plan.cover?.let { store.copyFile(it, store.create(destination, it.name), control) }
        val xml = store.create(destination, "ComicInfo.xml")
        val cbz = store.create(destination, "${plan.gid}.cbz")
        atFile(cbz.name) {
            ZipOutputStream(store.write(cbz).buffered(BUFFER_SIZE)).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)
                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                // A single source read feeds both XML copies, preserving equality even for a changing provider.
                (plan.xml?.let(store::read) ?: ByteArrayInputStream(generatedXml(plan))).use { input ->
                    store.write(xml).use { external ->
                        val tee = object : OutputStream() {
                            override fun write(value: Int) { external.write(value); zip.write(value) }
                            override fun write(bytes: ByteArray, offset: Int, count: Int) { external.write(bytes, offset, count); zip.write(bytes, offset, count) }
                        }
                        copyBytes(input, tee, control)
                    }
                }
                zip.closeEntry()
                for (page in plan.pages) {
                    control.check()
                    zip.putNextEntry(ZipEntry(page.name))
                    atFile(page.name) { store.read(page.node!!).use { copyBytes(it, zip, control) } }
                    zip.closeEntry()
                    onFile()
                }
            }
            store.archive(cbz, control).use { created ->
                val actual = ZipInspector.inspect(created.zip, control)
                if (actual.map { it.name } != plan.pages.map { it.name } || created.zip.size() != plan.pages.size + 1)
                    throw BridgeFailure(Issue(Code.ARCHIVE_UNSAFE, cbz.name))
            }
        }
        return if (plan.xml == null) listOf(Issue(Code.METADATA_REPAIRED, "ComicInfo.xml")) else emptyList()
    }

    private fun unpack(plan: Plan, destination: Node, control: Control, onFile: () -> Unit): List<Issue> {
        val warnings = mutableListOf<Issue>()
        plan.cover?.let { store.copyFile(it, store.create(destination, it.name), control) }
        store.archive(plan.archive!!, control).use { source ->
            val pages = ZipInspector.inspect(source.zip, control)
            if (pages.map { it.name } != plan.pages.map { it.name }) throw BridgeFailure(Issue(Code.SOURCE_CHANGED, plan.archive.name))
            val copiedXml = store.create(destination, "ComicInfo.xml")
            writeBestXml(plan, copiedXml, source.zip, control)
            if (plan.xml == null) warnings.add(Issue(Code.METADATA_REPAIRED, "ComicInfo.xml"))
            val external = runCatching { store.read(copiedXml).use { boundedXml(it, control) } }.getOrElse { if (it is CancellationException) throw it else null }
            val internal = runCatching { source.zip.getEntry("ComicInfo.xml")?.let { entry -> source.zip.getInputStream(entry).use { boundedXml(it, control) } } }.getOrElse { if (it is CancellationException) throw it else null }
            if (external != null && internal != null && !external.contentEquals(internal)) warnings.add(Issue(Code.COMICINFO_DIFFERENT, "ComicInfo.xml"))
            val outer = ComicInfoMetadata.parse(external, plan.gid!!)
            val inner = ComicInfoMetadata.parse(internal, plan.gid)
            val token = outer.token ?: inner.token
            if (token == null) warnings.add(Issue(Code.TOKEN_UNAVAILABLE))
            if (outer.token != null && inner.token != null && outer.token != inner.token) warnings.add(Issue(Code.TOKEN_SOURCE_CONFLICT))
            if (!outer.readable) warnings.add(Issue(Code.XML_METADATA_LIMITED, "ComicInfo.xml"))
            val count = maxOf(pages.maxOf { it.number }, outer.pages ?: inner.pages ?: 0)
            if (count !in 1..100_000) throw BridgeFailure(Issue(Code.UNSAFE_PAGE_INDEX, "ComicInfo.xml"))
            if (pages.map { it.number }.distinct().size < count && warnings.none { it.code == Code.GALLERY_INCOMPLETE }) warnings.add(Issue(Code.GALLERY_INCOMPLETE))
            for (page in pages) {
                control.check()
                val file = store.create(destination, page.name)
                copyArchiveEntry(source.zip, page.name, file, control)
                onFile()
            }
            try {
                val info = SpiderInfo(plan.gid, token, count)
                val encoded = SpiderInfoCodec.encode(info)
                val file = store.create(destination, ".ehviewer")
                store.write(file).use { it.write(encoded) }
                val persisted = store.read(file).use { boundedXml(it, control) } ?: error("Unreadable SpiderInfo")
                check(SpiderInfoCodec.decode(persisted) == info)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw BridgeFailure(Issue(Code.SPIDER_CODEC_ERROR, ".ehviewer")) }
        }
        return warnings
    }

    /** Add a missing sidecar to the copied destination, leaving every copied source byte intact. */
    private fun repairCopiedXml(plan: Plan, destination: Node, control: Control): List<Issue> {
        if (plan.xml != null) return emptyList()
        val xml = store.create(destination, "ComicInfo.xml")
        if (plan.archive != null) store.archive(plan.archive, control).use { source -> writeBestXml(plan, xml, source.zip, control) }
        else writeBestXml(plan, xml, null, control)
        return listOf(Issue(Code.METADATA_REPAIRED, "ComicInfo.xml"))
    }

    private fun generatedXml(plan: Plan): ByteArray =
        ComicInfoMetadata.fromFolder(plan.source.name, plan.gid!!, plan.pages.maxOf { it.number })

    private fun writeBestXml(plan: Plan, destination: Node, zip: ZipFile?, control: Control) {
        when {
            plan.xml != null -> store.copyFile(plan.xml, destination, control)
            zip?.getEntry("ComicInfo.xml") != null -> copyArchiveEntry(zip, "ComicInfo.xml", destination, control)
            else -> { control.check(); store.write(destination).use { it.write(generatedXml(plan)) } }
        }
    }

    private fun copyArchiveEntry(zip: ZipFile, name: String, destination: Node, control: Control) = atFile(name) {
        val entry = zip.getEntry(name) ?: throw BridgeFailure(Issue(Code.SOURCE_CHANGED, name))
        val crc = CRC32()
        zip.getInputStream(entry).use { raw ->
            CheckedInputStream(raw, crc).use { input ->
                store.write(destination).use { output ->
                    val bytes = copyBytes(input, output, control, entry.size)
                    if (bytes != entry.size || crc.value != entry.crc) throw BridgeFailure(Issue(Code.ARCHIVE_UNSAFE, name))
                }
            }
        }
    }

    private fun boundedXml(input: InputStream, control: Control): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            control.check()
            val read = input.read(buffer, 0, minOf(buffer.size, ComicInfoMetadata.MAX_XML + 1 - output.size()))
            if (read < 0) return output.toByteArray()
            output.write(buffer, 0, read)
            if (output.size() > ComicInfoMetadata.MAX_XML) return null
        }
    }
}
