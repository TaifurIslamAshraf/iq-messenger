package com.iqsocial.iq_message.data.call

import android.graphics.Matrix
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import livekit.org.webrtc.GlTextureFrameBuffer
import livekit.org.webrtc.TextureBufferImpl
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoProcessor
import livekit.org.webrtc.VideoSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The camera, brightened and softened, between the lens and everything else.
 *
 * A `VideoProcessor` sits where it does for a reason: the capturer hands frames to
 * it, and it hands them to the video source - so what comes out of here is what
 * gets encoded for the other phone **and** what the self-view draws. There is no
 * way for the two to disagree, and nothing extra is sent to make them agree.
 *
 * **On the GPU, on the thread the frame arrived on.** A camera frame is an OES
 * texture living in the capturer's EGL context, and that context is current on the
 * capture thread when [onFrameCaptured] runs. So the filter is a single shader
 * pass into a framebuffer of our own: no readback, no copy through the CPU, and no
 * second EGL context to keep in step. Pulling the pixels down to filter them in
 * Java would cost more per frame than the whole encode.
 *
 * **Off is free.** With both settings at zero the frame is passed straight on,
 * untouched, and no GL call is made at all.
 *
 * What it cannot do: the camera's own exposure. WebRTC owns the capture session
 * and offers no way in, so "brightness" here lifts the picture after the fact -
 * which brightens the noise with it. It is the honest limit of doing this in a
 * shader rather than in the sensor.
 */
class CameraFilter : VideoProcessor {

    private var sink: VideoSink? = null

    /** The shader programs, built on the capture thread the first time one is needed. */
    private var oesProgram: Program? = null
    private var flatProgram: Program? = null

    /**
     * Somewhere to draw, more than one.
     *
     * A frame handed downstream is not finished with when `onFrame` returns: the
     * encoder reads it on its own schedule. Drawing the next frame into the same
     * texture would rewrite a picture somebody is still reading, which looks like
     * tearing or a flash of the wrong frame. Each slot is taken when it is drawn
     * into and given back when the frame it belongs to is released.
     */
    private var slots = Array(SLOTS) { Slot() }

    /**
     * The GL context everything above was made in.
     *
     * This filter lives as long as the process - one for every call - but each
     * call's capturer brings an EGL context of its own, and a texture or a
     * framebuffer is a number that only means something inside the context that
     * made it. The slots were made once and kept: from the second call on they
     * held the first call's numbers, and `setSize` at an unchanged size does
     * nothing, so nothing ever noticed. The filter drew into a framebuffer that
     * did not exist and handed downstream a texture name that, in the new
     * context, was nothing or somebody else's. The encoder reading it raised a GL
     * error inside native code, and WebRTC answers that by aborting the process:
     * the SIGABRT Sentry caught here.
     *
     * So every frame checks which context is current, and a different one - or a
     * capturer that has stopped since - means building afresh.
     */
    private var madeIn: EGLContext? = null

    /** Passes in this context that ended in a GL error. Reset with the context. */
    private var failures = 0

    /** Set when the capturer stops; the next frame, on its own thread, rebuilds. */
    @Volatile
    private var stale = true

    private val quad: FloatBuffer = floats(
        // x, y, u, v - a triangle strip covering the whole target
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f
    )

    private val texMatrix = FloatArray(16)

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    override fun onCapturerStarted(success: Boolean) = Unit

    override fun onCapturerStopped() {
        // Deliberately not touching the GL objects here: this runs on whatever
        // thread stopped the capturer, and GL objects belong to the thread whose
        // context made them. They go when that context does. Only marked, so the
        // next frame - on the capture thread, in whatever context it brings -
        // starts again from nothing. A new context can even reuse the old one's
        // handle, which is why the context comparison alone is not enough.
        stale = true
    }

