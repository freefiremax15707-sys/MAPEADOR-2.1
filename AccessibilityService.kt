
package com.example.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class AccessibilityService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences

    private var floatingButton: TextView? = null
    private var mainMenu: LinearLayout? = null
    private var editorMenu: LinearLayout? = null
    private var sensitivityMenu: LinearLayout? = null
    private var targetView: TextView? = null
    private var cursorIndicator: View? = null

    private var menuOpen = false
    private var captureNextKey = false
    private var captureSensitivityKey = false
    private var cursorVisible = true

    private var selectedKey = KeyEvent.KEYCODE_UNKNOWN
    private var sensitivityKey = KeyEvent.KEYCODE_UNKNOWN

    private val mappings = mutableMapOf<Int, Pair<Float, Float>>()

    private var editorMessage: TextView? = null
    private var sensitivityMessage: TextView? = null

    private val density: Float
        get() = resources.displayMetrics.density

    private fun dp(value: Int): Int =
        (value * density).toInt()

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        prefs = getSharedPreferences(
            "M_ACCESSIBILITY",
            Context.MODE_PRIVATE
        )

        loadMappings()
        loadSensitivityKey()

        cursorVisible = prefs.getBoolean("cursor_visible", true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        createFloatingButton()
        createCursorIndicator()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Eventos não utilizados.
    }

    override fun onInterrupt() {
        removeAllWindows()
    }

    override fun onDestroy() {
        removeAllWindows()
        super.onDestroy()
    }

    // ==========================================
    // JANELAS
    // ==========================================

    private fun params(): WindowManager.LayoutParams {

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun addView(
        view: View,
        lp: WindowManager.LayoutParams
    ) {
        try {
            if (view.parent == null) {
                wm.addView(view, lp)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeView(view: View?) {
        if (view == null) return

        try {
            if (view.parent != null) {
                wm.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // BOTÃO FLUTUANTE M
    // ==========================================

    private fun createFloatingButton() {

        if (floatingButton != null) return

        val button = TextView(this).apply {

            text = "M"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
                setStroke(dp(2), Color.WHITE)
            }

            elevation = dp(8).toFloat()
        }

        val lp = params().apply {
            width = dp(55)
            height = dp(55)
            x = dp(20)
            y = dp(200)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        button.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY

                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) {
                        moved = true
                    }

                    lp.x = (startX + dx.toInt()).coerceAtLeast(0)
                    lp.y = (startY + dy.toInt()).coerceAtLeast(0)

                    try {
                        wm.updateViewLayout(button, lp)
                    } catch (_: Exception) {}

                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMainMenu()
                    true
                }

                else -> true
            }
        }

        floatingButton = button
        addView(button, lp)
    }

    // ==========================================
    // MENU PRINCIPAL
    // ==========================================

    private fun createContainer(): LinearLayout {

        return LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            background = GradientDrawable().apply {
                setColor(Color.rgb(25, 25, 25))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), Color.RED)
            }

            elevation = dp(10).toFloat()
        }
    }

    private fun createButton(
        text: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {

            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)

            background = GradientDrawable().apply {
                setColor(Color.rgb(55, 55, 55))
                cornerRadius = dp(8).toFloat()
            }

            setOnClickListener {
                action()
            }
        }
    }

    private fun createLabel(text: String): TextView {

        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun toggleMainMenu() {

        if (menuOpen) {
            closeMenus()
        } else {
            openMainMenu()
        }
    }

    private fun openMainMenu() {

        closeMenus()

        val menu = createContainer()

        menu.addView(
            createButton("Editor de teclas") {
                openEditorMenu()
            }
        )

        menu.addView(
            createButton("Sensibilidade da visualização") {
                openSensitivityMenu()
            }
        )

        mainMenu = menu
        menuOpen = true

        addView(menu, params().apply {
            x = dp(90)
            y = dp(250)
        })
    }

    // ==========================================
    // EDITOR DE TECLAS
    // ==========================================

    private fun openEditorMenu() {

        closeMenus()

        val menu = createContainer()

        menu.addView(createLabel("EDITOR DE TECLAS"))

        editorMessage = createLabel(
            "Nenhuma tecla selecionada."
        )

        menu.addView(editorMessage)

        menu.addView(
            createButton("Capturar próxima tecla") {

                captureNextKey = true
                captureSensitivityKey = false

                editorMessage?.text =
                    "Pressione a tecla física desejada."
            }
        )

        menu.addView(
            createButton("Posicionar alvo na tela") {

                if (selectedKey == KeyEvent.KEYCODE_UNKNOWN) {
                    editorMessage?.text =
                        "Primeiro capture uma tecla."
                } else {
                    createTarget()
                }
            }
        )

        menu.addView(
            createButton("Salvar mapeamentos") {
                saveMappings()
                editorMessage?.text = "Mapeamentos salvos!"
            }
        )

        menu.addView(
            createButton("Mostrar mapeamentos") {

                val result = mappings.entries.joinToString("\n") {
                    "Tecla ${it.key}: X=${it.value.first.toInt()}, Y=${it.value.second.toInt()}"
                }

                editorMessage?.text =
                    result.ifEmpty { "Nenhum mapeamento." }
            }
        )

        menu.addView(
            createButton("Excluir mapeamento selecionado") {

                if (selectedKey != KeyEvent.KEYCODE_UNKNOWN) {
                    mappings.remove(selectedKey)
                    saveMappings()

                    editorMessage?.text =
                        "Mapeamento excluído."
                }
            }
        )

        menu.addView(
            createButton("Voltar") {
                openMainMenu()
            }
        )

        editorMenu = menu
        menuOpen = true

        addView(menu, params().apply {
            x = dp(90)
            y = dp(250)
        })
    }

    // ==========================================
    // ALVO DE POSICIONAMENTO
    // ==========================================

    private fun createTarget() {

        removeView(targetView)
        targetView = null

        val target = TextView(this).apply {

            text = "+"
            textSize = 35f
            gravity = Gravity.CENTER
            setTextColor(Color.RED)

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(100, 255, 255, 255))
                setStroke(dp(2), Color.RED)
            }
        }

        val lp = params().apply {
            width = dp(60)
            height = dp(60)
            x = dp(150)
            y = dp(350)
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        target.setOnTouchListener { view, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    initialX = lp.x
                    initialY = lp.y

                    touchX = event.rawX
                    touchY = event.rawY

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    lp.x = (
                        initialX + event.rawX - touchX
                    ).toInt().coerceAtLeast(0)

                    lp.y = (
                        initialY + event.rawY - touchY
                    ).toInt().coerceAtLeast(0)

                    try {
                        wm.updateViewLayout(target, lp)
                    } catch (_: Exception) {}

                    true
                }

                MotionEvent.ACTION_UP -> {

                    val x = lp.x + view.width / 2f
                    val y = lp.y + view.height / 2f

                    mappings[selectedKey] = Pair(x, y)

                    saveMappings()

                    editorMessage?.text =
                        "Tecla $selectedKey salva em X=${x.toInt()}, Y=${y.toInt()}"

                    removeView(targetView)
                    targetView = null

                    true
                }

                else -> true
            }
        }

        targetView = target
        addView(target, lp)
    }

    // ==========================================
    // SENSIBILIDADE DA VISUALIZAÇÃO
    // ==========================================

    private fun openSensitivityMenu() {

        closeMenus()

        val menu = createContainer()

        menu.addView(
            createLabel("SENSIBILIDADE DA VISUALIZAÇÃO")
        )

        sensitivityMessage = createLabel(
            "Tecla atual: $sensitivityKey"
        )

        menu.addView(sensitivityMessage)

        menu.addView(
            createButton("Configurar tecla de alternância") {

                captureSensitivityKey = true
                captureNextKey = false

                sensitivityMessage?.text =
                    "Pressione a tecla física desejada."
            }
        )

        menu.addView(
            createButton("Salvar configuração") {

                prefs.edit()
                    .putInt("sensitivity_key", sensitivityKey)
                    .apply()

                sensitivityMessage?.text =
                    "Configuração salva."
            }
        )

        menu.addView(
            createButton("Voltar") {
                openMainMenu()
            }
        )

        sensitivityMenu = menu
        menuOpen = true

        addView(menu, params().apply {
            x = dp(90)
            y = dp(250)
        })
    }

    private fun toggleCursor() {

        cursorVisible = !cursorVisible

        cursorIndicator?.visibility =
            if (cursorVisible) View.VISIBLE
            else View.GONE

        prefs.edit()
            .putBoolean("cursor_visible", cursorVisible)
            .apply()
    }

    private fun createCursorIndicator() {

        if (cursorIndicator != null) return

        val cursor = View(this).apply {

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.RED)
            }
        }

        val lp = params().apply {

            width = dp(20)
            height = dp(20)

            flags = flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            x = dp(180)
            y = dp(300)
        }

        cursorIndicator = cursor

        addView(cursor, lp)

        cursor.visibility =
            if (cursorVisible) View.VISIBLE
            else View.GONE
    }

    // ==========================================
    // CAPTURA DE TECLAS FÍSICAS
    // ==========================================

    override fun onKeyEvent(event: KeyEvent): Boolean {

        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }

        if (event.repeatCount > 0) {
            return true
        }

        val key = event.keyCode

        // Captura da tecla de sensibilidade.
        if (captureSensitivityKey) {

            sensitivityKey = key
            captureSensitivityKey = false

            sensitivityMessage?.text =
                "Tecla configurada: $key"

            return true
        }

        // Captura da próxima tecla do editor.
        if (captureNextKey) {

            selectedKey = key
            captureNextKey = false

            editorMessage?.text =
                "Tecla $key capturada. Posicione o alvo."

            return true
        }

        // Alternância da visualização.
        if (
            sensitivityKey != KeyEvent.KEYCODE_UNKNOWN &&
            key == sensitivityKey
        ) {
            toggleCursor()
            return true
        }

        // Execução dos mapeamentos.
        val point = mappings[key]

        if (point != null) {

            dispatchTouch(
                point.first,
                point.second
            )

            return true
        }

        return super.onKeyEvent(event)
    }

    // ==========================================
    // DISPATCH GESTURE
    // ==========================================

    private fun dispatchTouch(x: Float, y: Float) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    100
                )
            )
            .build()

        dispatchGesture(
            gesture,
            null,
            null
        )
    }

    // ==========================================
    // SALVAR MAPEAMENTOS
    // ==========================================

    private fun saveMappings() {

        val array = JSONArray()

        mappings.forEach { (key, point) ->

            val item = JSONObject()

            item.put("key", key)
            item.put("x", point.first.toDouble())
            item.put("y", point.second.toDouble())

            array.put(item)
        }

        prefs.edit()
            .putString("mappings", array.toString())
            .apply()
    }

    // ==========================================
    // CARREGAR MAPEAMENTOS
    // ==========================================

    private fun loadMappings() {

        mappings.clear()

        val json = prefs.getString("mappings", "[]")
            ?: return

        try {

            val array = JSONArray(json)

            for (i in 0 until array.length()) {

                val item = array.getJSONObject(i)

                val key = item.getInt("key")
                val x = item.getDouble("x").toFloat()
                val y = item.getDouble("y").toFloat()

                mappings[key] = Pair(x, y)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSensitivityKey() {

        sensitivityKey = prefs.getInt(
            "sensitivity_key",
            KeyEvent.KEYCODE_UNKNOWN
        )
    }

    // ==========================================
    // FECHAR MENUS
    // ==========================================

    private fun closeMenus() {

        removeView(mainMenu)
        removeView(editorMenu)
        removeView(sensitivityMenu)

        mainMenu = null
        editorMenu = null
        sensitivityMenu = null

        editorMessage = null
        sensitivityMessage = null

        menuOpen = false
    }

    // ==========================================
    // LIMPEZA GERAL
    // ==========================================

    private fun removeAllWindows() {

        closeMenus()

        removeView(targetView)
        removeView(cursorIndicator)
        removeView(floatingButton)

        targetView = null
        cursorIndicator = null
        floatingButton = null

        captureNextKey = false
        captureSensitivityKey = false
    }
}
