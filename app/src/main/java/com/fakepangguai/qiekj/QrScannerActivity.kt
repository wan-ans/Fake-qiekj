package com.fakepangguai.qiekj

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 扫码页：CameraX 预览 + ML Kit 解码。
 *
 * 设备屏幕上的二维码（尤其是饮水机那种蓝底 LCD）对比度低、有摩尔纹，
 * ML Kit 直接解原始画面经常失败，所以这里会额外用「伽马 / CLAHE 局部直方图均衡」
 * 增强出几张候选图再交给 ML Kit，实测能让原本完全识别不出的码解出来。
 */
class QrScannerActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var hintView: TextView
    private lateinit var torchButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanner: BarcodeScanner
    private var camera: Camera? = null
    private var torchOn = false
    private var handled = false
    private var lastEnhancedAttempt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        scanner = BarcodeScanning.getClient(options)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setContentView(buildLayout())

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun buildLayout(): ViewGroup {
        val root = FrameLayout(this)
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView)

        hintView = TextView(this).apply {
            text = "将设备上的二维码放入取景框"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply { bottomMargin = (180 * resources.displayMetrics.density).toInt() }
        }
        root.addView(hintView)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply { bottomMargin = (96 * resources.displayMetrics.density).toInt() }
        }
        torchButton = Button(this).apply {
            text = "手电筒"
            setOnClickListener { toggleTorch() }
        }
        val galleryButton = Button(this).apply {
            text = "相册识别"
            setOnClickListener { pickImage() }
        }
        val cancelButton = Button(this).apply {
            text = "取消"
            setOnClickListener { finish() }
        }
        buttons.addView(torchButton)
        buttons.addView(galleryButton)
        buttons.addView(cancelButton)
        root.addView(buttons)
        return root
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy -> analyzeFrame(proxy) }
                provider.unbindAll()
                val lifecycleOwner: androidx.lifecycle.LifecycleOwner = this
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure {
                hintView.text = "相机不可用，可用「相册识别」选图"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        val mediaImage = proxy.image
        if (mediaImage == null || handled) {
            proxy.close()
            return
        }
        val now = System.currentTimeMillis()
        var snapshot: Bitmap? = null
        if (now - lastEnhancedAttempt >= ENHANCED_INTERVAL_MS) {
            lastEnhancedAttempt = now
            snapshot = runCatching { proxy.toBitmap() }.getOrNull()
        }
        if (snapshot != null) {
            tryVariants(enhanceVariants(snapshot), onAllFailed = {})
        }
        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let { finishWithResult(it) }
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, "选择二维码图片"), REQ_GALLERY)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_GALLERY || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        hintView.text = "正在识别图片…"
        val bitmap = runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (bitmap == null) {
            hintView.text = "无法读取图片"
            return
        }
        decodeBitmap(bitmap)
    }

    private fun decodeBitmap(bitmap: Bitmap) {
        val base = limitSize(bitmap, MAX_DECODE_SIZE)
        val variants = mutableListOf(base)
        variants.addAll(enhanceVariants(base))
        tryVariants(variants) {
            if (!handled) hintView.text = "图片里没有识别到二维码，换个角度重拍试试"
        }
    }

    private fun tryVariants(variants: List<Bitmap>, onAllFailed: () -> Unit) {
        var index = 0
        fun next() {
            if (handled) return
            if (index >= variants.size) {
                onAllFailed()
                return
            }
            val candidate = variants[index]
            index += 1
            scanner.process(InputImage.fromBitmap(candidate, 0))
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let { finishWithResult(it) }
                }
                .addOnCompleteListener { next() }
        }
        next()
    }

    /**
     * 候选图：伽马压暗 + CLAHE 局部均衡，专门对付低对比度的屏幕二维码。
     * 只保留两种变体以降低 CPU/内存开销（实测 CLAHE 覆盖了绝大部分场景）。
     */
    private fun enhanceVariants(source: Bitmap): List<Bitmap> {
        val small = limitSize(source, ENHANCE_MAX_SIZE)
        val result = mutableListOf<Bitmap>()
        runCatching { result.add(luminanceTransform(small) { value -> gammaLut(value, 1.6) }) }
        runCatching { result.add(clahe(small, clipLimit = 3.0f, tiles = 8)) }
        return result
    }

    private fun gammaLut(value: Int, gamma: Double): Int {
        val normalized = value / 255.0
        val corrected = Math.pow(normalized, gamma)
        return (corrected * 255.0).toInt().coerceIn(0, 255)
    }

    private fun luminanceTransform(source: Bitmap, mapper: (Int) -> Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val lut = IntArray(256) { mapper(it) }
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val gray = luminance(pixel)
            val mapped = lut[gray]
            pixels[index] = (0xFF shl 24) or (mapped shl 16) or (mapped shl 8) or mapped
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun luminance(pixel: Int): Int {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** 简化版 CLAHE：分块直方图均衡 + 裁剪限幅 + 双线性插值。 */
    private fun clahe(source: Bitmap, clipLimit: Float, tiles: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = IntArray(pixels.size) { luminance(pixels[it]) }

        val tileWidth = (width + tiles - 1) / tiles
        val tileHeight = (height + tiles - 1) / tiles
        val lut = Array(tiles * tiles) { IntArray(256) }

        for (tileY in 0 until tiles) {
            for (tileX in 0 until tiles) {
                val x0 = tileX * tileWidth
                val y0 = tileY * tileHeight
                val x1 = minOf(width, x0 + tileWidth)
                val y1 = minOf(height, y0 + tileHeight)
                val histogram = IntArray(256)
                var count = 0
                for (y in y0 until y1) {
                    val rowOffset = y * width
                    for (x in x0 until x1) {
                        histogram[gray[rowOffset + x]] += 1
                        count += 1
                    }
                }
                val tileLut = lut[tileY * tiles + tileX]
                if (count == 0) {
                    for (i in 0 until 256) tileLut[i] = i
                    continue
                }
                val limit = maxOf(1, (clipLimit * count / 256f).toInt())
                var excess = 0
                for (i in 0 until 256) {
                    if (histogram[i] > limit) {
                        excess += histogram[i] - limit
                        histogram[i] = limit
                    }
                }
                val perBin = excess / 256
                val remainder = excess % 256
                for (i in 0 until 256) {
                    histogram[i] += perBin
                    if (i < remainder) histogram[i] += 1
                }
                var cumulative = 0
                val scale = 255f / count
                for (i in 0 until 256) {
                    cumulative += histogram[i]
                    tileLut[i] = (cumulative * scale).toInt().coerceIn(0, 255)
                }
            }
        }

        val output = IntArray(pixels.size)
        for (y in 0 until height) {
            val framedY = (y + 0.5f) / tileHeight - 0.5f
            val tileY0 = framedY.toInt().coerceIn(0, tiles - 1)
            val tileY1 = minOf(tiles - 1, tileY0 + 1)
            val weightY = (framedY - tileY0).coerceIn(0f, 1f)
            for (x in 0 until width) {
                val framedX = (x + 0.5f) / tileWidth - 0.5f
                val tileX0 = framedX.toInt().coerceIn(0, tiles - 1)
                val tileX1 = minOf(tiles - 1, tileX0 + 1)
                val weightX = (framedX - tileX0).coerceIn(0f, 1f)
                val value = gray[y * width + x]
                val v00 = lut[tileY0 * tiles + tileX0][value]
                val v01 = lut[tileY0 * tiles + tileX1][value]
                val v10 = lut[tileY1 * tiles + tileX0][value]
                val v11 = lut[tileY1 * tiles + tileX1][value]
                val top = v00 + (v01 - v00) * weightX
                val bottom = v10 + (v11 - v10) * weightX
                val mapped = (top + (bottom - top) * weightY).toInt().coerceIn(0, 255)
                output[y * width + x] = (0xFF shl 24) or (mapped shl 16) or (mapped shl 8) or mapped
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun limitSize(bitmap: Bitmap, maxSize: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSize) return bitmap
        val scale = maxSize.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun finishWithResult(text: String) {
        if (handled) return
        handled = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CONTENTS, text))
        finish()
    }

    private fun toggleTorch() {
        val current = camera
        if (current == null) {
            Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        torchOn = !torchOn
        current.cameraControl.enableTorch(torchOn)
        torchButton.text = if (torchOn) "关闭手电筒" else "手电筒"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else if (requestCode == REQ_CAMERA) {
            hintView.text = "没有相机权限，可用「相册识别」选图"
        }
    }

    override fun onDestroy() {
        runCatching { camera?.cameraControl?.enableTorch(false) }
        runCatching { cameraExecutor.shutdown() }
        runCatching { scanner.close() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONTENTS = "contents"

        private const val REQ_CAMERA = 1001
        private const val REQ_GALLERY = 1002
        private const val ENHANCED_INTERVAL_MS = 1200L
        private const val ENHANCE_MAX_SIZE = 960
        private const val MAX_DECODE_SIZE = 1600
    }
}