    /**
     * Forgets every GL object made in some other context and starts again in this
     * one.
     *
     * Forgotten, not deleted: their names belong to a context that is gone, and in
     * this one the same numbers may name objects that are not ours - deleting them
     * would delete the camera's own texture. A slot still lent to a frame in
     * flight is simply dropped; its frame gives it back to nobody when released.
     */
    private fun adopt(context: EGLContext) {
        oesProgram = null
        flatProgram = null
        slots = Array(SLOTS) { Slot() }
        // A capturer stopping is a fresh start; a failed pass marking itself stale
        // is not, or the limit below would never be reached.
        if (context != madeIn) failures = 0
        madeIn = context
        stale = false
    }

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = sink ?: return
        val buffer = frame.buffer

        // Nothing asked for, or a frame this cannot work on (an I420 buffer from a
        // software capturer). Either way it goes on untouched rather than being
        // dropped - a filter that loses frames is worse than no filter.
        if (CameraLook.off || buffer !is TextureBufferImpl) {
            out.onFrame(frame)
            return
        }

        val filtered = runCatching { draw(buffer) }.getOrElse {
            Log.w(TAG, "Filter failed; sending the frame as it came", it)
            null
        }
        if (filtered == null) {
            out.onFrame(frame)
            return
        }
        // Handed on, and then **let go of**.
        //
        // A buffer is born holding one reference - this side's - and anything
        // downstream that wants to keep the frame takes its own. Not releasing
        // ours left the count permanently above zero, so the release callback
        // never ran and the slot it was drawn into was never given back. With
        // three slots that is three filtered frames and then nothing: from the
        // fourth frame on, [draw] found no free slot, returned null, and every
        // frame went through untouched. The filter was not broken, it was
        // starved - and what it looked like from outside was brightness and
        // smoothing that simply did nothing.
        //
        // The frame that came in is not touched: the capturer owns that one.
        val sent = VideoFrame(filtered, frame.rotation, frame.timestampNs)
        out.onFrame(sent)
        sent.release()
    }

    private fun draw(buffer: TextureBufferImpl): VideoFrame.Buffer? {
        val width = buffer.width
        val height = buffer.height
        if (width <= 0 || height <= 0) return null

        // No context current is no GL at all: pass the frame through untouched.
        val current = EGL14.eglGetCurrentContext()
        if (current == null || current == EGL14.EGL_NO_CONTEXT) return null
        if (stale || current != madeIn) adopt(current)
        // A context where the pass keeps failing gets no more attempts: rebuilding
        // the shaders every frame to fail again would cost more than the filter is
        // worth. Plain frames until the next context.
        if (failures >= MAX_FAILURES) return null

        // Errors left over from whoever drew last on this thread - the camera's
        // own texture update - are theirs, not ours. Cleared so the check after
        // drawing reads only what this pass did. Bounded: a context that is lost
        // reports an error for ever.
        for (attempt in 0 until MAX_STALE_ERRORS) {
            if (GLES20.glGetError() == GLES20.GL_NO_ERROR) break
        }

        val slot = slots.firstOrNull { it.free } ?: return null
        val target = slot.take(width, height)

        val oes = buffer.type == VideoFrame.TextureBuffer.Type.OES
        val program = program(oes) ?: run {
            slot.give()
            return null
        }

        // The capturer's transform matrix says how the texture is laid out - which
        // way up, and whether it is mirrored. Applied here, so the picture that
        // leaves this is the right way round and the output needs no matrix of its
        // own.
        matrixFrom(buffer.transformMatrix)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target.frameBufferId)
        // A framebuffer that is not complete cannot be drawn into, and a texture
        // that was not drawn into must not go downstream: the original frame goes
        // instead. Cheaper than a crash in the encoder, which is where a bad
        // texture would otherwise be found.
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) !=
            GLES20.GL_FRAMEBUFFER_COMPLETE
        ) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            slot.give()
            return null
        }
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program.id)

        val textureTarget =
            if (oes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, buffer.textureId)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glUniform1i(program.texture, 0)

        GLES20.glUniformMatrix4fv(program.matrix, 1, false, texMatrix, 0)
        GLES20.glUniform2f(program.texel, 1f / width, 1f / height)
        GLES20.glUniform1f(program.brightness, CameraLook.brightnessNow)
        GLES20.glUniform1f(program.smooth, CameraLook.smoothNow)

        quad.position(0)
        GLES20.glVertexAttribPointer(program.position, 2, GLES20.GL_FLOAT, false, STRIDE, quad)
        GLES20.glEnableVertexAttribArray(program.position)
        quad.position(2)
        GLES20.glVertexAttribPointer(program.coords, 2, GLES20.GL_FLOAT, false, STRIDE, quad)
        GLES20.glEnableVertexAttribArray(program.coords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(program.position)
        GLES20.glDisableVertexAttribArray(program.coords)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        // Anything this pass got wrong, the original frame is sent instead.
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.w(TAG, "Filter pass failed with GL error $error; sending the frame as it came")
            failures++
            stale = true
            slot.give()
            return null
        }

        // The output is a plain RGB texture the right way up, so its own transform
        // is the identity - the input's matrix was spent in the shader above.
        return TextureBufferImpl(
            width,
            height,
            VideoFrame.TextureBuffer.Type.RGB,
            target.textureId,
            Matrix(),
            buffer.toI420Handler,
            buffer.yuvConverter,
            Runnable { slot.give() }
        )
    }

    private fun program(oes: Boolean): Program? {
        val held = if (oes) oesProgram else flatProgram
        if (held != null) return held
        val built = Program.build(oes) ?: return null
        if (oes) oesProgram = built else flatProgram = built
        return built
    }

    /** Android's 3x3 affine matrix, as the 4x4 the shader multiplies by. */
    private fun matrixFrom(source: Matrix) {
        val values = FloatArray(9)
        source.getValues(values)
        texMatrix[0] = values[0]
        texMatrix[1] = values[3]
        texMatrix[2] = 0f
        texMatrix[3] = values[6]
        texMatrix[4] = values[1]
        texMatrix[5] = values[4]
        texMatrix[6] = 0f
        texMatrix[7] = values[7]
        texMatrix[8] = 0f
        texMatrix[9] = 0f
        texMatrix[10] = 1f
        texMatrix[11] = 0f
        texMatrix[12] = values[2]
        texMatrix[13] = values[5]
        texMatrix[14] = 0f
        texMatrix[15] = values[8]
    }

    /** One place to draw into, and whether anybody is still reading it. */
    private class Slot {
        private val buffer = GlTextureFrameBuffer(GLES20.GL_RGBA)

        @Volatile
        var free = true
            private set

        fun take(width: Int, height: Int): GlTextureFrameBuffer {
            free = false
            buffer.setSize(width, height)
            return buffer
        }

        /** Called when the frame drawn here is released, from whichever thread. */
        fun give() {
            free = true
        }
    }

    private class Program(
        val id: Int,
        val position: Int,
        val coords: Int,
        val texture: Int,
        val matrix: Int,
        val texel: Int,
        val brightness: Int,
        val smooth: Int
    ) {
        companion object {
            fun build(oes: Boolean): Program? {
                val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX) ?: return null
                val fragment = compile(
                    GLES20.GL_FRAGMENT_SHADER,
                    if (oes) OES_HEADER + FRAGMENT else FLAT_HEADER + FRAGMENT
                ) ?: return null
                val id = GLES20.glCreateProgram()
                GLES20.glAttachShader(id, vertex)
                GLES20.glAttachShader(id, fragment)
                GLES20.glLinkProgram(id)
                val linked = IntArray(1)
                GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, linked, 0)
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
                if (linked[0] == 0) {
                    Log.w(TAG, "Filter program did not link: " + GLES20.glGetProgramInfoLog(id))
                    GLES20.glDeleteProgram(id)
                    return null
                }
                return Program(
                    id = id,
                    position = GLES20.glGetAttribLocation(id, "aPosition"),
                    coords = GLES20.glGetAttribLocation(id, "aCoords"),
                    texture = GLES20.glGetUniformLocation(id, "uTexture"),
                    matrix = GLES20.glGetUniformLocation(id, "uMatrix"),
                    texel = GLES20.glGetUniformLocation(id, "uTexel"),
                    brightness = GLES20.glGetUniformLocation(id, "uBrightness"),
                    smooth = GLES20.glGetUniformLocation(id, "uSmooth")
                )
            }

            private fun compile(type: Int, source: String): Int? {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val ok = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
                if (ok[0] == 0) {
                    Log.w(TAG, "Filter shader: " + GLES20.glGetShaderInfoLog(shader))
                    GLES20.glDeleteShader(shader)
                    return null
                }
                return shader
            }
        }
    }

    private companion object {
        const val TAG = "CameraFilter"

        /** Failed passes in one context before the filter stands aside in it. */
        const val MAX_FAILURES = 3

        /** How many leftover GL errors are cleared before a pass, at most. */
        const val MAX_STALE_ERRORS = 8

        /** Three is enough for a frame in the encoder and one being drawn. */
        const val SLOTS = 3
        const val STRIDE = 4 * 4

        val VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aCoords;
            uniform mat4 uMatrix;
            varying vec2 vCoords;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vCoords = (uMatrix * vec4(aCoords, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        val OES_HEADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
        """.trimIndent() + "\n"

        val FLAT_HEADER = """
            precision mediump float;
            uniform sampler2D uTexture;
        """.trimIndent() + "\n"

        /**
         * The filter itself.
         *
         * **Smoothing** is a ring of eight samples around each pixel, weighted
         * down where the colour differs - a cheap stand-in for a bilateral blur.
         * The weighting is what keeps it from turning a face into a smear: edges,
         * eyes and the line of the lips differ sharply from their neighbours and
         * so keep most of their own colour, while skin, which varies slowly,
         * averages with what is next to it. A true bilateral filter does this
         * properly and costs far more than a video call can afford at thirty
         * frames a second.
         *
         * **Brightness** lifts towards white rather than adding a flat amount:
         * `c + k(1 - c)` leaves white alone and moves the shadows most, where a
         * plain `c + k` would wash the highlights out to nothing. At the top of
         * the slider it closes just under half the distance to white, which is as
         * far as a face can be lifted before the noise in the shadows comes up
         * with it.
         */
        val FRAGMENT = """
            varying vec2 vCoords;
            uniform vec2 uTexel;
            uniform float uBrightness;
            uniform float uSmooth;

            void main() {
                vec3 centre = texture2D(uTexture, vCoords).rgb;
                vec3 colour = centre;

                if (uSmooth > 0.01) {
                    vec2 r = uTexel * 2.0;
                    vec3 sum = centre;
                    float weight = 1.0;
                    vec3 s;
                    float w;

                    s = texture2D(uTexture, vCoords + vec2(-r.x, -r.y)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;
                    s = texture2D(uTexture, vCoords + vec2(0.0, -r.y)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;
                    s = texture2D(uTexture, vCoords + vec2(r.x, -r.y)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;
                    s = texture2D(uTexture, vCoords + vec2(-r.x, 0.0)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;
                    s = texture2D(uTexture, vCoords + vec2(r.x, 0.0)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;
                    s = texture2D(uTexture, vCoords + vec2(-r.x, r.y)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;
                    s = texture2D(uTexture, vCoords + vec2(0.0, r.y)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;
                    s = texture2D(uTexture, vCoords + vec2(r.x, r.y)).rgb;
                    w = 1.0 - smoothstep(0.0, 0.25, distance(s, centre)); sum += s * w; weight += w;

                    colour = mix(centre, sum / weight, uSmooth);
                }

                colour = colour + uBrightness * 0.45 * (1.0 - colour);
                gl_FragColor = vec4(clamp(colour, 0.0, 1.0), 1.0);
            }
        """.trimIndent()

        fun floats(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }
    }
}
