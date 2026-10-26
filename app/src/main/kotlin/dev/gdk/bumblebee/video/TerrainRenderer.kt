package dev.gdk.bumblebee.video

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one frame of a ride video with OpenGL ES 3.0: sky, terrain, the route ribbon, the comet
 * marker and moment pins, then the overlay bitmap. Renders into a 4× multisampled framebuffer and
 * resolves into whatever is bound as the default framebuffer (the encoder's surface).
 *
 * Must be created, used and released on the thread that owns the GL context.
 */
class TerrainRenderer(
    private val plan: RideVideoPlan,
    private val look: VideoLook,
    val width: Int,
    val height: Int,
) {
    private val terrainProg = Program(TERRAIN_VS, TERRAIN_FS)
    private val skyProg = Program(SKY_VS, SKY_FS)
    private val ribbonProg = Program(RIBBON_VS, RIBBON_FS)
    private val spriteProg = Program(SPRITE_VS, SPRITE_FS)
    private val overlayProg = Program(SKY_VS, OVERLAY_FS)
    private val copyProg = Program(SKY_VS, COPY_FS)

    private class MeshBuffers(val vbo: Int, val nbo: Int, val ibo: Int, val count: Int)
    private val meshes = mutableListOf<MeshBuffers>()

    private val fbo: Int
    private val resolveFbo: Int
    private val resolveTex: Int
    private val colorRb: Int
    private val depthRb: Int
    private val overlayTex: Int
    private val ribbonVbo: Int
    private val spriteVbo: Int

    private val hMin = plan.land.minHeight
    private val hMax = max(plan.land.maxHeight, hMin + 1)
    private val cfg = plan.config
    private val fogDist = max(plan.land.base.width, plan.land.base.height) * 0.9 / cfg.haze

    init {
        val land = plan.land
        val ex = plan.exaggeration
        val base = TerrainMesh.build(land.base, ex, surface = land, sink = { x, y -> if (land.detailWeight(x, y) >= 1.0) 25.0 else 0.0 })
        upload(base)
        land.detail?.let { upload(TerrainMesh.build(it, ex, surface = land)) }

        val ids = IntArray(2)
        GLES30.glGenRenderbuffers(2, ids, 0)
        colorRb = ids[0]
        depthRb = ids[1]
        val samples = IntArray(1).also { GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, it, 0) }[0].coerceIn(0, 4)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, colorRb)
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, width, height)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRb)
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, width, height)
        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        fbo = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, colorRb)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depthRb)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer incomplete: $status" }
        // A plain texture to resolve the multisampled image into, then drawn to the output.
        val rt = IntArray(1)
        GLES30.glGenTextures(1, rt, 0)
        resolveTex = rt[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, resolveTex)
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, width, height)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glGenFramebuffers(1, f, 0)
        resolveFbo = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, resolveFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, resolveTex, 0)
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Resolve framebuffer incomplete" }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        overlayTex = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val b = IntArray(2)
        GLES30.glGenBuffers(2, b, 0)
        ribbonVbo = b[0]
        spriteVbo = b[1]
        checkGl("init")
    }

    private var overlayAllocated = false

    /**
     * Renders the frame at video time [t] with [overlay] (premultiplied ARGB, same size as the
     * video, or null) on top, ending with the result in the default framebuffer.
     */
    fun draw(t: Double, overlay: Bitmap?) {
        val cue = plan.timeline.cue(t)
        val pose = plan.camera(t)
        val dist = (pose.target - pose.eye).length
        val vm = ViewMath(pose, width.toDouble() / height, near = max(2.0, dist * 0.004), far = fogDist * 4)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        drawSky(vm)
        drawTerrain(vm)
        val upTo = if (cue.shot == Shot.INTRO) 0.0 else cue.distanceM
        drawRibbons(vm, upTo)
        drawSprites(vm, upTo, cue)
        drawPost()

        // Resolve multisampling into a texture, then draw that and the overlay to the output.
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resolveFbo)
        GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, resolveTex)
        copyProg.use()
        GLES30.glUniform1i(copyProg.loc("uTex"), 0)
        fullScreen(copyProg)
        if (overlay != null) drawOverlay(overlay)
        checkGl("draw")
    }

    private fun lookUniforms(p: Program) {
        val l = look.lightDir
        GLES30.glUniform3f(p.loc("uLight"), l.x.toFloat(), l.y.toFloat(), l.z.toFloat())
        GLES30.glUniform3fv(p.loc("uSun"), 1, look.sunColor.floats(), 0)
        GLES30.glUniform3fv(p.loc("uAmbient"), 1, look.ambient.floats(), 0)
        GLES30.glUniform3fv(p.loc("uZenith"), 1, look.zenith.floats(), 0)
        GLES30.glUniform3fv(p.loc("uHorizon"), 1, look.horizon.floats(), 0)
        GLES30.glUniform3fv(p.loc("uGlow"), 1, look.glow.floats(), 0)
        GLES30.glUniform3fv(p.loc("uHaze"), 1, look.haze.floats(), 0)
    }

    private fun cameraUniforms(p: Program, vm: ViewMath) {
        GLES30.glUniform3f(p.loc("uRight"), vm.right.x.toFloat(), vm.right.y.toFloat(), vm.right.z.toFloat())
        GLES30.glUniform3f(p.loc("uUp"), vm.up.x.toFloat(), vm.up.y.toFloat(), vm.up.z.toFloat())
        GLES30.glUniform3f(p.loc("uForward"), vm.forward.x.toFloat(), vm.forward.y.toFloat(), vm.forward.z.toFloat())
        GLES30.glUniform2f(p.loc("uTan"), (vm.tanHalfFov * vm.aspect).toFloat(), vm.tanHalfFov.toFloat())
    }

    private fun drawSky(vm: ViewMath) {
        skyProg.use()
        lookUniforms(skyProg)
        cameraUniforms(skyProg, vm)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        fullScreen(skyProg)
        GLES30.glDepthMask(true)
    }

    private fun drawTerrain(vm: ViewMath) {
        val p = terrainProg
        p.use()
        lookUniforms(p)
        GLES30.glUniformMatrix4fv(p.loc("uViewProj"), 1, false, vm.viewProj, 0)
        GLES30.glUniform3f(p.loc("uEye"), vm.eye.x.toFloat(), vm.eye.y.toFloat(), vm.eye.z.toFloat())
        GLES30.glUniform1f(p.loc("uExag"), plan.exaggeration.toFloat())
        GLES30.glUniform2f(p.loc("uHeights"), hMin.toFloat(), hMax.toFloat())
        GLES30.glUniform1f(p.loc("uFogDist"), fogDist.toFloat())
        GLES30.glUniform1f(p.loc("uContours"), if (cfg.contours) 1f else 0f)
        val b = plan.land.base
        GLES30.glUniform4f(p.loc("uArea"), b.x0.toFloat(), b.y0.toFloat(), b.width.toFloat(), b.height.toFloat())
        val stops = look.groundStops
        GLES30.glUniform1fv(p.loc("uStopT"), stops.size, FloatArray(stops.size) { stops[it].first.toFloat() }, 0)
        GLES30.glUniform3fv(p.loc("uStopC"), stops.size, stops.flatMap { it.second.floats().toList() }.toFloatArray(), 0)
        GLES30.glUniform3fv(p.loc("uRock"), 1, look.rock.floats(), 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LESS)
        for (m in meshes) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, m.vbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, m.nbo)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 12, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.ibo)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.count, GLES30.GL_UNSIGNED_INT, 0)
        }
        GLES30.glDisableVertexAttribArray(1)
    }

    /**
     * The route as camera-facing ribbons built each frame: a soft accent glow and a bright core
     * for the ridden part, a thin pale line for the rest. Widths are in pixels at any distance.
     */
    private fun drawRibbons(vm: ViewMath, upTo: Double) {
        val path = plan.path
        val split = path.index(upTo)
        val pxToWorld = 2.0 * vm.tanHalfFov / height
        val scale = height / 1920.0 * cfg.pathWidth
        val p = ribbonProg
        p.use()
        GLES30.glUniformMatrix4fv(p.loc("uViewProj"), 1, false, vm.viewProj, 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val accent = look.accent.floats()
        val glow = (look.pathGlow * cfg.glow).toFloat()
        // Still to ride.
        if (split < path.points.lastIndex) {
            strip(vm, split, path.points.lastIndex, 3.5 * scale, pxToWorld)?.let {
                drawStrip(it, floatArrayOf(1f, 1f, 1f), 0.38f, 1.4f)
            }
        }
        if (split > 0) {
            if (glow > 0f) strip(vm, 0, split, 34.0 * scale, pxToWorld)?.let { drawStrip(it, accent, (0.30f * glow).coerceAtMost(0.8f), 3.0f) }
            strip(vm, 0, split, 13.0 * scale, pxToWorld)?.let { drawStrip(it, accent, 0.85f, 1.6f) }
            strip(vm, 0, split, 4.0 * scale, pxToWorld)?.let { drawStrip(it, floatArrayOf(1f, 0.98f, 0.88f), 1f, 0.8f) }
        }
        GLES30.glDepthMask(true)
    }

    private class Strip(val buffer: FloatBuffer, val vertices: Int)

    /**
     * Vertices (x, y, z eye-relative; side -1..1; along metres; gap flag) for points [from]..[to],
     * thinned to at most [MAX_RIBBON_POINTS] and lifted a little so they sit on the ground.
     */
    private fun strip(vm: ViewMath, from: Int, to: Int, widthPx: Double, pxToWorld: Double): Strip? {
        val path = plan.path
        val n = to - from + 1
        if (n < 2) return null
        val stride = max(1, ceil(n / MAX_RIBBON_POINTS.toDouble()).toInt())
        val idx = ArrayList<Int>(n / stride + 2)
        var i = from
        while (i < to) {
            idx += i
            i += stride
        }
        idx += to
        val buf = floatBuffer(idx.size * 2 * 6)
        var count = 0
        for ((k, pi) in idx.withIndex()) {
            val pt = path.points[pi]
            val prev = path.points[idx[max(0, k - 1)]]
            val next = path.points[idx[min(idx.lastIndex, k + 1)]]
            val tangent = (next - prev).let { if (it.length < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalized() }
            val rel = pt - vm.eye
            val toCam = (rel * -1.0).normalized()
            val side = tangent.cross(toCam).normalized()
            val depth = max(rel.dot(vm.forward), 1.0)
            val half = widthPx / 2 * pxToWorld * depth
            // Pull toward the camera so the ribbon wins against the ground it lies on.
            val lifted = rel + toCam * min(depth * 0.004, 40.0)
            val gap = if (path.gap[pi]) 1f else 0f
            for (s in listOf(-1.0, 1.0)) {
                val v = lifted + side * (half * s)
                buf.put(v.x.toFloat()).put(v.y.toFloat()).put(v.z.toFloat())
                buf.put(s.toFloat()).put(path.along[pi].toFloat()).put(gap)
                count++
            }
        }
        buf.position(0)
        return Strip(buf, count)
    }

    private fun drawStrip(s: Strip, color: FloatArray, alpha: Float, falloff: Float) {
        val p = ribbonProg
        GLES30.glUniform3fv(p.loc("uColor"), 1, color, 0)
        GLES30.glUniform1f(p.loc("uAlpha"), alpha)
        GLES30.glUniform1f(p.loc("uFalloff"), falloff)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ribbonVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, s.vertices * 6 * 4, s.buffer, GLES30.GL_STREAM_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, s.vertices)
        GLES30.glDisableVertexAttribArray(1)
    }

    /** The comet head at the rider, and a small pin at each moment already passed or held. */
    private fun drawSprites(vm: ViewMath, upTo: Double, cue: Cue) {
        val scale = height / 1920.0
        val sprites = ArrayList<FloatArray>()
        val accent = look.accent.floats()
        for (m in plan.timeline.moments) {
            if (m.distanceM > upTo + 1) continue
            val pos = plan.path.at(m.distanceM) - vm.eye
            val active = cue.moment == m
            sprites += floatArrayOf(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat(), (if (active) 46 else 26) * scale.toFloat(), accent[0], accent[1], accent[2], 0.9f)
        }
        if (cue.shot != Shot.INTRO || cue.progress > 0.15) {
            val head = plan.path.at(upTo) - vm.eye
            val pulse = 1.0 + 0.08 * kotlin.math.sin(cue.progress * 40)
            sprites += floatArrayOf(head.x.toFloat(), head.y.toFloat(), head.z.toFloat(), (110 * scale * pulse * look.pathGlow * (0.6 + 0.4 * cfg.glow) * cfg.pathWidth.coerceIn(0.8, 1.5)).toFloat(), accent[0], accent[1], accent[2], 1f)
        }
        if (sprites.isEmpty()) return
        val p = spriteProg
        p.use()
        GLES30.glUniformMatrix4fv(p.loc("uViewProj"), 1, false, vm.viewProj, 0)
        GLES30.glUniform2f(p.loc("uPixel"), 2f / width, 2f / height)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        // Sprites stay visible over nearby ground but hide behind ridges.
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        val buf = floatBuffer(sprites.size * 6 * 10)
        val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to -1f, 1f to 1f, -1f to 1f)
        for (s in sprites) for ((cx, cy) in corners) {
            buf.put(s[0]).put(s[1]).put(s[2]).put(cx).put(cy).put(s[3]).put(s[4]).put(s[5]).put(s[6]).put(s[7])
        }
        buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, spriteVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, sprites.size * 6 * 10 * 4, buf, GLES30.GL_STREAM_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 40, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 40, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, 40, 24)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, sprites.size * 6)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDepthMask(true)
    }

    /** Vignette and a light film grain over the scene, before the overlay. */
    private var frameNo = 0
    private val postProg = Program(SKY_VS, POST_FS)

    private fun drawPost() {
        if (!cfg.vignette && !cfg.grain) return
        postProg.use()
        GLES30.glUniform1f(postProg.loc("uVignette"), if (cfg.vignette) 1f else 0f)
        GLES30.glUniform1f(postProg.loc("uGrain"), if (cfg.grain) 1f else 0f)
        GLES30.glUniform1f(postProg.loc("uSeed"), (frameNo++ % 97).toFloat())
        GLES30.glUniform2f(postProg.loc("uSize"), width.toFloat(), height.toFloat())
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        // Output is (grain, grain, grain, factor): colour × factor + grain.
        GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
        fullScreen(postProg)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawOverlay(bmp: Bitmap) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, overlayTex)
        if (!overlayAllocated) {
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            overlayAllocated = true
        } else {
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bmp)
        }
        overlayProg.use()
        GLES30.glUniform1i(overlayProg.loc("uTex"), 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        // Android bitmaps are premultiplied.
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        fullScreen(overlayProg)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** A full-screen triangle; the vertex shader makes it from gl_VertexID. */
    private fun fullScreen(@Suppress("UNUSED_PARAMETER") p: Program) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    private fun upload(mesh: TerrainMesh) {
        val b = IntArray(3)
        GLES30.glGenBuffers(3, b, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.positions.size * 4, floatBuffer(mesh.positions), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.normals.size * 4, floatBuffer(mesh.normals), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, b[2])
        val ib = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(mesh.indices).position(0)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, ib, GLES30.GL_STATIC_DRAW)
        meshes += MeshBuffers(b[0], b[1], b[2], mesh.indices.size)
    }

    /**
     * Terrain positions are absolute, but drawn relative to the eye: the vertex shader subtracts
     * uEye before projecting. Ribbons and sprites are built eye-relative on the CPU.
     */
    fun release() {
        meshes.forEach { GLES30.glDeleteBuffers(3, intArrayOf(it.vbo, it.nbo, it.ibo), 0) }
        GLES30.glDeleteBuffers(2, intArrayOf(ribbonVbo, spriteVbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(overlayTex), 0)
        GLES30.glDeleteFramebuffers(2, intArrayOf(fbo, resolveFbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(resolveTex), 0)
        GLES30.glDeleteRenderbuffers(2, intArrayOf(colorRb, depthRb), 0)
        listOf(terrainProg, skyProg, ribbonProg, spriteProg, overlayProg, postProg, copyProg).forEach { it.release() }
    }

    private class Program(vs: String, fs: String) {
        val id: Int
        private val locs = HashMap<String, Int>()

        init {
            val v = compile(GLES30.GL_VERTEX_SHADER, vs)
            val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
            id = GLES30.glCreateProgram()
            GLES30.glAttachShader(id, v)
            GLES30.glAttachShader(id, f)
            GLES30.glLinkProgram(id)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { "Link failed: " + GLES30.glGetProgramInfoLog(id) }
            GLES30.glDeleteShader(v)
            GLES30.glDeleteShader(f)
        }

        fun use() = GLES30.glUseProgram(id)
        fun loc(name: String): Int = locs.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
        fun release() = GLES30.glDeleteProgram(id)

        private fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader compile failed: " + GLES30.glGetShaderInfoLog(s) }
            return s
        }
    }

    companion object {
        private const val MAX_RIBBON_POINTS = 6000

        private fun floatBuffer(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private fun floatBuffer(a: FloatArray): FloatBuffer = floatBuffer(a.size).also { it.put(a).position(0) }

        private fun checkGl(where: String) {
            val e = GLES30.glGetError()
            check(e == GLES30.GL_NO_ERROR) { "GL error 0x${Integer.toHexString(e)} in $where" }
        }

        private const val COMMON = """
            uniform vec3 uLight;
            uniform vec3 uSun;
            uniform vec3 uAmbient;
            uniform vec3 uZenith;
            uniform vec3 uHorizon;
            uniform vec3 uGlow;
            uniform vec3 uHaze;
            vec3 skyColor(vec3 ray) {
                float up = clamp(ray.z, -1.0, 1.0);
                vec3 c = mix(uZenith, uHorizon, exp(-max(up, 0.0) * 5.0));
                float toSun = max(dot(ray, uLight), 0.0);
                return c + uGlow * (0.55 * pow(toSun, 6.0) + 0.9 * pow(toSun, 80.0));
            }
            vec3 toSrgb(vec3 c) { return pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2)); }
        """

        // A triangle covering the screen, from gl_VertexID, with uv 0..1.
        const val SKY_VS = """#version 300 es
            out vec2 vUv;
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                vUv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
        """

        const val SKY_FS = """#version 300 es
            precision highp float;
            in vec2 vUv;
            out vec4 outColor;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform vec2 uTan;
            $COMMON
            void main() {
                vec2 ndc = vUv * 2.0 - 1.0;
                vec3 ray = normalize(uForward + uRight * ndc.x * uTan.x + uUp * ndc.y * uTan.y);
                outColor = vec4(toSrgb(skyColor(ray)), 1.0);
            }
        """

        const val TERRAIN_VS = """#version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            uniform mat4 uViewProj;
            uniform vec3 uEye;
            out vec3 vWorld;
            out vec3 vRel;
            out vec3 vNormal;
            void main() {
                vec3 rel = aPos - uEye;
                vWorld = aPos;
                vRel = rel;
                vNormal = aNormal;
                gl_Position = uViewProj * vec4(rel, 1.0);
            }
        """

        const val TERRAIN_FS = """#version 300 es
            precision highp float;
            in vec3 vWorld;
            in vec3 vRel;
            in vec3 vNormal;
            out vec4 outColor;
            uniform float uExag;
            uniform vec2 uHeights;
            uniform float uFogDist;
            uniform vec4 uArea;
            uniform float uStopT[6];
            uniform vec3 uStopC[6];
            uniform vec3 uRock;
            uniform float uContours;
            $COMMON
            vec3 ground(float t, float slope) {
                vec3 c = uStopC[0];
                for (int i = 0; i < 5; i++) {
                    float a = uStopT[i];
                    float b = uStopT[i + 1];
                    if (t >= a) c = mix(uStopC[i], uStopC[i + 1], clamp((t - a) / (b - a), 0.0, 1.0));
                }
                return mix(c, uRock, clamp((slope - 0.40) * 1.6, 0.0, 0.6));
            }
            void main() {
                vec3 n = normalize(vNormal);
                float h = vWorld.z / uExag;
                float t = (h - uHeights.x) / (uHeights.y - uHeights.x);
                vec3 c = ground(t, 1.0 - n.z);
                float ndl = dot(n, uLight);
                float diffuse = max(ndl, 0.0);
                float wrap = max((ndl + 0.3) / 1.3, 0.0);
                c *= uAmbient * (0.6 + 0.4 * n.z) + uSun * (0.9 * diffuse + 0.15 * wrap);
                float dist = length(vRel);
                // Contours every 100 m, fading with distance.
                float f = fract(h / 100.0);
                float lineM = min(f, 1.0 - f) * 100.0;
                float widthM = 1.2 + dist / 2500.0;
                if (lineM < widthM) c *= 1.0 - 0.22 * uContours * (1.0 - lineM / widthM) * exp(-dist / 30000.0);
                float valley = exp(-(h - uHeights.x) / 220.0) * 0.16 * (1.0 - exp(-dist / 5000.0));
                float fog = 1.0 - exp(-pow(dist / uFogDist, 1.6));
                c = mix(c, uHaze, clamp(fog * 0.9 + valley, 0.0, 0.95));
                // Melt the outer edge of the area into the sky.
                vec2 a = (vWorld.xy - uArea.xy) / uArea.zw;
                float edge = min(min(a.x, 1.0 - a.x), min(a.y, 1.0 - a.y));
                float e = clamp(1.0 - edge / 0.12, 0.0, 1.0);
                vec3 ray = normalize(vRel);
                vec3 sky = mix(uHorizon, uHaze, 0.5) + uGlow * 0.4 * pow(max(dot(ray, uLight), 0.0), 6.0);
                c = mix(c, sky, e * e);
                outColor = vec4(toSrgb(c), 1.0);
            }
        """

        const val RIBBON_VS = """#version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aInfo;
            uniform mat4 uViewProj;
            out vec3 vInfo;
            void main() {
                vInfo = aInfo;
                gl_Position = uViewProj * vec4(aPos, 1.0);
            }
        """

        const val RIBBON_FS = """#version 300 es
            precision highp float;
            in vec3 vInfo;
            out vec4 outColor;
            uniform vec3 uColor;
            uniform float uAlpha;
            uniform float uFalloff;
            void main() {
                // GPS gaps are dashed: 40 m on, 40 m off.
                if (vInfo.z > 0.5 && fract(vInfo.y / 80.0) > 0.5) discard;
                float s = abs(vInfo.x);
                float a = uAlpha * exp(-s * s * uFalloff * uFalloff) * smoothstep(1.0, 0.85, s);
                outColor = vec4(pow(uColor, vec3(1.0 / 2.2)), a);
            }
        """

        const val SPRITE_VS = """#version 300 es
            layout(location = 0) in vec3 aCenter;
            layout(location = 1) in vec3 aCorner;
            layout(location = 2) in vec4 aColor;
            uniform mat4 uViewProj;
            uniform vec2 uPixel;
            out vec2 vUv;
            out vec4 vColor;
            void main() {
                vec4 c = uViewProj * vec4(aCenter, 1.0);
                // Size in pixels whatever the distance; pulled slightly forward in depth.
                c.xy += aCorner.xy * aCorner.z * 0.5 * uPixel * c.w;
                c.z -= 0.002 * c.w;
                gl_Position = c;
                vUv = aCorner.xy;
                vColor = aColor;
            }
        """

        const val SPRITE_FS = """#version 300 es
            precision highp float;
            in vec2 vUv;
            in vec4 vColor;
            out vec4 outColor;
            void main() {
                float r = length(vUv);
                if (r > 1.0) discard;
                float glow = exp(-r * r * 9.0);
                float core = smoothstep(0.16, 0.10, r);
                vec3 c = mix(pow(vColor.rgb, vec3(1.0 / 2.2)), vec3(1.0), core);
                outColor = vec4(c, max(glow * 0.75, core) * vColor.a);
            }
        """

        const val POST_FS = """#version 300 es
            precision highp float;
            in vec2 vUv;
            out vec4 outColor;
            uniform float uSeed;
            uniform vec2 uSize;
            uniform float uVignette;
            uniform float uGrain;
            float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233)) + uSeed * 7.13) * 43758.5453); }
            void main() {
                vec2 d = (vUv - 0.5) * vec2(uSize.x / uSize.y, 1.0);
                float v = 1.0 - 0.38 * uVignette * pow(length(d) / 0.62, 2.2);
                // Grain can't be negative in an 8-bit target, so darken a touch to stay neutral.
                float grain = hash(floor(vUv * uSize)) * 0.03 * uGrain;
                outColor = vec4(vec3(grain), clamp(v, 0.0, 1.0) * (1.0 - 0.015 * uGrain));
            }
        """

        const val COPY_FS = """#version 300 es
            precision mediump float;
            in vec2 vUv;
            out vec4 outColor;
            uniform sampler2D uTex;
            void main() { outColor = vec4(texture(uTex, vUv).rgb, 1.0); }
        """

        const val OVERLAY_FS = """#version 300 es
            precision mediump float;
            in vec2 vUv;
            out vec4 outColor;
            uniform sampler2D uTex;
            void main() {
                // Bitmaps are stored top row first.
                outColor = texture(uTex, vec2(vUv.x, 1.0 - vUv.y));
            }
        """
    }
}
