package com.chapiodara.prismastudio

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

private data class SceneObject(val kind: Int, var x: Float = 0f, var y: Float = 0.55f, var z: Float = 0f,
                               var rotation: Float = 0f, var scale: Float = 0.55f, var material: Int = 0)

class MainActivity : Activity() {
    private lateinit var renderer: SceneRenderer
    private lateinit var glView: GLSurfaceView
    private lateinit var status: TextView
    private lateinit var hierarchy: LinearLayout
    private lateinit var toolRail: LinearLayout
    private lateinit var properties: TextView
    private lateinit var scaleDetector: ScaleGestureDetector
    private val bg = Color.rgb(20, 21, 25)
    private val panel = Color.rgb(31, 33, 39)
    private val panel2 = Color.rgb(40, 42, 49)
    private val accent = Color.rgb(239, 50, 65)
    private var activeTool = "SELECT"
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchLastX = 0f
    private var touchLastY = 0f
    private var currentFrame = 0
    private var playing = false
    private lateinit var frameLabel: TextView
    private val animationHandler = Handler(Looper.getMainLooper())
    private val playback = object : Runnable { override fun run() { if (playing) { setFrame((currentFrame + 1) % 121); animationHandler.postDelayed(this, 120) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        renderer = SceneRenderer(this)
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean { renderer.zoom(detector.scaleFactor); return true }
        })
        loadScene()
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2); setRenderer(renderer); renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { touchDownX = event.x; touchDownY = event.y; touchLastX = event.x; touchLastY = event.y; renderer.beginDrag(event.x, event.y); true }
                    MotionEvent.ACTION_POINTER_DOWN -> true
                    MotionEvent.ACTION_MOVE -> { if (!scaleDetector.isInProgress && event.pointerCount == 1) { if (activeTool == "SELECT") renderer.drag(event.x, event.y) else renderer.toolDrag(activeTool, event.x - touchLastX, event.y - touchLastY); touchLastX = event.x; touchLastY = event.y }; true }
                    MotionEvent.ACTION_UP -> { if (kotlin.math.abs(event.x - touchDownX) + kotlin.math.abs(event.y - touchDownY) < dp(10) && activeTool == "SELECT") { renderer.selectNext(); refresh() }; true }
                    else -> true
                }
            }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.addView(makeHeader(), LinearLayout.LayoutParams(-1, dp(48)))

        val center = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(15, 16, 19)) }
        toolRail = makeToolRail() as LinearLayout
        center.addView(toolRail, LinearLayout.LayoutParams(dp(58), -1))
        center.addView(glView, LinearLayout.LayoutParams(0, -1, 1f))
        center.addView(makeInspector(), LinearLayout.LayoutParams(dp(178), -1))
        root.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(makeTimeline(), LinearLayout.LayoutParams(-1, dp(58)))
        root.addView(makeBottomBar(), LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
        rebuildHierarchy()
    }

    private fun makeHeader(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(8), 0); setBackgroundColor(panel) }
        val brand = TextView(this).apply { text = "◈  PRISMA STUDIO"; setTextColor(Color.WHITE); textSize = 14f; setTypeface(null, 1) }
        row.addView(brand, LinearLayout.LayoutParams(dp(160), -2))
        val divider = TextView(this).apply { text = "│"; setTextColor(Color.DKGRAY); textSize = 16f }
        row.addView(divider)
        val project = TextView(this).apply { text = "Mi proyecto     ▾"; setTextColor(Color.LTGRAY); textSize = 12f; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, 0, 0) }
        row.addView(project, LinearLayout.LayoutParams(0, -1, 1f))
        status = TextView(this).apply { text = "1 objeto"; setTextColor(Color.LTGRAY); textSize = 11f; gravity = Gravity.CENTER }
        row.addView(status, LinearLayout.LayoutParams(dp(70), -1))
        row.addView(makeTopAction("−") { renderer.transform { it.scale = (it.scale - 0.1f).coerceAtLeast(0.15f) }; refresh() })
        row.addView(makeTopAction("＋") { renderer.transform { it.scale = (it.scale + 0.1f).coerceAtMost(2f) }; refresh() })
        row.addView(makeTopAction("▣ Guardar") { saveScene() })
        return row
    }

    private fun makeTopAction(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(dp(9), 0, dp(9), 0)
        setBackgroundColor(panel2); layoutParams = LinearLayout.LayoutParams(-2, dp(34)).apply { setMargins(dp(3), 0, dp(3), 0) }
        setOnClickListener { action() }
    }

    private fun makeToolRail(): View {
        val rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setPadding(dp(4), dp(8), dp(4), dp(4)); setBackgroundColor(panel) }
        addTool(rail, "⌖", "Select", "SELECT")
        addTool(rail, "↔", "Mover", "MOVE")
        addTool(rail, "⟳", "Rotar", "ROTATE")
        addTool(rail, "⇲", "Escala", "SCALE")
        val spacer = View(this); rail.addView(spacer, LinearLayout.LayoutParams(1, dp(7)))
        addRailAction(rail, "+", "Cubo") { renderer.add(0); refresh() }
        addRailAction(rail, "+", "Esfera") { renderer.add(1); refresh() }
        return rail
    }

    private fun addTool(rail: LinearLayout, icon: String, label: String, tool: String) {
        val tile = IconToolTile(this, icon, label, activeTool == tool) {
            activeTool = tool
            refreshToolRail()
        }
        tile.tag = "tool:$tool"
        tile.layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(2), 0, dp(2)) }
        rail.addView(tile)
    }

    private fun addRailAction(rail: LinearLayout, icon: String, label: String, action: () -> Unit) {
        val tile = IconToolTile(this, icon, label, false, action)
        tile.layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(2), 0, dp(2)) }
        rail.addView(tile)
    }

    private fun refreshToolRail() {
        if (!::toolRail.isInitialized) return
        for (i in 0 until toolRail.childCount) {
            val v = toolRail.getChildAt(i)
            val tag = v.tag?.toString() ?: continue
            if (tag.startsWith("tool:")) (v as IconToolTile).setActive(tag.substringAfter(":") == activeTool)
        }
    }

    private fun makeInspector(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel); setPadding(dp(8), dp(8), dp(8), dp(6)) }
        box.addView(TextView(this).apply { text = "JERARQUÍA"; textSize = 10f; setTypeface(null, 1); setTextColor(Color.LTGRAY); setPadding(dp(2), 0, 0, dp(7)) })
        hierarchy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(hierarchy)
        val sep = View(this).apply { setBackgroundColor(Color.rgb(60, 62, 68)) }
        box.addView(sep, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(7); bottomMargin = dp(7) })
        box.addView(TextView(this).apply { text = "TRANSFORMAR"; textSize = 10f; setTypeface(null, 1); setTextColor(Color.LTGRAY); setPadding(dp(2), 0, 0, dp(4)) })
        properties = TextView(this).apply { textSize = 10f; setTextColor(Color.LTGRAY); setLineSpacing(dp(4).toFloat(), 1f); setPadding(dp(3), dp(3), 0, 0) }
        box.addView(properties)
        box.addView(TextView(this).apply { text = "\nVista 3D\nArrastra para orbitar\nPellizca para zoom"; textSize = 9f; setTextColor(Color.GRAY); gravity = Gravity.BOTTOM; setPadding(dp(3), 0, 0, dp(2)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return box
    }

    private fun rebuildHierarchy() {
        if (!::hierarchy.isInitialized) return
        hierarchy.removeAllViews()
        val count = renderer.objectCount(); val selected = renderer.selectedIndex()
        val list = renderer.objectsSnapshot()
        list.forEachIndexed { index, obj ->
            val label = if (obj.kind == 0) "Cube" else "Sphere"
            val row = TextView(this).apply {
                text = "${if (index == selected) "◆" else "◇"}  $label ${index + 1}"; textSize = 11f; setTextColor(if (index == selected) Color.WHITE else Color.LTGRAY)
                setPadding(dp(6), dp(7), dp(3), dp(7)); setBackgroundColor(if (index == selected) Color.rgb(66, 42, 47) else Color.TRANSPARENT)
                setOnClickListener { renderer.select(index); refresh() }
            }
            hierarchy.addView(row)
        }
        val obj = list.getOrNull(selected)
        val materialNames = listOf("Cian", "Rojo", "Verde", "Dorado", "Violeta")
        properties.text = if (obj == null) "Sin selección" else "Posición\nX  ${fmt(obj.x)}\nY  ${fmt(obj.y)}\nZ  ${fmt(obj.z)}\n\nRotación\nY  ${fmt(obj.rotation)}°\n\nEscala\n${fmt(obj.scale)}\n\nMaterial\n${materialNames[obj.material.coerceIn(0, materialNames.lastIndex)]}"
        status.text = "$count objeto${if (count == 1) "" else "s"}"
    }

    private fun makeTimeline(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(10), 0); setBackgroundColor(Color.rgb(25, 26, 31)) }
        row.addView(TextView(this).apply { text = "ANIMACIÓN"; textSize = 10f; setTypeface(null, 1); setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(dp(92), -1))
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(panel2); setPadding(dp(7), 0, dp(7), 0) }
        line.addView(makeTimelineControl("−10") { setFrame((currentFrame - 10).coerceAtLeast(0)) }, LinearLayout.LayoutParams(dp(42), -1))
        line.addView(makeTimelineControl("▶") { togglePlayback() }, LinearLayout.LayoutParams(dp(36), -1))
        frameLabel = TextView(this).apply { text = "Fotograma 0"; setTextColor(Color.WHITE); textSize = 10f; gravity = Gravity.CENTER }
        line.addView(frameLabel, LinearLayout.LayoutParams(0, -1, 1f))
        line.addView(makeTimelineControl("◆") { renderer.setKeyframe(currentFrame); Toast.makeText(this@MainActivity, "Clave guardada en fotograma $currentFrame", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(dp(38), -1))
        line.addView(makeTimelineControl("+10") { setFrame((currentFrame + 10).coerceAtMost(120)) }, LinearLayout.LayoutParams(dp(42), -1))
        row.addView(line, LinearLayout.LayoutParams(0, dp(38), 1f))
        row.addView(TextView(this).apply { text = "  ${(currentFrame / 24)}s / 5s"; setTextColor(Color.LTGRAY); textSize = 9f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(55), -1))
        return row
    }

    private fun makeBottomBar(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(panel) }
        listOf("OBJETOS", "MATERIAL", "ANIMAR").forEachIndexed { i, label ->
            row.addView(TextView(this).apply {
                text = label; textSize = 10f; setTypeface(null, 1); gravity = Gravity.CENTER; setTextColor(if (i == 0) Color.WHITE else Color.LTGRAY)
                setBackgroundColor(if (i == 0) accent else panel2); layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
                setOnClickListener { when (label) { "OBJETOS" -> { renderer.selectNext(); refresh() }; "MATERIAL" -> { renderer.nextMaterial(); refresh() }; "ANIMAR" -> { renderer.setKeyframe(currentFrame); Toast.makeText(this@MainActivity, "Clave guardada: $currentFrame", Toast.LENGTH_SHORT).show() } } }
            })
        }
        val spacer = View(this); row.addView(spacer, LinearLayout.LayoutParams(dp(12), 1))
        row.addView(makeBottomAction("↔ Mover") { renderer.transform { it.x += 0.25f }; refresh() })
        row.addView(makeBottomAction("⟳ Girar") { renderer.transform { it.rotation += 15f }; refresh() })
        row.addView(makeBottomAction("⇲ Escala") { renderer.transform { it.scale = (it.scale + 0.1f).coerceAtMost(2f) }; refresh() })
        row.addView(makeBottomAction("⌫ Borrar") { renderer.removeSelected(); refresh() })
        return row
    }

    private fun makeTimelineControl(label: String, action: () -> Unit) = TextView(this).apply { text = label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { action() } }

    private fun setFrame(frame: Int) { currentFrame = frame.coerceIn(0, 120); renderer.goToFrame(currentFrame); if (::frameLabel.isInitialized) frameLabel.text = "Fotograma $currentFrame"; refresh() }
    private fun togglePlayback() { playing = !playing; if (playing) animationHandler.post(playback) else animationHandler.removeCallbacks(playback) }

    private fun makeBottomAction(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY); setBackgroundColor(panel2)
        layoutParams = LinearLayout.LayoutParams(-2, -1).apply { setMargins(dp(2), 0, dp(2), 0) }; setPadding(dp(8), 0, dp(8), 0); setOnClickListener { action() }
    }

    private fun refresh() { rebuildHierarchy() }
    private fun fmt(v: Float) = String.format(java.util.Locale.US, "%.2f", v)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadScene() {
        try {
            val file = File(filesDir, "escena.json"); if (!file.exists()) return
            val json = JSONObject(file.readText()).getJSONArray("objects"); val items = mutableListOf<SceneObject>()
            for (i in 0 until json.length()) { val item = json.getJSONObject(i); items.add(SceneObject(if (item.optString("type") == "esfera") 1 else 0,
                item.optDouble("x", 0.0).toFloat(), item.optDouble("y", 0.55).toFloat(), item.optDouble("z", 0.0).toFloat(), item.optDouble("rotation", 0.0).toFloat(), item.optDouble("scale", 0.55).toFloat(), item.optInt("material", 0))) }
            if (items.isNotEmpty()) renderer.replace(items)
            val frames = JSONObject(file.readText()).optJSONArray("keyframes")
            if (frames != null) renderer.loadKeyframes(frames)
        } catch (_: Exception) { }
    }

    private fun saveScene() {
        renderer.snapshot { list ->
            try { val arr = JSONArray(); list.forEach { o -> arr.put(JSONObject().put("type", if (o.kind == 0) "cubo" else "esfera").put("x", o.x).put("y", o.y).put("z", o.z).put("rotation", o.rotation).put("scale", o.scale).put("material", o.material)) }
                val frameArray = JSONArray()
                renderer.keyframesSnapshot().forEach { (frame, frameObjects) ->
                    val frameObjectsJson = JSONArray()
                    frameObjects.forEach { o -> frameObjectsJson.put(JSONObject().put("type", if (o.kind == 0) "cubo" else "esfera").put("x", o.x).put("y", o.y).put("z", o.z).put("rotation", o.rotation).put("scale", o.scale).put("material", o.material)) }
                    frameArray.put(JSONObject().put("frame", frame).put("objects", frameObjectsJson))
                }
                File(filesDir, "escena.json").writeText(JSONObject().put("name", "Mi proyecto").put("objects", arr).put("keyframes", frameArray).toString(2))
                runOnUiThread { Toast.makeText(this, "Proyecto guardado", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) { runOnUiThread { Toast.makeText(this, "No se pudo guardar", Toast.LENGTH_SHORT).show() } }
        }
    }

    override fun onResume() { super.onResume(); if (::glView.isInitialized) glView.onResume() }
    override fun onPause() { playing = false; animationHandler.removeCallbacks(playback); if (::glView.isInitialized) glView.onPause(); super.onPause() }
}

private class IconToolTile(context: Context, icon: String, label: String, active: Boolean, action: () -> Unit) : LinearLayout(context) {
    private val caption = TextView(context)
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(0, dp(3), 0, dp(2))
        addView(DrawnIconView(context, if (icon == "+") label else icon, Color.WHITE), LayoutParams(dp(20), dp(21)))
        caption.text = label
        caption.textSize = 8.5f
        caption.gravity = Gravity.CENTER
        caption.setTextColor(Color.LTGRAY)
        addView(caption, LayoutParams(-1, dp(17)))
        setActive(active)
        setOnClickListener { action() }
    }
    fun setActive(active: Boolean) {
        setBackgroundColor(if (active) Color.rgb(201, 44, 58) else Color.rgb(40, 42, 49))
        caption.setTextColor(if (active) Color.WHITE else Color.LTGRAY)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

private class DrawnIconView(context: Context, private val name: String, private val tint: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = 2.1f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width, height).toFloat(); val cx = width / 2f; val cy = height / 2f
        canvas.save(); canvas.translate(cx - s * .5f, cy - s * .5f); canvas.scale(s / 24f, s / 24f)
        fun line(x: Float,y: Float,a: Float,b: Float) = canvas.drawLine(x,y,a,b,p)
        when (name.lowercase()) {
            "select", "⌖" -> { canvas.drawCircle(12f,12f,6f,p); line(12f,1f,12f,6f); line(12f,18f,12f,23f); line(1f,12f,6f,12f); line(18f,12f,23f,12f) }
            "mover", "↔" -> { line(4f,12f,20f,12f); line(4f,12f,8f,8f); line(4f,12f,8f,16f); line(20f,12f,16f,8f); line(20f,12f,16f,16f); line(12f,4f,12f,20f); line(12f,4f,8f,8f); line(12f,4f,16f,8f); line(12f,20f,8f,16f); line(12f,20f,16f,16f) }
            "rotar", "⟳" -> { canvas.drawArc(4f,4f,20f,20f,40f,280f,false,p); line(19f,4f,20f,9f); line(19f,4f,14f,5f) }
            "escala", "⇲" -> { line(4f,9f,4f,4f); line(4f,4f,9f,4f); line(4f,4f,10f,10f); line(20f,15f,20f,20f); line(20f,20f,15f,20f); line(20f,20f,14f,14f) }
            "cubo", "+" -> { val a=Path().apply { moveTo(12f,3f); lineTo(21f,8f); lineTo(12f,13f); lineTo(3f,8f); close(); moveTo(3f,8f); lineTo(3f,18f); lineTo(12f,23f); lineTo(21f,18f); lineTo(21f,8f); moveTo(12f,13f); lineTo(12f,23f) }; canvas.drawPath(a,p) }
            "esfera" -> canvas.drawCircle(12f,12f,8f,p)
            "guardar", "▣ guardar" -> { canvas.drawRoundRect(4f,3f,20f,21f,2f,2f,p); canvas.drawRect(7f,4f,17f,10f,p); canvas.drawRoundRect(7f,13f,17f,20f,1f,1f,p) }
            "play", "▶" -> { val a=Path().apply { moveTo(7f,4f); lineTo(20f,12f); lineTo(7f,20f); close() }; canvas.drawPath(a,p) }
            "keyframe", "◆" -> { val a=Path().apply { moveTo(12f,3f); lineTo(21f,12f); lineTo(12f,21f); lineTo(3f,12f); close() }; canvas.drawPath(a,p) }
            "borrar" -> { canvas.drawRoundRect(6f,7f,18f,21f,2f,2f,p); line(4f,5f,20f,5f); line(9f,3f,15f,3f); line(10f,10f,10f,18f); line(14f,10f,14f,18f) }
            else -> { line(12f,4f,12f,20f); line(4f,12f,20f,12f) }
        }
        canvas.restore()
    }
}

