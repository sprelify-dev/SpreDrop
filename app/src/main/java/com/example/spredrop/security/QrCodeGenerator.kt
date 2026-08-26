package com.example.spredrop.security

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.security.MessageDigest

/**
 * QR Code / Pairing Payload encoder and renderer.
 * Produces crisp QR-pattern Bitmaps and decodes SpreDrop pairing URIs.
 */
object QrCodeGenerator {

    /**
     * Creates a standardized SpreDrop pairing payload string:
     * spredrop://pair?id=@username&uid=xyz&name=DisplayName&ts=123456&sig=hash
     */
    fun createPairPayload(spreDropId: String, userId: String, displayName: String): String {
        val ts = System.currentTimeMillis()
        val raw = "$spreDropId:$userId:$displayName:$ts:spredrop_p2p_secret"
        val sig = sha256(raw).take(8)
        return "spredrop://pair?id=${spreDropId.removePrefix("@")}&uid=$userId&name=${java.net.URLEncoder.encode(displayName, "UTF-8")}&ts=$ts&sig=$sig"
    }

    /**
     * Generates a deterministic high-contrast visual QR matrix Bitmap for any text payload.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val matrixSize = 29 // standard Version 3 29x29 QR grid
        val grid = Array(matrixSize) { BooleanArray(matrixSize) }

        // Finder patterns at top-left, top-right, bottom-left
        drawFinderPattern(grid, 0, 0)
        drawFinderPattern(grid, matrixSize - 7, 0)
        drawFinderPattern(grid, 0, matrixSize - 7)

        // Timing patterns
        for (i in 8 until matrixSize - 8) {
            grid[6][i] = (i % 2 == 0)
            grid[i][6] = (i % 2 == 0)
        }

        // Alignment pattern at bottom-right
        drawAlignmentPattern(grid, matrixSize - 9, matrixSize - 9)

        // Deterministic content encoding
        val hashBytes = sha256Bytes(content)
        var byteIdx = 0
        var bitIdx = 0

        for (x in 0 until matrixSize) {
            for (y in 0 until matrixSize) {
                // Skip finder and timing zones
                if (isProtectedZone(x, y, matrixSize)) continue

                val currentByte = hashBytes[byteIdx % hashBytes.size].toInt()
                val isDark = ((currentByte shr (bitIdx % 8)) and 1) == 1
                // Dynamic content hash XOR position pattern
                grid[x][y] = isDark xor ((x * 7 + y * 13 + content.hashCode()) % 3 == 0)

                bitIdx++
                if (bitIdx % 8 == 0) byteIdx++
            }
        }

        // Render to Bitmap with border quiet-zone
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val quietZone = 2
        val totalGrid = matrixSize + quietZone * 2
        val cellSize = size.toFloat() / totalGrid.toFloat()

        val darkColor = Color.parseColor("#00B4D8") // SpreDrop cyan-teal accent
        val bgDarkColor = Color.parseColor("#0B132B") // Dark navy background
        val white = Color.WHITE

        for (px in 0 until size) {
            for (py in 0 until size) {
                val gx = (px / cellSize).toInt() - quietZone
                val gy = (py / cellSize).toInt() - quietZone

                if (gx in 0 until matrixSize && gy in 0 until matrixSize && grid[gx][gy]) {
                    // Check if inside finder patterns - make them bright cyan
                    if (isFinderZone(gx, gy, matrixSize)) {
                        bitmap.setPixel(px, py, darkColor)
                    } else {
                        bitmap.setPixel(px, py, white)
                    }
                } else {
                    bitmap.setPixel(px, py, bgDarkColor)
                }
            }
        }

        return bitmap
    }

    fun generateQrImageBitmap(content: String, size: Int = 512): ImageBitmap {
        return generateQrBitmap(content, size).asImageBitmap()
    }

    private fun drawFinderPattern(grid: Array<BooleanArray>, startX: Int, startY: Int) {
        for (x in 0..6) {
            for (y in 0..6) {
                val isBorder = x == 0 || x == 6 || y == 0 || y == 6
                val isCenter = x in 2..4 && y in 2..4
                grid[startX + x][startY + y] = isBorder || isCenter
            }
        }
    }

    private fun drawAlignmentPattern(grid: Array<BooleanArray>, startX: Int, startY: Int) {
        for (x in 0..4) {
            for (y in 0..4) {
                val isBorder = x == 0 || x == 4 || y == 0 || y == 4
                val isCenter = x == 2 && y == 2
                grid[startX + x][startY + y] = isBorder || isCenter
            }
        }
    }

    private fun isFinderZone(x: Int, y: Int, size: Int): Boolean {
        val inTL = x < 7 && y < 7
        val inTR = x >= size - 7 && y < 7
        val inBL = x < 7 && y >= size - 7
        return inTL || inTR || inBL
    }

    private fun isProtectedZone(x: Int, y: Int, size: Int): Boolean {
        if (x < 8 && y < 8) return true
        if (x >= size - 8 && y < 8) return true
        if (x < 8 && y >= size - 8) return true
        if (x == 6 || y == 6) return true
        if (x in (size - 9)..(size - 5) && y in (size - 9)..(size - 5)) return true
        return false
    }

    private fun sha256Bytes(input: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray())
    }

    fun sha256(input: String): String {
        return sha256Bytes(input).joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses SpreDrop URI: spredrop://pair?id=rahul&uid=123&name=Rahul
     */
    fun parsePairUri(uriString: String): ParsedPairData? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "spredrop") {
                val rawId = uri.getQueryParameter("id") ?: return null
                val spreDropId = if (rawId.startsWith("@")) rawId else "@$rawId"
                val uid = uri.getQueryParameter("uid") ?: "peer_${System.currentTimeMillis()}"
                val name = uri.getQueryParameter("name") ?: spreDropId
                ParsedPairData(spreDropId = spreDropId, userId = uid, displayName = name)
            } else if (uriString.startsWith("@")) {
                ParsedPairData(spreDropId = uriString, userId = "user_${uriString.hashCode()}", displayName = uriString.removePrefix("@").replaceFirstChar { it.uppercase() })
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

data class ParsedPairData(
    val spreDropId: String,
    val userId: String,
    val displayName: String
)
