package dev.gdk.bumblebee.video

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * The Studio's live preview: the export's own [TerrainRenderer] and [VideoOverlay], drawn at the
 * view's size, so what the rider sees is what they'll get. Set [plan] and [time] from any thread;
 * the next frame picks them up, rebuilding the GPU side only when the plan or size changes.
 */
class PreviewRenderer(private val fonts: VideoFonts) : GLSurfaceView.Renderer {
    @Volatile var plan: RideVideoPlan? = null
    @Volatile var look: VideoLook? = null
    @Volatile var accent: Int = 0
    @Volatile var time: Double = 0.0

    /** Called on the GL thread if building the scene fails, so the screen can say so. */
    var onError: (Throwable) -> Unit = {}

    private var width = 0
    private var height = 0
    private var built: RideVideoPlan? = null
    private var builtW = 0
    private var builtH = 0
    private var renderer: TerrainRenderer? = null
    private var overlay: VideoOverlay? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context: anything built for the old one is gone.
        renderer = null
        overlay?.release()
        overlay = null
        built = null
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
    }

    override fun onDrawFrame(gl: GL10?) {
        val p = plan
        val l = look
        if (p == null || l == null || width == 0) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }
        try {
            if (p !== built || width != builtW || height != builtH) {
                renderer?.release()
                overlay?.release()
                renderer = TerrainRenderer(p, l, width, height)
                overlay = VideoOverlay(p, fonts, accent, width, height)
                built = p
                builtW = width
                builtH = height
            }
            val t = time.coerceIn(0.0, p.totalS)
            renderer?.draw(t, overlay?.draw(t))
        } catch (e: Throwable) {
            renderer = null
            built = p
            onError(e)
        }
    }

    companion object {
        /** A GLSurfaceView set up for OpenGL ES 3 that only redraws when asked. */
        fun view(context: Context, renderer: PreviewRenderer): GLSurfaceView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
}
