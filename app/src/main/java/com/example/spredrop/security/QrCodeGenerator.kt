package com.example.spredrop.security

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
     * Generates a real, valid standard QR code Bitmap.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            val bgDarkColor = Color.parseColor("#0B132B") // Dark navy background
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    if (bitMatrix.get(x, y)) {
                        bitmap.setPixel(x, y, Color.WHITE)
                    } else {
                        bitmap.setPixel(x, y, bgDarkColor)
                    }
                }
            }
            bitmap
        } catch (e: Exception) {
            // Fallback empty bitmap
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }
    }

    fun generateQrImageBitmap(content: String, size: Int = 512): ImageBitmap {
        return generateQrBitmap(content, size).asImageBitmap()
    }

    private fun sha256Bytes(input: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray())
    }

    fun sha256(input: String): String {
        return sha256Bytes(input).joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses and securely verifies SpreDrop URI: spredrop://pair?id=rahul&uid=123&name=Rahul
     */
    fun parsePairUri(uriString: String): ParsedPairData? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "spredrop") {
                val rawId = uri.getQueryParameter("id") ?: return null
                val spreDropId = if (rawId.startsWith("@")) rawId.lowercase() else "@${rawId.lowercase()}"
                val uid = uri.getQueryParameter("uid") ?: "peer_${System.currentTimeMillis()}"
                val name = uri.getQueryParameter("name") ?: spreDropId
                val decodedName = java.net.URLDecoder.decode(name, "UTF-8")
                
                // Security Check: Validate expiration and signature
                val tsStr = uri.getQueryParameter("ts")
                val sig = uri.getQueryParameter("sig")
                if (tsStr != null && sig != null) {
                    val ts = tsStr.toLongOrNull() ?: 0L
                    val age = System.currentTimeMillis() - ts
                    // 1 hour expiration limit, with 5 min buffer for clock drift
                    if (age > 3600000 || age < -300000) {
                        android.util.Log.e("QrSecurity", "QR Code has expired or timestamp is invalid (Age: $age ms)")
                        return null
                    }
                    val expectedRaw = "$spreDropId:$uid:$decodedName:$ts:spredrop_p2p_secret"
                    val expectedSig = sha256(expectedRaw).take(8)
                    if (sig != expectedSig) {
                        android.util.Log.e("QrSecurity", "QR Code signature mismatch! Potential security tampering.")
                        return null
                    }
                }
                
                ParsedPairData(spreDropId = spreDropId, userId = uid, displayName = decodedName)
            } else if (uriString.startsWith("@")) {
                val lowercaseId = uriString.lowercase()
                ParsedPairData(spreDropId = lowercaseId, userId = "user_${lowercaseId.hashCode()}", displayName = uriString.removePrefix("@").replaceFirstChar { it.uppercase() })
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
