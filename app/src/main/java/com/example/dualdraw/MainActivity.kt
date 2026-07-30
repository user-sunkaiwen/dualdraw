package com.example.dualdraw

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.sqrt

/* ========== 数据结构 ========== */
data class Stroke(
    val id: Int,
    val points: MutableList<PointF>,
    val color: Int,       // 颜色
    val isLeft: Boolean   // 是否左侧用户所画
)

/* ========== 核心画布视图 ========== */
class DualCanvas(ctx: android.content.Context) : View(ctx) {

    // 笔画存储
    val strokes = mutableListOf<Stroke>()
    val leftPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A90D9.toInt(); strokeWidth = 8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE85D75.toInt(); strokeWidth = 8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFFFFF; strokeWidth = 36f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val bgPaint = Paint().apply { color = 0xFF1A1A2E.toInt() }
    val midLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30FFFFFF; strokeWidth = 2f; style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f) }

    // 当前活跃的手指标识 -> Stroke
    val activeStrokes = mutableMapOf<Int, Stroke>()

    // 橡皮擦模式（右侧专属）
    var eraserMode = false
    var eraserX = 0f
    var eraserY = 0f
    val eraserRadius = 30f

    // 中线 x 坐标（动态计算）
    var midX = 0f

    private var strokeIdCounter = 0

    init {
        // 开启多点触控
        // 默认就支持多指，不需要额外设置
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        midX = w / 2f
    }

    /* ========== 触摸处理 ========== */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId  = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val isLeft = x < midX
                if (!isLeft && eraserMode) {
                    // 右侧橡皮擦模式：擦除左侧笔画
                    doEraser(x, y)
                } else {
                    // 正常画
                    val c = if (isLeft) leftPaint.color else rightPaint.color
                    val s = Stroke(strokeIdCounter++, mutableListOf(PointF(x, y)), c, isLeft)
                    activeStrokes[pointerId] = s
                    strokes.add(s)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px  = event.getX(i)
                    val py  = event.getY(i)

                    if (eraserMode && px >= midX) {
                        // 当前手指在右侧且橡皮擦模式
                        doEraser(px, py)
                    } else {
                        activeStrokes[pid]?.let { s ->
                            s.points.add(PointF(px, py))
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                activeStrokes.remove(pointerId)
                if (eraserMode) {
                    eraserX = 0f; eraserY = 0f
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                activeStrokes.clear()
                eraserX = 0f; eraserY = 0f
            }
        }

        invalidate()
        return true
    }

    private fun doEraser(x: Float, y: Float) {
        eraserX = x; eraserY = y
        // 遍历所有左侧笔画，检查是否被橡皮擦碰到
        val toRemove = strokes.filter { it.isLeft }.filter { stroke ->
            stroke.points.any { pt ->
                val dx = pt.x - x
                val dy = pt.y - y
                sqrt(dx * dx + dy * dy) < eraserRadius
            }
        }
        strokes.removeAll(toRemove)
    }

    /* ========== 绘制 ========== */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 中线
        canvas.drawLine(midX, 0f, midX, height.toFloat(), midLinePaint)

        // 左侧区域淡色底
        val sideBg = Paint().apply { color = 0x0A4A90D9; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, midX, height.toFloat(), sideBg)

        // 右侧区域淡色底
        sideBg.color = 0x0AE85D75
        canvas.drawRect(midX, 0f, width.toFloat(), height.toFloat(), sideBg)

        // 绘制所有笔画
        for (s in strokes) {
            if (s.points.size < 2) continue
            val paint = if (s.isLeft) leftPaint else rightPaint
            val path = Path()
            path.moveTo(s.points[0].x, s.points[0].y)
            for (i in 1 until s.points.size) {
                path.lineTo(s.points[i].x, s.points[i].y)
            }
            canvas.drawPath(path, paint)
        }

        // 橡皮擦指示器
        if (eraserMode && eraserX > 0f) {
            canvas.drawCircle(eraserX, eraserY, eraserRadius, eraserPaint)
        }

        // 左右标签
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40FFFFFF; textSize = 36f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🙋 A", midX / 2f, 50f, labelPaint)
        canvas.drawText("🙋 B", midX + midX / 2f, 50f, labelPaint)

        // 颜色指示点
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        dotPaint.color = leftPaint.color
        canvas.drawCircle(midX / 2f, 80f, 8f, dotPaint)
        dotPaint.color = rightPaint.color
        canvas.drawCircle(midX + midX / 2f, 80f, 8f, dotPaint)
    }

    /* ========== 外部控制 ========== */
    fun undo() {
        // 撤销最后一个笔画（无论左右）
        if (strokes.isNotEmpty()) strokes.removeLast()
        invalidate()
    }

    fun clear() {
        strokes.clear()
        activeStrokes.clear()
        invalidate()
    }
}

/* ========== Activity ========== */
class MainActivity : Activity() {

    private lateinit var canvas: DualCanvas
    private var eraserBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 用 LinearLayout 组合 画布 + 工具栏
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF12122A.toInt())
        }

        // 工具栏
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF1E1E3A.toInt())
        }

        fun makeBtn(label: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF2A2A50.toInt())
                textSize = 13f
                setPadding(24, 8, 24, 8)
                setOnClickListener { onClick() }
            }
        }

        eraserBtn = makeBtn("🧹 橡皮擦: 关") {
            canvas.eraserMode = !canvas.eraserMode
            eraserBtn?.text = if (canvas.eraserMode) "🧹 橡皮擦: 开" else "🧹 橡皮擦: 关"
            Toast.makeText(this,
                if (canvas.eraserMode) "右侧触摸可擦除左侧线条" else "橡皮擦已关闭",
                Toast.LENGTH_SHORT).show()
        }

        val undoBtn = makeBtn("↩ 撤销") { canvas.undo() }
        val clearBtn = makeBtn("🗑 清空") { canvas.clear() }

        toolbar.addView(undoBtn)
        toolbar.addView(clearBtn)
        toolbar.addView(eraserBtn)

        // 画布
        canvas = DualCanvas(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f // 占据所有剩余空间
            )
        }

        root.addView(toolbar)
        root.addView(canvas)
        setContentView(root)
    }
}