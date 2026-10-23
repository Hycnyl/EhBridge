package com.hycnyl.ehbridge.scanner

import com.hycnyl.ehbridge.model.*
import com.hycnyl.ehbridge.storage.*

class GalleryScanner(private val store: Store) {
    fun scan(input: Node, output: Node, target: Layout, control: Control, onItem: (Int) -> Unit = {}): List<Plan> {
        store.requireSeparateRoots(input, output)
        val occupied = store.children(output).mapTo(HashSet()) { it.name }
        val gids = HashSet<Long>()
        val names = HashSet<String>()
        return store.children(input).sortedBy { it.name }.filterNot { !it.directory && Names.junk(it.name) }.mapIndexed { index, item ->
            control.check()
            val plan = inspect(item, target, control)
            val duplicate = !names.add(item.name) || (plan.gid != null && !gids.add(plan.gid))
            onItem(index + 1)
            if (plan.issues.isNotEmpty()) plan
            else if (item.name in occupied || duplicate) plan.copy(operation = Operation.SKIP, issues = listOf(Issue(Code.OUTPUT_EXISTS, item.name)))
            else plan
        }
    }

    fun inspect(folder: Node, target: Layout, control: Control): Plan {
        val gid = Names.gid(folder.name)
        if (!folder.directory || gid == null) return Plan(folder, issues = listOf(Issue(Code.INVALID_GALLERY_ITEM)))
        var layout = Layout.INVALID
        return try {
            control.check()
            val children = store.children(folder)
            if (children.map { it.name }.distinct().size != children.size) throw BridgeFailure(Issue(Code.LAYOUT_AMBIGUOUS))
            val xml = children.firstOrNull { it.name == "ComicInfo.xml" && !it.directory }
            val covers = children.filter { !it.directory && Names.cover(it.name) }
            if (covers.size > 1) throw BridgeFailure(Issue(Code.LAYOUT_AMBIGUOUS, covers.joinToString { it.name }))
            val cover = covers.singleOrNull()
            val spider = children.firstOrNull { it.name == ".ehviewer" && !it.directory }
            val archives = children.filter { !it.directory && it.name.endsWith(".cbz", true) }
            val pages = children.filterNot { it.directory }.mapNotNull { Names.page(it.name, it) }.sortedWith(Names.pageOrder)
            if (archives.size > 1 || (archives.isNotEmpty() && (spider != null || pages.isNotEmpty())))
                throw BridgeFailure(Issue(Code.LAYOUT_AMBIGUOUS, archives.joinToString { it.name }))
            val archive = archives.singleOrNull()
            val actualPages: List<Page>
            if (archive != null) {
                if (archive.name.substringBeforeLast('.').toLongOrNull() != gid) throw BridgeFailure(Issue(Code.GID_MISMATCH, archive.name))
                layout = Layout.CBZ
                actualPages = try { store.archive(archive, control).use { ZipInspector.inspect(it.zip, control) } }
                catch (e: BridgeFailure) { throw e }
                catch (e: java.util.concurrent.CancellationException) { throw e }
                catch (_: java.util.zip.ZipException) { throw BridgeFailure(Issue(Code.ARCHIVE_UNSAFE, archive.name)) }
                catch (_: Exception) { throw BridgeFailure(Issue(Code.READ_WRITE_FAILURE, archive.name)) }
            } else {
                if (pages.isEmpty()) throw BridgeFailure(Issue(Code.NO_PAGE_FILES))
                layout = Layout.LOOSE
                actualPages = pages
            }
            // Same-layout copying preserves extra files and subdirectories in their entirety.
            if (layout != target) {
                val business = buildSet {
                    xml?.let { add(it.name) }; cover?.let { add(it.name) }
                    if (archive != null) add(archive.name) else { spider?.let { add(it.name) }; addAll(pages.map { it.name }) }
                }
                children.firstOrNull { it.name !in business && !(!it.directory && (Names.junk(it.name) || it.size == 0L)) }?.let {
                    throw BridgeFailure(Issue(Code.UNMAPPED_EXTRA_DATA, it.name))
                }
            }
            for (required in listOfNotNull(xml, cover, if (layout == Layout.LOOSE) spider else null)) {
                control.check()
                atFile(required.name) { store.read(required).use { /* openability only; no metadata/media validation */ } }
            }
            val warnings = mutableListOf<Issue>()
            if (xml == null) warnings.add(Issue(Code.MISSING_COMICINFO, "ComicInfo.xml"))
            if (cover == null) warnings.add(Issue(Code.MISSING_COVER, "thumb / thumb.*"))
            if (layout == Layout.LOOSE && spider == null) warnings.add(Issue(Code.MISSING_EHVIEWER, ".ehviewer"))
            if (layout != target && layout == Layout.LOOSE && spider != null) warnings.add(Issue(Code.SOURCE_STATE_NOT_TRANSFERRED, ".ehviewer"))
            if (actualPages.map { it.number }.distinct().size < actualPages.maxOf { it.number }) warnings.add(Issue(Code.GALLERY_INCOMPLETE))
            Plan(folder, gid, layout, if (layout == target) Operation.COPY else if (target == Layout.CBZ) Operation.PACK else Operation.UNPACK,
                actualPages, xml, cover, archive, warnings = warnings)
        } catch (e: BridgeFailure) {
            Plan(folder, gid, layout, issues = listOf(e.issue))
        } catch (e: java.util.concurrent.CancellationException) { throw e }
        catch (_: Exception) { Plan(folder, gid, layout, issues = listOf(Issue(Code.READ_WRITE_FAILURE))) }
    }
}
