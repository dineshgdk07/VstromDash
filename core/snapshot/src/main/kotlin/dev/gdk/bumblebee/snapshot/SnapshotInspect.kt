package dev.gdk.bumblebee.snapshot

sealed class SnapshotVerdict {
    data object Ok : SnapshotVerdict()
    data class Rejected(val message: String, val log: List<String>) : SnapshotVerdict()
}

fun inspectManifest(
    manifest: SnapshotManifest,
    payloadSha256: String,
    actualCounts: Map<String, Int>,
): SnapshotVerdict {
    if (manifest.magic != SnapshotFormat.MAGIC) {
        return SnapshotVerdict.Rejected("This file is not a Bumblebee snapshot.", emptyList())
    }
    if (manifest.format > SnapshotFormat.FORMAT || manifest.schema > SnapshotFormat.APP_SCHEMA) {
        return SnapshotVerdict.Rejected("This file was made by a newer app.", emptyList())
    }
    if (manifest.schema < SnapshotFormat.APP_SCHEMA) {
        return SnapshotVerdict.Rejected(
            "This file was made by an older app. Pack it again with this version.",
            emptyList(),
        )
    }
    if (manifest.sha256 != payloadSha256) {
        return SnapshotVerdict.Rejected("The file is damaged. The checksum does not match.", emptyList())
    }
    for (section in SnapshotSections.ORDER + SnapshotSections.PREFS) {
        val expected = manifest.counts[section]
        val actual = actualCounts[section]
        if (expected != actual) {
            return SnapshotVerdict.Rejected(
                "The file is damaged. The $section count does not match.",
                listOf("$section manifest $expected actual $actual"),
            )
        }
    }
    return SnapshotVerdict.Ok
}
