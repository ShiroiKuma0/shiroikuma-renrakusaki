package org.fossify.contacts.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import java.util.Locale
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.contacts.databinding.DialogColorPickerAlphaBinding

private const val ALPHA_MAX = 255
private const val HEX_LENGTH = 6
private const val HUE_MAX = 360f
private const val HUE_EPSILON = 0.001f
private const val RGB_MASK = 0xFFFFFF
private const val ALPHA_SHIFT = 24
private const val HSV_SIZE = 3

// A commons-style HSV colour picker with an added alpha/transparency slider; returns an ARGB int.
@SuppressLint("ClickableViewAccessibility")
@Suppress("TooManyFunctions", "EmptyFunctionBlock", "SwallowedException")
class AlphaColorPickerDialog(
    private val activity: Activity,
    private val color: Int,
    private val addDefaultColorButton: Boolean = false,
    private val callback: (wasPositive: Boolean, color: Int) -> Unit,
) {
    private val binding = DialogColorPickerAlphaBinding.inflate(activity.layoutInflater)
    private val currentColorHsv = FloatArray(HSV_SIZE)
    private var currentAlpha = Color.alpha(color)
    private var dialog: AlertDialog? = null
    private var isCursorPlaced = false
    private var ignoreHexChanges = false

    init {
        Color.colorToHSV(color, currentColorHsv)

        binding.apply {
            colorPickerSquare.setHue(currentColorHsv[0])
            colorPickerOldHex.text = hexOf(color)
            colorPickerOldColor.setBackgroundColor(color)

            colorPickerAlphaSeekbar.max = ALPHA_MAX
            colorPickerAlphaSeekbar.progress = currentAlpha
            colorPickerAlphaValue.text = currentAlpha.toString()
            colorPickerAlphaSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    currentAlpha = progress
                    colorPickerAlphaValue.text = progress.toString()
                    updateNewColor()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

            colorPickerHue.setOnTouchListener { _, event -> onHueTouch(event) }
            colorPickerSquare.setOnTouchListener { _, event -> onSquareTouch(event) }

            setHexField(hexOf(color))
            colorPickerNewHex.onTextChangeListener { text ->
                if (!ignoreHexChanges && text.length == HEX_LENGTH) {
                    onHexEntered(text)
                }
            }

            colorPickerHolder.onGlobalLayout {
                if (!isCursorPlaced) {
                    isCursorPlaced = true
                    moveHueCursor()
                    moveColorCursor()
                }
            }
            updateNewColor()
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> callback(true, currentColor()) }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        if (addDefaultColorButton) {
            builder.setNeutralButton(org.fossify.commons.R.string.default_color) { _, _ -> callback(false, color) }
        }

        builder.apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun onHueTouch(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) {
            return false
        }
        val height = binding.colorPickerHue.measuredHeight.toFloat()
        if (height <= 0f) {
            return true
        }
        val y = event.y.coerceIn(0f, height - HUE_EPSILON)
        var hue = HUE_MAX - HUE_MAX / height * y
        if (hue >= HUE_MAX) {
            hue = HUE_MAX - HUE_EPSILON
        }
        currentColorHsv[0] = hue
        binding.colorPickerSquare.setHue(hue)
        moveHueCursor()
        setHexField(currentHex())
        updateNewColor()
        return true
    }

    private fun onSquareTouch(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) {
            return false
        }
        val width = binding.colorPickerSquare.measuredWidth.toFloat()
        val height = binding.colorPickerSquare.measuredHeight.toFloat()
        if (width <= 0f || height <= 0f) {
            return true
        }
        val x = event.x.coerceIn(0f, width)
        val y = event.y.coerceIn(0f, height)
        currentColorHsv[1] = 1f / width * x
        currentColorHsv[2] = 1f - 1f / height * y
        moveColorCursor()
        setHexField(currentHex())
        updateNewColor()
        return true
    }

    private fun onHexEntered(text: String) {
        try {
            Color.colorToHSV(Color.parseColor("#$text"), currentColorHsv)
            binding.colorPickerSquare.setHue(currentColorHsv[0])
            moveHueCursor()
            moveColorCursor()
            updateNewColor()
        } catch (e: IllegalArgumentException) {
            // ignore an incomplete/invalid hex while the user is still typing
        }
    }

    private fun moveHueCursor() {
        val hue = binding.colorPickerHue
        val hueHeight = hue.measuredHeight.toFloat()
        var y = hueHeight - currentColorHsv[0] * hueHeight / HUE_MAX
        if (y >= hueHeight) {
            y = hueHeight - HUE_EPSILON
        }
        val cursor = binding.colorPickerHueCursor
        cursor.x = (hue.left - cursor.width).toFloat()
        cursor.y = hue.top + y - cursor.height / 2f
    }

    private fun moveColorCursor() {
        val square = binding.colorPickerSquare
        val x = currentColorHsv[1] * square.measuredWidth
        val y = (1f - currentColorHsv[2]) * square.measuredHeight
        val cursor = binding.colorPickerCursor
        cursor.x = square.left + x - cursor.width / 2f
        cursor.y = square.top + y - cursor.height / 2f
    }

    private fun updateNewColor() {
        binding.colorPickerNewColor.setBackgroundColor(currentColor())
    }

    private fun currentColor() = (currentAlpha shl ALPHA_SHIFT) or (Color.HSVToColor(currentColorHsv) and RGB_MASK)

    private fun currentHex() = hexOf(Color.HSVToColor(currentColorHsv))

    private fun hexOf(value: Int) = String.format(Locale.US, "%06X", value and RGB_MASK)

    private fun setHexField(hex: String) {
        ignoreHexChanges = true
        binding.colorPickerNewHex.setText(hex)
        ignoreHexChanges = false
    }
}
