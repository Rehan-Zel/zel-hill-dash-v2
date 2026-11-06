package com.hilldash.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural hill terrain. Y axis points DOWN (screen convention).
 * Flat start, then rolling hills that get steeper the further you go.
 */
object Terrain {
    const val BASE_Y = 520f

    private fun ramp(x: Float): Float {
        val t = ((x - 400f) / 2200f).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        val growth = 1f + (x / 30000f).coerceIn(0f, 0.5f)
        return smooth * growth
    }

    /** World Y of the ground surface at world X. */
    fun heightAt(x: Float): Float {
        val h = 90f * sin(x / 500f) +
            45f * sin(x / 230f + 1.3f) +
            14f * sin(x / 97f + 0.4f)
        return BASE_Y - ramp(x) * h
    }

    /** dY/dX of the ground surface. */
    fun slopeAt(x: Float): Float = (heightAt(x + 1f) - heightAt(x - 1f)) / 2f
}

/**
 * Simple rigid-body car: one body with two spring-loaded wheel contacts,
 * two bumper contacts and roof sensors. Mass = 1, units are "pixels".
 */
class GameEngine {

    companion object {
        const val GRAVITY = 900f
        const val WHEEL_R = 22f
        const val WHEEL_X = 42f
        const val WHEEL_Y = 22f
        const val SPRING = 3000f
        const val DAMP = 70f
        const val INERTIA = 3600f
        const val DRIVE = 760f
        const val BRAKE = 1100f
        const val MAX_SPEED = 950f
        const val FIXED_DT = 1f / 60f
        const val SUBSTEPS = 4
    }

    // Body state
    var x = 0f
    var y = 0f
    var angle = 0f      // radians, positive = clockwise on screen
    var vx = 0f
    var vy = 0f
    var w = 0f          // angular velocity

    // Inputs
    @Volatile var gas = false
    @Volatile var brake = false

    // Status
    var crashed = false
    var grounded = false
    var wheelSpin = 0f
    var camY = 0f

    val speedKmh: Float get() = hypot(vx, vy) / 7f
    val distanceM: Int get() = (max(0f, x) / 40f).toInt()

    // Force accumulators
    private var fax = 0f
    private var fay = 0f
    private var faw = 0f
    private var acc = 0f

    init {
        reset()
    }

    fun reset() {
        x = 0f
        y = Terrain.BASE_Y - WHEEL_R - WHEEL_Y - 4f
        angle = 0f
        vx = 0f
        vy = 0f
        w = 0f
        crashed = false
        grounded = false
        wheelSpin = 0f
        acc = 0f
        camY = y - 400f
    }

    /** Advance the simulation by [dt] seconds using a fixed 60 Hz step. */
    fun update(dt: Float) {
        if (crashed) return
        acc += min(dt, 0.05f)
        while (acc >= FIXED_DT) {
            acc -= FIXED_DT
            val h = FIXED_DT / SUBSTEPS
            for (i in 0 until SUBSTEPS) {
                step(h)
                if (crashed) break
            }
            camY += ((y - 400f) - camY) * 0.08f
            if (crashed) break
        }
    }

    private fun step(h: Float) {
        fax = 0f
        fay = GRAVITY
        faw = 0f

        val c = cos(angle)
        val s = sin(angle)

        val rear = contact(c, s, -WHEEL_X, WHEEL_Y, WHEEL_R, true, 0.5f)
        val front = contact(c, s, WHEEL_X, WHEEL_Y, WHEEL_R, true, 0.5f)
        contact(c, s, -62f, 12f, 0f, false, 0f)
        contact(c, s, 62f, 12f, 0f, false, 0f)
        grounded = rear || front

        if (!grounded) {
            // Air control: gas rotates forward (nose down), brake rotates back (nose up)
            if (gas) faw += 7f
            if (brake) faw -= 7f
            faw -= w * 0.3f
        } else {
            if (gas) faw += 2f
            faw -= w * 1.0f
        }

        vx += fax * h
        vy += fay * h
        w += faw * h
        x += vx * h
        y += vy * h
        angle += w * h

        // Left invisible wall at the start line
        if (x < -60f) {
            x = -60f
            vx = max(vx, 0f)
        }

        wheelSpin += (vx / WHEEL_R) * h
        if (!grounded && gas) wheelSpin += 12f * h

        checkCrash(cos(angle), sin(angle))
    }

    /**
     * Resolve one contact point. Returns true if it touches the ground.
     * (lx, ly) is the point in the body frame, [radius] the wheel radius (0 for bumpers).
     */
    private fun contact(
        c: Float, s: Float,
        lx: Float, ly: Float,
        radius: Float,
        isWheel: Boolean,
        driveShare: Float
    ): Boolean {
        val rx = lx * c - ly * s
        val ry = lx * s + ly * c
        val px = x + rx
        val py = y + ry

        val gy = Terrain.heightAt(px)
        val pen = py + radius - gy
        if (pen <= 0f) return false

        val slope = Terrain.slopeAt(px)
        val inv = 1f / sqrt(1f + slope * slope)
        val tx = inv
        val ty = slope * inv
        val nx = slope * inv
        val ny = -inv

        // Velocity of the contact point
        val vpx = vx - w * ry
        val vpy = vy + w * rx
        val vn = vpx * nx + vpy * ny
        val vt = vpx * tx + vpy * ty

        // Spring-damper normal force
        val fn = max(0f, SPRING * min(pen, 30f) - DAMP * vn)
        var fx = fn * nx
        var fy = fn * ny

        var ft = 0f
        if (isWheel) {
            if (gas) {
                val taper = (1f - vt / MAX_SPEED).coerceIn(0f, 1.5f)
                ft += DRIVE * driveShare * taper
            }
            if (brake) {
                ft += -(vt * 10f).coerceIn(-BRAKE * driveShare, BRAKE * driveShare)
            }
            ft += -vt * 0.15f // rolling resistance
            val grip = 1.6f * fn
            ft = ft.coerceIn(-grip, grip)
        } else {
            val grip = 0.8f * fn
            ft = (-vt * 20f).coerceIn(-grip, grip)
        }
        fx += ft * tx
        fy += ft * ty

        fax += fx
        fay += fy
        faw += (rx * fy - ry * fx) / INERTIA
        return true
    }

    private fun checkCrash(c: Float, s: Float) {
        // Roof and helmet sensors in the body frame
        val sensors = floatArrayOf(-18f, -50f, 26f, -50f, 6f, -62f)
        var i = 0
        while (i < sensors.size) {
            val lx = sensors[i]
            val ly = sensors[i + 1]
            val px = x + lx * c - ly * s
            val py = y + lx * s + ly * c
            if (py - Terrain.heightAt(px) > 2f) {
                crashed = true
                return
            }
            i += 2
        }
        if (y > Terrain.BASE_Y + 1500f) crashed = true
        if (grounded && abs(angle) > 2.6f) crashed = true
        // keep angle bounded so repeated flips do not accumulate forever
        if (abs(angle) > 2f * Math.PI.toFloat()) angle -= sign(angle) * 2f * Math.PI.toFloat()
    }
}
