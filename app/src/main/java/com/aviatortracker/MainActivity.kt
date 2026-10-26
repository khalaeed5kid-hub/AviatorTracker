package com.aviatortracker

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var db: ResultsDb
    private lateinit var diagView: TextView
    private lateinit var cropView: ImageView
    private lateinit var resultsView: TextView
    private val ui = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = ResultsDb(this)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            root.addView(this)
        }

        root.addView(TextView(this).apply { text = "Aviator Tracker"; textSize = 22f; setTypeface(null, Typeface.BOLD) })
        btn("1) منح إذن الظهور فوق التطبيقات") { requestOverlay() }
        btn("2) بدء الخدمة (إذن التقاط الشاشة)") { startCapture() }
        btn("إيقاف الخدمة") {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
        }

        root.addView(TextView(this).apply { text = "\nالتشخيص"; setTypeface(null, Typeface.BOLD) })
        diagView = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 13f }
        root.addView(diagView)
        cropView = ImageView(this).apply {
            setBackgroundColor(Color.LTGRAY)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 220)
        }
        root.addView(cropView)

        btn("نسخ كل النتائج") { copyAll() }
        btn("حذف كل النتائج") { confirmDelete() }

        root.addView(TextView(this).apply { text = "\nالسجل"; setTypeface(null, Typeface.BOLD) })
        resultsView = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 14f }
        root.addView(resultsView)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(refresh) }

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "الإذن ممنوح بالفعل", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "امنح إذن الظهور فوق التطبيقات أولًا", Toast.LENGTH_LONG).show()
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
            startForegroundService(i)
            Toast.makeText(this, "بدأت الخدمة. اضغط الأيقونة العائمة لتحديد المنطقة", Toast.LENGTH_LONG).show()
        }
    }

    private fun fmt(v: Double?) = if (v == null) "—" else String.format(Locale.US, "%.2fx", v)

    private fun render() {
        val d = Diag
        diagView.text = buildString {
            appendLine("الخدمة         : " + if (d.running) "تعمل" else "متوقفة")
            appendLine("المنطقة        : ${d.region}")
            appendLine("نص OCR الخام   : '${d.rawText}'")
            appendLine("المضاعف المقروء: ${fmt(d.value)}")
            appendLine("حالة الجولة    : ${if (d.state == RoundState.FLYING) "طيران (FLYING)" else "انتظار (WAITING)"}")
            appendLine("سيُحفظ عند النهاية: ${fmt(d.pending)}")
            appendLine("آخر نتيجة محفوظة: ${fmt(d.lastSaved)}  (محفوظ في هذه الجلسة: ${d.savedCount})")
            appendLine("الإطارات: ${d.frames}   إطارات سوداء: ${d.blankFrames}")
            if (d.error.isNotEmpty()) appendLine("تنبيه: ${d.error}")
        }
        d.crop?.let { cropView.setImageBitmap(it) }

        val rows = db.all()
        resultsView.text = if (rows.isEmpty()) "(لا نتائج)" else {
            val f = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
            rows.asReversed().take(200).joinToString("\n") { (m, t) -> "${fmt(m)}   ${f.format(Date(t))}" } +
                "\n\nالمجموع: ${rows.size}"
        }
    }

    private fun copyAll() {
        val rows = db.all()
        if (rows.isEmpty()) { Toast.makeText(this, "لا نتائج", Toast.LENGTH_SHORT).show(); return }
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val text = rows.mapIndexed { i, (m, t) -> "${i + 1}. ${fmt(m)}  ${f.format(Date(t))}" }.joinToString("\n")
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("results", text))
        Toast.makeText(this, "نُسخت ${rows.size} نتيجة", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("حذف كل النتائج؟")
            .setMessage("لا يمكن التراجع عن هذا الإجراء.")
            .setPositiveButton("حذف") { _, _ -> db.clear(); render() }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
