package com.rasmi.purevon.util.viral

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.rasmi.purevon.util.newSharedCacheFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ VIRAL #4b — محرك رسم بطاقة QR جهة الاتصال (نمط سناب شات)
 *
 * تصميم معتمد من المستخدم (إعادة تصميم 3 — هوية بلوسكاي):
 * بطاقة بيضاء بزوايا دائرية وخارج شفاف + حلقة داخلية كحلية داكنة،
 * والـQR يملأ البطاقة بوحدات زرقاء داكنة، وفي مركزه شارة بيضاء تحمل
 * «By Rasmi» بالأزرق الهويّ وآخر 4 أرقام تحتها — كل شيء داخل الرمز.
 * تصحيح أخطاء H (30%) لتحمّل تغطية الشارة المركزية بأمان.
 * كل الأبعاد بكسلات ثابتة — المخرجات مطابقة تمامًا من أي جهاز.
 */
@Singleton
class QrCardRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val SIZE = 1080
        private const val QR_BITMAP_SIZE = 560

        // أبعاد ثابتة بالبكسل — اتساق كامل بين الأجهزة
        private const val CARD_MARGIN = 20f
        private const val CARD_RADIUS = 60f
        private const val RING_INSET = 14f
        private const val RING_STROKE = 10f

        // الـQR يملأ البطاقة تقريبًا مع إطار أبيض رفيع مدمج (quiet zone ~21px)
        private const val QR_SIDE = 950f
        private const val QR_CENTER_Y = 540f

        // الشارة المركزية (نمط سناب شات)
        private const val EMBLEM_WIDTH = 340f
        private const val EMBLEM_HEIGHT = 190f
        private const val EMBLEM_RADIUS = 36f
        private const val EMBLEM_TEXT_SIZE = 56f
        private const val EMBLEM_DIGITS_SIZE = 38f
    }

    // لوحة ألوان هوية التطبيق (بلوسكاي): أزرق هويّ فاتح + أزرق داكن آمن للمسح + كحلي
    private val brandBlue = 0xFF1185FE.toInt()
    private val scanSafeBlue = 0xFF0C6FD8.toInt()
    private val darkNavy = 0xFF16314F.toInt()
    private val numberColor = 0xFF37474F.toInt()

    /** توليد مصفوفة QR كخطوة وسيطة قابلة للفحص */
    fun encodeQr(payload: String): Bitmap {
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            QR_BITMAP_SIZE,
            QR_BITMAP_SIZE,
            mapOf(
                // ✅ UTF-8 إلزامي: الترميز الافتراضي ISO-8859-1 يفسد الأسماء العربية
                // ويستبدلها بعلامات استفهام فيتلف حفظ جهة الاتصال
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to "H"
            )
        )
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] =
                    if (matrix[x, y]) scanSafeBlue else android.graphics.Color.WHITE
            }
        }
        return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }

    /**
     * رسم البطاقة الكاملة. يعيد null إذا فشل بناء الحمولة (رقم غير صالح).
     * الحمولة vCard 3.0 كاملة → المسح يعرض «إضافة لجهات الاتصال».
     */
    fun renderCard(data: com.rasmi.purevon.util.viral.MyCardData, appName: String): Bitmap? {
        val payload = QrContactCard.buildContactPayload(data) ?: return null
        return renderCardWithPayload(payload, data.phone, appName)
    }

    internal fun renderCardWithPayload(
        payload: String,
        phoneNumber: String?,
        appName: String
    ): Bitmap {
        // يبدأ شفافًا بالكامل — الزوايا الخارجية للبطاقة تبقى شفافة
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // البطاقة البيضاء العائمة بزوايا دائرية
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        canvas.drawRoundRect(
            RectF(CARD_MARGIN, CARD_MARGIN, SIZE - CARD_MARGIN, SIZE - CARD_MARGIN),
            CARD_RADIUS, CARD_RADIUS, cardPaint
        )

        // حلقة داخلية كحلية داكنة — لمسة بلوسكاي داكن على حافة البطاقة
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkNavy
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE
        }
        val ringHalf = RING_INSET + RING_STROKE / 2f
        canvas.drawRoundRect(
            RectF(
                CARD_MARGIN + ringHalf, CARD_MARGIN + ringHalf,
                SIZE - CARD_MARGIN - ringHalf, SIZE - CARD_MARGIN - ringHalf
            ),
            CARD_RADIUS - ringHalf, CARD_RADIUS - ringHalf, ringPaint
        )

        // الـQR يملأ البطاقة بوحدات زرقاء داكنة آمنة للمسح
        val qr = encodeQr(payload)
        val qrLeft = (SIZE - QR_SIDE) / 2f
        val qrTop = QR_CENTER_Y - QR_SIDE / 2f
        canvas.drawBitmap(
            qr, null,
            RectF(qrLeft, qrTop, qrLeft + QR_SIDE, qrTop + QR_SIDE),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )

        // الشارة البيضاء المركزية: «By Rasmi» + آخر 4 أرقام (نمط سناب شات)
        val emblemLeft = (SIZE - EMBLEM_WIDTH) / 2f
        val emblemTop = QR_CENTER_Y - EMBLEM_HEIGHT / 2f
        val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        canvas.drawRoundRect(
            RectF(emblemLeft, emblemTop, emblemLeft + EMBLEM_WIDTH, emblemTop + EMBLEM_HEIGHT),
            EMBLEM_RADIUS, EMBLEM_RADIUS, emblemPaint
        )

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brandBlue
            textSize = EMBLEM_TEXT_SIZE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("By $appName", SIZE / 2f, emblemTop + 82f, brandPaint)

        val digitsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = numberColor
            textSize = EMBLEM_DIGITS_SIZE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val masked = QrContactCard.maskPhone(phoneNumber)
        canvas.drawText(masked, SIZE / 2f, emblemTop + 148f, digitsPaint)

        return bitmap
    }

    /**
     * كتابة البطاقة PNG إلى sharedCacheDir وإرجاع URI عبر FileProvider للمشاركة.
     */
    fun writeToCache(card: Bitmap, fileName: String): Uri? {
        return try {
            val file = context.newSharedCacheFile(fileName)
            file.outputStream().use { out ->
                card.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }
}