private class SceneRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private val objects = mutableListOf(SceneObject(0))
    private val keyframes = java.util.TreeMap<Int, List<SceneObject>>()
    private var selected = 0
    private var program = 0
    private var cube: FloatBuffer? = null
    private var sphere: FloatBuffer? = null
    private var grid: FloatBuffer? = null
    private var cubeCount = 0
    private var sphereCount = 0
    private var gridCount = 0
    private var width = 1
    private var height = 1
    private var yaw = 35f
    private var pitch = 28f
    private var distance = 8.5f
    private var lastX = 0f
    private var lastY = 0f
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private val light = floatArrayOf(0.4f, 1f, 0.7f)

    @Synchronized fun add(kind: Int) { objects.add(SceneObject(kind, x = ((objects.size % 3) - 1) * 0.8f)); selected = objects.lastIndex }
    @Synchronized fun removeSelected() { if (objects.size > 1) { objects.removeAt(selected.coerceIn(0, objects.lastIndex)); selected = (objects.size - 1).coerceAtLeast(0) } }
    @Synchronized fun transform(f: (SceneObject) -> Unit) { if (objects.isNotEmpty()) f(objects[selected.coerceIn(0, objects.lastIndex)]) }
    @Synchronized fun nextMaterial() { if (objects.isNotEmpty()) objects[selected.coerceIn(0, objects.lastIndex)].material = (objects[selected.coerceIn(0, objects.lastIndex)].material + 1) % 5 }
    @Synchronized fun setKeyframe(frame: Int) { keyframes[frame] = objects.map { it.copy() } }
    @Synchronized fun keyframesSnapshot() = keyframes.mapValues { entry -> entry.value.map { it.copy() } }
    @Synchronized fun loadKeyframes(frames: JSONArray) {
        keyframes.clear()
        for (i in 0 until frames.length()) {
            val frameItem = frames.optJSONObject(i) ?: continue
            val values = frameItem.optJSONArray("objects") ?: continue
            val items = mutableListOf<SceneObject>()
            for (j in 0 until values.length()) {
                val o = values.optJSONObject(j) ?: continue
                items.add(SceneObject(if (o.optString("type") == "esfera") 1 else 0, o.optDouble("x", 0.0).toFloat(), o.optDouble("y", 0.55).toFloat(), o.optDouble("z", 0.0).toFloat(), o.optDouble("rotation", 0.0).toFloat(), o.optDouble("scale", 0.55).toFloat(), o.optInt("material", 0)))
            }
            keyframes[frameItem.optInt("frame")] = items
        }
    }
    @Synchronized fun goToFrame(frame: Int) {
        val lo = keyframes.floorEntry(frame) ?: keyframes.firstEntry() ?: return
        val hi = keyframes.ceilingEntry(frame) ?: lo
        val t = if (hi.key == lo.key) 0f else ((frame - lo.key).toFloat() / (hi.key - lo.key)).coerceIn(0f, 1f)
        val from = lo.value; val to = hi.value
        for (i in objects.indices) {
            val a = from.getOrNull(i) ?: continue; val b = to.getOrNull(i) ?: a; val o = objects[i]
            o.x = a.x + (b.x - a.x) * t; o.y = a.y + (b.y - a.y) * t; o.z = a.z + (b.z - a.z) * t
            o.rotation = a.rotation + (b.rotation - a.rotation) * t; o.scale = a.scale + (b.scale - a.scale) * t
        }
    }
    @Synchronized fun objectCount() = objects.size
    @Synchronized fun selectedIndex() = selected
    @Synchronized fun objectsSnapshot() = objects.map { it.copy() }
    @Synchronized fun select(index: Int) { if (objects.isNotEmpty()) selected = index.coerceIn(0, objects.lastIndex) }
    @Synchronized fun selectNext() { if (objects.isNotEmpty()) selected = (selected + 1) % objects.size }
    @Synchronized fun selectPrevious() { if (objects.isNotEmpty()) selected = (selected - 1 + objects.size) % objects.size }
    @Synchronized fun replace(items: List<SceneObject>) { objects.clear(); objects.addAll(items); selected = 0 }
    @Synchronized fun zoom(factor: Float) { if (factor > 0f) distance = (distance / factor).coerceIn(3f, 18f) }
    @Synchronized fun snapshot(done: (List<SceneObject>) -> Unit) { done(objects.map { it.copy() }) }
    @Synchronized fun beginDrag(x: Float, y: Float) { lastX = x; lastY = y }
    @Synchronized fun drag(x: Float, y: Float) { yaw += (x - lastX) * 0.45f; pitch = (pitch + (y - lastY) * 0.35f).coerceIn(5f, 78f); lastX = x; lastY = y }
    @Synchronized fun toolDrag(tool: String, dx: Float, dy: Float) {
        if (objects.isEmpty()) return
        val o = objects[selected.coerceIn(0, objects.lastIndex)]
        when (tool) {
            "MOVE" -> { o.x += dx * 0.012f; o.y = (o.y - dy * 0.012f).coerceAtLeast(0.08f) }
            "ROTATE" -> o.rotation += dx * 0.7f
            "SCALE" -> o.scale = (o.scale + dx * 0.004f).coerceIn(0.1f, 3f)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.06f, 0.065f, 0.075f, 1f); GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_CULL_FACE)
        val vs = """uniform mat4 uMvp; uniform mat4 uModel; attribute vec3 aPos; attribute vec3 aNormal; varying vec3 vN; void main(){ gl_Position=uMvp*vec4(aPos,1.0); vN=mat3(uModel)*aNormal; }"""
        val fs = """precision mediump float; uniform vec4 uColor; uniform vec3 uLight; varying vec3 vN; void main(){ float l=max(dot(normalize(vN),normalize(uLight)),0.0); gl_FragColor=vec4(uColor.rgb*(0.42+0.58*l),uColor.a); }"""
        program = link(compile(GLES20.GL_VERTEX_SHADER, vs), compile(GLES20.GL_FRAGMENT_SHADER, fs))
        val c = makeCube(); cube = buffer(c); cubeCount = c.size / 6
        val s = makeSphere(); sphere = buffer(s); sphereCount = s.size / 6
        val g = makeGrid(); grid = buffer(g); gridCount = g.size / 6
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h.coerceAtLeast(1); GLES20.glViewport(0, 0, w, h); Matrix.perspectiveM(projection, 0, 43f, w.toFloat() / height, 0.1f, 100f) }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val yr = Math.toRadians(yaw.toDouble()); val pr = Math.toRadians(pitch.toDouble())
        val ex = (distance * cos(pr) * sin(yr)).toFloat(); val ey = (distance * sin(pr)).toFloat(); val ez = (distance * cos(pr) * cos(yr)).toFloat()
        Matrix.setLookAtM(view, 0, ex, ey + 0.25f, ez, 0f, 0.4f, 0f, 0f, 1f, 0f)
        GLES20.glUseProgram(program)
        val pos = GLES20.glGetAttribLocation(program, "aPos"); val norm = GLES20.glGetAttribLocation(program, "aNormal")
        val mvpLoc = GLES20.glGetUniformLocation(program, "uMvp"); val modelLoc = GLES20.glGetUniformLocation(program, "uModel"); val colorLoc = GLES20.glGetUniformLocation(program, "uColor"); val lightLoc = GLES20.glGetUniformLocation(program, "uLight")
        GLES20.glUniform3fv(lightLoc, 1, light, 0)
        drawMesh(grid, gridCount, GLES20.GL_LINES, 0.16f, 0.18f, 0.21f, 1f, pos, norm, mvpLoc, modelLoc, colorLoc)
        synchronized(this) {
            objects.forEachIndexed { i, o ->
                Matrix.setIdentityM(model, 0); Matrix.translateM(model, 0, o.x, o.y, o.z); Matrix.rotateM(model, 0, o.rotation, 0f, 1f, 0f); Matrix.scaleM(model, 0, o.scale, o.scale, o.scale)
                Matrix.multiplyMM(mv, 0, view, 0, model, 0); Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
                GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0); GLES20.glUniformMatrix4fv(modelLoc, 1, false, model, 0)
                val hot = i == selected
                val palette = arrayOf(floatArrayOf(0.30f,0.66f,0.78f), floatArrayOf(0.87f,0.28f,0.24f), floatArrayOf(0.28f,0.76f,0.44f), floatArrayOf(0.94f,0.70f,0.22f), floatArrayOf(0.62f,0.38f,0.88f))
                val c = palette[o.material.coerceIn(0, palette.lastIndex)]
                GLES20.glUniform4f(colorLoc, c[0], c[1], c[2], 1f)
                val b = if (o.kind == 0) cube else sphere; val count = if (o.kind == 0) cubeCount else sphereCount
                b?.position(0); GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(norm)
                b?.let { GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 24, it); it.position(3); GLES20.glVertexAttribPointer(norm, 3, GLES20.GL_FLOAT, false, 24, it) }
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
            }
        }
    }
    private fun drawMesh(b: FloatBuffer?, count: Int, mode: Int, r: Float, g: Float, bl: Float, a: Float, p: Int, n: Int, ml: Int, modl: Int, cl: Int) {
        if (b == null) return
        Matrix.setIdentityM(model, 0); Matrix.multiplyMM(mv, 0, view, 0, model, 0); Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        GLES20.glUniformMatrix4fv(ml, 1, false, mvp, 0); GLES20.glUniformMatrix4fv(modl, 1, false, model, 0); GLES20.glUniform4f(cl, r, g, bl, a)
        GLES20.glEnableVertexAttribArray(p); GLES20.glEnableVertexAttribArray(n); b.position(0); GLES20.glVertexAttribPointer(p, 3, GLES20.GL_FLOAT, false, 24, b); b.position(3); GLES20.glVertexAttribPointer(n, 3, GLES20.GL_FLOAT, false, 24, b); GLES20.glDrawArrays(mode, 0, count)
    }
    private fun compile(type: Int, src: String): Int { val s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, src); GLES20.glCompileShader(s); return s }
    private fun link(v: Int, f: Int): Int { val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p); return p }
    private fun buffer(data: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    private fun makeCube(): FloatArray {
        val out = ArrayList<Float>()
        val faces = arrayOf(floatArrayOf(0f,0f,1f, -1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,1f,1f),floatArrayOf(0f,0f,-1f, 1f,-1f,-1f, -1f,-1f,-1f, -1f,1f,-1f, 1f,1f,-1f),floatArrayOf(1f,0f,0f, 1f,-1f,1f, 1f,-1f,-1f, 1f,1f,-1f, 1f,1f,1f),floatArrayOf(-1f,0f,0f, -1f,-1f,-1f, -1f,-1f,1f, -1f,1f,1f, -1f,1f,-1f),floatArrayOf(0f,1f,0f, -1f,1f,1f, 1f,1f,1f, 1f,1f,-1f, -1f,1f,-1f),floatArrayOf(0f,-1f,0f, -1f,-1f,-1f, 1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,1f))
        faces.forEach { face -> val normal=face.sliceArray(0..2); val verts=face.sliceArray(3 until 15); intArrayOf(0,1,2,0,2,3).forEach { i -> out.add(verts[i*3]);out.add(verts[i*3+1]);out.add(verts[i*3+2]);out.add(normal[0]);out.add(normal[1]);out.add(normal[2]) } }
        return out.toFloatArray()
    }
    private fun makeSphere(): FloatArray {
        val out=ArrayList<Float>(); val lat=12; val lon=18
        fun vertex(a: Float,b: Float) { val x=(cos(a)*sin(b)); val y=cos(b); val z=sin(a)*sin(b); out.add(x);out.add(y);out.add(z);out.add(x);out.add(y);out.add(z) }
        for(i in 0 until lat){ val b0=Math.PI.toFloat()*i/lat; val b1=Math.PI.toFloat()*(i+1)/lat; for(j in 0 until lon){val a0=(2*Math.PI*j/lon).toFloat();val a1=(2*Math.PI*(j+1)/lon).toFloat(); vertex(a0,b0);vertex(a1,b0);vertex(a1,b1);vertex(a0,b0);vertex(a1,b1);vertex(a0,b1)} }
        return out.toFloatArray()
    }
    private fun makeGrid(): FloatArray {
        val out=ArrayList<Float>(); for(i in -10..10){ val t=i.toFloat()*0.5f; listOf(floatArrayOf(-5f,0f,t,5f,0f,t),floatArrayOf(t,0f,-5f,t,0f,5f)).forEach { l -> out.add(l[0]);out.add(l[1]);out.add(l[2]);out.add(0f);out.add(1f);out.add(0f);out.add(l[3]);out.add(l[4]);out.add(l[5]);out.add(0f);out.add(1f);out.add(0f) } }; return out.toFloatArray()
    }
}
