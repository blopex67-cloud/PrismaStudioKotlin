package com.chapiodara.prismastudio

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Color
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

private data class SceneObject(val kind: Int, var x: Float = 0f, var y: Float = 1f, var z: Float = 0f,
                               var rotation: Float = 0f, var scale: Float = 1f)

class MainActivity : Activity() {
    private lateinit var renderer: SceneRenderer
    private lateinit var glView: GLSurfaceView
    private lateinit var status: TextView
    private lateinit var scaleDetector: ScaleGestureDetector
    private val bg = Color.rgb(18, 19, 23)
    private val panel = Color.rgb(29, 31, 37)
    private val accent = Color.rgb(255, 55, 74)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        renderer = SceneRenderer(this)
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean { renderer.zoom(detector.scaleFactor); return true }
        })
        loadScene()
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { renderer.beginDrag(event.x, event.y); true }
                    MotionEvent.ACTION_POINTER_DOWN -> true
                    MotionEvent.ACTION_MOVE -> { if (!scaleDetector.isInProgress && event.pointerCount == 1) renderer.drag(event.x, event.y); true }
                    else -> true
                }
            }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(5), dp(10), dp(5)); setBackgroundColor(panel)
        }
        val title = TextView(this).apply { text = "PRISMA STUDIO 3D"; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, 1) }
        top.addView(title, LinearLayout.LayoutParams(0, dp(46), 1f))
        status = TextView(this).apply { text = "Escena · 1 objeto"; setTextColor(Color.LTGRAY); textSize = 12f; gravity = Gravity.CENTER_VERTICAL }
        top.addView(status)
        root.addView(top)
        root.addView(glView, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(5)); setBackgroundColor(panel) }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(makeButton("＋ Cubo", accent) { renderer.add(0); refresh() })
        row1.addView(makeButton("＋ Esfera") { renderer.add(1); refresh() })
        row1.addView(makeButton("Mover X") { renderer.transform { it.x += 0.25f }; refresh() })
        row1.addView(makeButton("Girar") { renderer.transform { it.rotation += 15f }; refresh() })
        row1.addView(makeButton("Escala") { renderer.transform { it.scale = if (it.scale > 1.35f) 0.7f else it.scale + 0.25f }; refresh() })
        row1.addView(makeButton("Siguiente") { renderer.selectNext(); refresh() })
        row1.addView(makeButton("Borrar") { renderer.removeSelected(); refresh() })
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(3), dp(4), 0) }
        val hint = TextView(this).apply { text = "Arrastra el visor para orbitar · pellizca para acercar"; setTextColor(Color.LTGRAY); textSize = 11f }
        row2.addView(hint, LinearLayout.LayoutParams(0, dp(30), 1f))
        row2.addView(makeButton("Guardar") { saveScene() })
        bar.addView(row1)
        bar.addView(row2)
        root.addView(bar)
        setContentView(root)
    }

    private fun makeButton(label: String, color: Int = Color.rgb(53, 56, 64), action: () -> Unit): Button =
        Button(this).apply {
            text = label; textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(color); isAllCaps = false
            setPadding(dp(4), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) }
            setOnClickListener { action() }
        }

    private fun refresh() { status.text = "Escena · ${renderer.objectCount()} objeto${if (renderer.objectCount() == 1) "" else "s"}" }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadScene() {
        try {
            val file = File(filesDir, "escena.json")
            if (!file.exists()) return
            val json = JSONObject(file.readText()).getJSONArray("objects")
            val items = mutableListOf<SceneObject>()
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                items.add(SceneObject(if (item.optString("type") == "esfera") 1 else 0,
                    item.optDouble("x", 0.0).toFloat(), item.optDouble("y", 1.0).toFloat(), item.optDouble("z", 0.0).toFloat(),
                    item.optDouble("rotation", 0.0).toFloat(), item.optDouble("scale", 1.0).toFloat()))
            }
            if (items.isNotEmpty()) renderer.replace(items)
        } catch (_: Exception) { }
    }

    private fun saveScene() {
        renderer.snapshot { list ->
            try {
                val arr = JSONArray()
                list.forEach { o -> arr.put(JSONObject().put("type", if (o.kind == 0) "cubo" else "esfera").put("x", o.x).put("y", o.y).put("z", o.z).put("rotation", o.rotation).put("scale", o.scale)) }
                File(filesDir, "escena.json").writeText(JSONObject().put("name", "Mi escena").put("objects", arr).toString(2))
                runOnUiThread { Toast.makeText(this, "Escena guardada en el dispositivo", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { runOnUiThread { Toast.makeText(this, "No se pudo guardar", Toast.LENGTH_SHORT).show() } }
        }
    }

    override fun onResume() { super.onResume(); if (::glView.isInitialized) glView.onResume() }
    override fun onPause() { if (::glView.isInitialized) glView.onPause(); super.onPause() }
}

private class SceneRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private val objects = mutableListOf(SceneObject(0))
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
    private var pitch = 23f
    private var distance = 6f
    private var lastX = 0f
    private var lastY = 0f
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private val light = floatArrayOf(0.4f, 1f, 0.7f)

    @Synchronized fun add(kind: Int) { objects.add(SceneObject(kind, x = ((objects.size % 3) - 1) * 0.7f)); selected = objects.lastIndex }
    @Synchronized fun removeSelected() { if (objects.size > 1) { objects.removeAt(selected.coerceIn(0, objects.lastIndex)); selected = (objects.size - 1).coerceAtLeast(0) } }
    @Synchronized fun transform(f: (SceneObject) -> Unit) { if (objects.isNotEmpty()) f(objects[selected.coerceIn(0, objects.lastIndex)]) }
    @Synchronized fun objectCount() = objects.size
    @Synchronized fun selectNext() { if (objects.isNotEmpty()) selected = (selected + 1) % objects.size }
    @Synchronized fun replace(items: List<SceneObject>) { objects.clear(); objects.addAll(items); selected = 0 }
    @Synchronized fun zoom(factor: Float) { if (factor > 0f) distance = (distance / factor).coerceIn(2.2f, 12f) }
    @Synchronized fun snapshot(done: (List<SceneObject>) -> Unit) { done(objects.map { it.copy() }) }
    @Synchronized fun beginDrag(x: Float, y: Float) { lastX = x; lastY = y }
    @Synchronized fun drag(x: Float, y: Float) { yaw += (x - lastX) * 0.45f; pitch = (pitch + (y - lastY) * 0.35f).coerceIn(5f, 78f); lastX = x; lastY = y }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.075f, 0.08f, 0.095f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        val vs = """uniform mat4 uMvp; uniform mat4 uModel; attribute vec3 aPos; attribute vec3 aNormal; varying vec3 vN; void main(){ gl_Position=uMvp*vec4(aPos,1.0); vN=mat3(uModel)*aNormal; }"""
        val fs = """precision mediump float; uniform vec4 uColor; uniform vec3 uLight; varying vec3 vN; void main(){ float l=max(dot(normalize(vN),normalize(uLight)),0.0); gl_FragColor=vec4(uColor.rgb*(0.34+0.66*l),uColor.a); }"""
        program = link(compile(GLES20.GL_VERTEX_SHADER, vs), compile(GLES20.GL_FRAGMENT_SHADER, fs))
        val c = makeCube(); cube = buffer(c); cubeCount = c.size / 6
        val s = makeSphere(); sphere = buffer(s); sphereCount = s.size / 6
        val g = makeGrid(); grid = buffer(g); gridCount = g.size / 6
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h.coerceAtLeast(1); GLES20.glViewport(0, 0, w, h); Matrix.perspectiveM(projection, 0, 45f, w.toFloat() / height, 0.1f, 100f) }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val yr = Math.toRadians(yaw.toDouble()); val pr = Math.toRadians(pitch.toDouble())
        val ex = (distance * cos(pr) * sin(yr)).toFloat(); val ey = (distance * sin(pr)).toFloat(); val ez = (distance * cos(pr) * cos(yr)).toFloat()
        Matrix.setLookAtM(view, 0, ex, ey + 0.4f, ez, 0f, 0.65f, 0f, 0f, 1f, 0f)
        GLES20.glUseProgram(program)
        val pos = GLES20.glGetAttribLocation(program, "aPos"); val norm = GLES20.glGetAttribLocation(program, "aNormal")
        val mvpLoc = GLES20.glGetUniformLocation(program, "uMvp"); val modelLoc = GLES20.glGetUniformLocation(program, "uModel"); val colorLoc = GLES20.glGetUniformLocation(program, "uColor"); val lightLoc = GLES20.glGetUniformLocation(program, "uLight")
        GLES20.glUniform3fv(lightLoc, 1, light, 0)
        drawMesh(grid, gridCount, GLES20.GL_LINES, 0.22f, 0.24f, 0.28f, 1f, pos, norm, mvpLoc, modelLoc, colorLoc)
        synchronized(this) {
            objects.forEachIndexed { i, o ->
                Matrix.setIdentityM(model, 0); Matrix.translateM(model, 0, o.x, o.y, o.z); Matrix.rotateM(model, 0, o.rotation, 0f, 1f, 0f); Matrix.scaleM(model, 0, o.scale, o.scale, o.scale)
                Matrix.multiplyMM(mv, 0, view, 0, model, 0); Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
                GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0); GLES20.glUniformMatrix4fv(modelLoc, 1, false, model, 0)
                val hot = i == selected
                GLES20.glUniform4f(colorLoc, if (hot) 1f else 0.22f, if (hot) 0.20f else 0.60f, if (hot) 0.24f else 0.96f, 1f)
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
        val faces = arrayOf(
            floatArrayOf(0f,0f,1f, -1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,1f,1f),
            floatArrayOf(0f,0f,-1f, 1f,-1f,-1f, -1f,-1f,-1f, -1f,1f,-1f, 1f,1f,-1f),
            floatArrayOf(1f,0f,0f, 1f,-1f,1f, 1f,-1f,-1f, 1f,1f,-1f, 1f,1f,1f),
            floatArrayOf(-1f,0f,0f, -1f,-1f,-1f, -1f,-1f,1f, -1f,1f,1f, -1f,1f,-1f),
            floatArrayOf(0f,1f,0f, -1f,1f,1f, 1f,1f,1f, 1f,1f,-1f, -1f,1f,-1f),
            floatArrayOf(0f,-1f,0f, -1f,-1f,-1f, 1f,-1f,-1f, 1f,-1f,1f, -1f,-1f,1f))
        faces.forEach { face -> val normal=face.sliceArray(0..2); val verts=face.sliceArray(3 until 15); val idx=intArrayOf(0,1,2,0,2,3); idx.forEach { i -> out.add(verts[i*3]);out.add(verts[i*3+1]);out.add(verts[i*3+2]);out.add(normal[0]);out.add(normal[1]);out.add(normal[2]) } }
        return out.toFloatArray()
    }
    private fun makeSphere(): FloatArray {
        val out=ArrayList<Float>(); val lat=12; val lon=18
        fun vertex(a: Float,b: Float) { val x=(cos(a)*sin(b)); val y=cos(b); val z=sin(a)*sin(b); out.add(x);out.add(y);out.add(z);out.add(x);out.add(y);out.add(z) }
        for(i in 0 until lat){ val b0=Math.PI.toFloat()*i/lat; val b1=Math.PI.toFloat()*(i+1)/lat
            for(j in 0 until lon){val a0=(2*Math.PI*j/lon).toFloat();val a1=(2*Math.PI*(j+1)/lon).toFloat(); vertex(a0,b0);vertex(a1,b0);vertex(a1,b1);vertex(a0,b0);vertex(a1,b1);vertex(a0,b1)} }
        return out.toFloatArray()
    }
    private fun makeGrid(): FloatArray {
        val out=ArrayList<Float>(); for(i in -10..10){ val t=i.toFloat()*0.5f
            listOf(floatArrayOf(-5f,0f,t,5f,0f,t),floatArrayOf(t,0f,-5f,t,0f,5f)).forEach { l ->
                out.add(l[0]);out.add(l[1]);out.add(l[2]);out.add(0f);out.add(1f);out.add(0f); out.add(l[3]);out.add(l[4]);out.add(l[5]);out.add(0f);out.add(1f);out.add(0f)
            }
        }; return out.toFloatArray()
    }
}
