package io.github.dryingday

import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.big_shoulders_display_bold
import io.github.dryingday.shared.resources.big_shoulders_display_extrabold
import io.github.dryingday.shared.resources.public_sans_bold
import io.github.dryingday.shared.resources.public_sans_regular
import io.github.dryingday.shared.resources.public_sans_semibold
import org.jetbrains.compose.resources.FontResource
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// On the JVM host the resources reader needs Android's Log, so this reads the files from the
// module directory, as ReferenceParityTest does. Referencing the Res.font accessors means the
// test fails to compile if one is missing. Bytes and hashes are docs/bolts/09-washing-line.md's.
class FontsTest {
    private class Asset(val file: String, val bytes: Int, val sha256: String)

    private val fonts: List<Pair<FontResource, Asset>> =
        listOf(
            Res.font.big_shoulders_display_bold to
                Asset(
                    "font/big_shoulders_display_bold.ttf",
                    89_444,
                    "8bbf6cc1a50aa41d475294ca88d111869c928d432df5d3f49ae0c037155d0bb5",
                ),
            Res.font.big_shoulders_display_extrabold to
                Asset(
                    "font/big_shoulders_display_extrabold.ttf",
                    89_852,
                    "ca975779c90d08a721fee703cce970a6426a41a4bcb1d51b01e146456ce20389",
                ),
            Res.font.public_sans_regular to
                Asset(
                    "font/public_sans_regular.ttf",
                    84_836,
                    "b577e9bc9887284e90aae5ad0699689ce36b5cd96207efbec68f77f8aed88379",
                ),
            Res.font.public_sans_semibold to
                Asset(
                    "font/public_sans_semibold.ttf",
                    84_412,
                    "9f537d607dc78450841dc31c401e3c4ba7a0ba7217e8b34c1be684a983806399",
                ),
            Res.font.public_sans_bold to
                Asset(
                    "font/public_sans_bold.ttf",
                    84_744,
                    "bddd96a0bcd49b292524b4beff57d770ce97482fb3f84b788959422e06b863a6",
                ),
        )

    private val licences =
        listOf(
            Asset(
                "files/licenses/ofl_big_shoulders.txt",
                4_396,
                "338f9c050f19daeda1d597243faf79f3a3d437c338af58cb7047617d0ce08771",
            ),
            Asset(
                "files/licenses/ofl_public_sans.txt",
                4_390,
                "157a9e77f7580246e97c769490e2e977ae94399f9d30f4556015c41fe8c28bac",
            ),
        )

    private fun read(asset: Asset): ByteArray = File("src/commonMain/composeResources/${asset.file}").readBytes()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        it.toUByte().toString(HEX).padStart(2, '0')
    }

    @Test
    fun eachOfTheFiveFontsIsPresentAsTrueTypeAndMatchesTheSpec() {
        assertEquals(FONT_COUNT, fonts.size)
        for ((_, asset) in fonts) {
            val bytes = read(asset)
            assertEquals(asset.bytes, bytes.size, "${asset.file} size")
            assertEquals(asset.sha256, sha256(bytes), "${asset.file} hash")
            assertEquals(
                TRUETYPE_MAGIC,
                bytes.take(TRUETYPE_MAGIC.size).map {
                    it.toInt() and BYTE_MASK
                },
                "${asset.file} is not TrueType",
            )
        }
    }

    @Test
    fun eachFamilysLicenceIsPresentAndMatchesTheSpec() {
        for (asset in licences) {
            val bytes = read(asset)
            assertEquals(asset.bytes, bytes.size, "${asset.file} size")
            assertEquals(asset.sha256, sha256(bytes), "${asset.file} hash")
            assertTrue("SIL OPEN FONT LICENSE Version 1.1" in bytes.decodeToString(), "${asset.file} is not the OFL")
        }
    }

    private companion object {
        const val FONT_COUNT = 5
        const val HEX = 16
        const val BYTE_MASK = 0xFF
        val TRUETYPE_MAGIC = listOf(0x00, 0x01, 0x00, 0x00)
    }
}
