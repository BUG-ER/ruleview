package com.zjun.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 值变化监听器接口
 * 当控件的值发生变化时，会回调此接口方法
 */
interface IOnValueChangedListener {
    /**
     * 值变化的回调方法
     *
     * @param value 当前值
     */
    fun onValueChanged(value: Float)
}

/**
 * 滑动停止监听器接口
 * 当滑动停止时，会回调此接口方法
 */
interface IOnScrollStopListener {
    /**
     * 滑动停止的回调方法
     *
     * @param value 当前值
     * @param label 当前刻度标签文本
     */
    fun onScrollStop(value: Float, label: String)
}

/**
 * GradationView / RuleView
 * 刻度卷尺控件
 *
 * 实现原理：
 * 1. 数值处理：将float类型数据乘以10转为int类型，避免浮点精度问题，简化计算
 * 2. 绘制刻度：
 * - 根据中间指针位置的数值，计算最小值位置与中间指针的距离
 * - 仅绘制控件宽度范围内的刻度，并在两侧各扩展一定数量刻度，保证滑动过程中的视觉连续性
 * 3. 区间规则：
 * - 使用GradationGapRule定义不同值区间的刻度间距
 * - 自动根据控件宽度计算合适的刻度间距，确保刻度均匀分布
 * 4. 滑动处理：
 * - 移动时更新最小值与中心指针的距离，逆向计算当前刻度值
 * - 滑动停止后，自动对齐到最近的刻度（使用Scroller实现平滑过渡）
 * 5. 惯性滑动：使用VelocityTracker跟踪手指速度，实现松手后的惯性滑动
 * 6. 特殊功能：
 * - 支持触觉反馈（震动）
 * - 支持点击定位
 *
 * Author: Ralap
 * Description: 可自定义的刻度尺控件，支持多种刻度显示模式和交互方式
 * Date 2018/7/29
 */
class RuleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    View(context, attrs, defStyleAttr) {
    /**
     * 滑动阈值，手指移动超过此距离才视为有效滑动
     * 在构造函数中从ViewConfiguration获取
     */
    private val TOUCH_SLOP: Int

    /**
     * 惯性滑动的最小速度阈值
     * 在构造函数中从ViewConfiguration获取
     */
    private val MIN_FLING_VELOCITY: Int

    /**
     * 惯性滑动的最大速度阈值
     * 在构造函数中从ViewConfiguration获取
     */
    private val MAX_FLING_VELOCITY: Int

    //=============================== 可配置参数（通过xml属性或方法设置）===============================
    /** 背景色  */
    private var bgColor = 0

    /** 刻度颜色  */
    private var gradationColor = 0

    /** 短刻度线宽度  */
    private var shortLineWidth = 0f

    /**
     * 长刻度线宽度
     * 默认为短刻度线宽度的2倍
     */
    private var longLineWidth = 0f

    /** 短刻度长度  */
    private var shortGradationLen = 0f

    /**
     * 长刻度长度
     * 默认为短刻度长度的2倍
     */
    private var longGradationLen = 0f

    /** 刻度字体颜色  */
    private var textColor = 0

    /** 刻度字体大小  */
    private var textSize = 0f

    /** 中间指针线颜色  */
    private var indicatorLineColor = 0

    /** 中间指针线宽度  */
    private var indicatorLineWidth = 0f

    /** 中间指针线长度  */
    private var indicatorLineLen = 0f

    /**
     * 获取最小值
     *
     * @return 最小值
     */
    /** 最小值  */
    var minValue: Float = 0f
        private set

    /**
     * 获取最大值
     *
     * @return 最大值
     */
    /** 最大值  */
    var maxValue: Float = 0f
        private set

    /** 当前值  */
    private var currentValue = 0f

    /** 刻度最小单位  */
    private var gradationUnit = 0f

    /** 相邻两条长刻度线之间的刻度数量  */
    private var numberPerCount = 0

    /** 刻度与文字的间距  */
    private var gradationNumberGap = 0f

    /** 刻度条与刻度文字的间距  */
    private var textGradationGap = 0f

    //=============================== 内部计算使用的变量 ===============================
    /**
     * 最小数值，放大10倍：minValue * 10
     * 用于内部计算，避免浮点精度问题
     */
    private var mMinNumber = 0

    /**
     * 最大数值，放大10倍：maxValue * 10
     * 用于内部计算，避免浮点精度问题
     */
    private var mMaxNumber = 0

    /**
     * 当前数值，放大10倍：currentValue * 10
     * 用于内部计算，避免浮点精度问题
     */
    private var mCurrentNumber = 0

    /**
     * 最大数值与最小数值间的距离：(mMaxNumber - mMinNumber) / mNumberUnit * gradationGap
     * 表示整个刻度范围在屏幕上对应的像素距离
     */
    private var mNumberRangeDistance = 0f

    /**
     * 刻度数值最小单位：gradationUnit * 10
     * 用于内部计算，避免浮点精度问题
     */
    private var mNumberUnit = 0

    /**
     * 当前数值与最小值的距离：(mCurrentNumber - mMinNumber) / mNumberUnit * gradationGap
     * 当前指针位置与最小值位置的像素距离
     */
    private var mCurrentDistance = 0f

    //=============================== 工具类实例 ===============================
    /** 普通画笔，用于绘制刻度和指针  */
    private var mPaint: Paint? = null

    /** 文字画笔，用于绘制刻度值  */
    private var mTextPaint: TextPaint? = null

    /** 滑动器，用于实现平滑滚动和惯性滑动效果  */
    private var mScroller: Scroller? = null

    /** 速度跟踪器，用于测量手指滑动速度  */
    private var mVelocityTracker: VelocityTracker? = null

    /** 控件尺寸：宽度、一半宽度、高度  */
    private var mWidth = 0
    private var mHalfWidth = 0
    private var mHeight = 0

    /** 触摸事件起始X坐标  */
    private var mDownX = 0

    /** 上次触摸事件坐标  */
    private var mLastX = 0
    private var mLastY = 0

    /** 是否已经开始滑动（区分点击和滑动）  */
    private var isMoved = false

    /** 值变化监听器  */
    private var mValueChangedListener: IOnValueChangedListener? = null

    /** 滑动停止监听器 */
    private var mScrollStopListener: IOnScrollStopListener? = null

    /** 长刻度颜色  */
    private var longLineColor = 0

    /** 短刻度颜色  */
    private var shortLineColor = 0

    /** 是否已释放资源标志，防止内存泄漏  */
    private var mIsReleased = false

    /** 使用弱引用持有Context，避免内存泄漏  */
    private var mContextRef: WeakReference<Context>?

    @Deprecated("请使用 {@link IOnValueChangedListener} 代替")
    interface OnValueChangedListener : IOnValueChangedListener

    /**
     * 构造函数，三参数
     * @param context 上下文
     * @param attrs 属性集
     * @param defStyleAttr 默认样式属性
     */
    /**
     * 构造函数，双参数
     * @param context 上下文
     * @param attrs 属性集
     */
    /**
     * 构造函数，单参数
     * @param context 上下文
     */
    init {
        // 使用弱引用持有context，避免内存泄漏
        mContextRef = WeakReference(context)

        // 初始化自定义属性
        initAttrs(context, attrs)

        // 初始化系统相关常量，必须在构造中赋初值
        val viewConfiguration = ViewConfiguration.get(context)
        TOUCH_SLOP = viewConfiguration.scaledTouchSlop
        MIN_FLING_VELOCITY = viewConfiguration.scaledMinimumFlingVelocity
        MAX_FLING_VELOCITY = viewConfiguration.scaledMaximumFlingVelocity

        // 转换值和初始化画笔等资源
        convertValue2Number()
        init(context)
    }

    /**
     * 初始化自定义属性
     * 从XML布局文件中读取用户配置的属性值，并设置默认值
     *
     * @param context 上下文
     * @param attrs 属性集
     */
    private fun initAttrs(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RuleView)
        // 读取各项属性值，如果XML中未指定则使用默认值
        bgColor = typedArray.getColor(
            R.styleable.RuleView_zjun_bgColor, Color.parseColor(
                DEFAULT_BG_COLOR
            )
        )
        gradationColor = typedArray.getColor(R.styleable.RuleView_zjun_gradationColor, Color.LTGRAY)
        shortLineWidth = typedArray.getDimension(
            R.styleable.RuleView_gv_shortLineWidth, dp2px(
                DEFAULT_SHORT_LINE_WIDTH_DP
            ).toFloat()
        )
        shortGradationLen = typedArray.getDimension(
            R.styleable.RuleView_gv_shortGradationLen, dp2px(
                DEFAULT_SHORT_GRADATION_LEN_DP
            ).toFloat()
        )
        longGradationLen =
            typedArray.getDimension(R.styleable.RuleView_gv_longGradationLen, shortGradationLen * 2)
        longLineWidth = typedArray.getDimension(R.styleable.RuleView_gv_longLineWidth, shortLineWidth * 2)
        longLineColor = typedArray.getColor(R.styleable.RuleView_gv_longLineColor, gradationColor)
        shortLineColor = typedArray.getColor(R.styleable.RuleView_gv_shortLineColor, gradationColor)
        textColor = typedArray.getColor(R.styleable.RuleView_zjun_textColor, Color.BLACK)
        textSize = typedArray.getDimension(
            R.styleable.RuleView_zjun_textSize,
            sp2px(DEFAULT_TEXT_SIZE_SP).toFloat()
        )
        indicatorLineColor = typedArray.getColor(
            R.styleable.RuleView_zjun_indicatorLineColor, Color.parseColor(
                DEFAULT_INDICATOR_COLOR
            )
        )
        indicatorLineWidth = typedArray.getDimension(
            R.styleable.RuleView_zjun_indicatorLineWidth, dp2px(
                DEFAULT_INDICATOR_LINE_WIDTH_DP
            ).toFloat()
        )
        indicatorLineLen = typedArray.getDimension(
            R.styleable.RuleView_gv_indicatorLineLen, dp2px(
                DEFAULT_INDICATOR_LINE_LEN_DP
            ).toFloat()
        )
        minValue = typedArray.getFloat(R.styleable.RuleView_gv_minValue, DEFAULT_MIN_VALUE)
        maxValue = typedArray.getFloat(R.styleable.RuleView_gv_maxValue, DEFAULT_MAX_VALUE)
        currentValue = typedArray.getFloat(R.styleable.RuleView_gv_currentValue, DEFAULT_CURRENT_VALUE)
        gradationUnit = typedArray.getFloat(R.styleable.RuleView_gv_gradationUnit, DEFAULT_GRADATION_UNIT)
        numberPerCount = typedArray.getInt(R.styleable.RuleView_gv_numberPerCount, DEFAULT_NUMBER_PER_COUNT)
        gradationNumberGap = typedArray.getDimension(
            R.styleable.RuleView_gv_gradationNumberGap, dp2px(
                DEFAULT_GRADATION_NUMBER_GAP_DP
            ).toFloat()
        )
        textGradationGap = typedArray.getDimension(
            R.styleable.RuleView_gv_textGradationGap, dp2px(
                DEFAULT_TEXT_GRADATION_GAP_DP
            ).toFloat()
        )
        // 回收TypedArray，释放资源
        typedArray.recycle()
    }

    /**
     * 初始化画笔和滚动器
     * 设置画笔的抗锯齿、颜色、宽度等属性
     *
     * @param context 上下文
     */
    private fun init(context: Context) {
        // 创建普通画笔并设置抗锯齿
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint!!.strokeWidth = shortLineWidth
        // 创建文字画笔并设置抗锯齿、文字大小和颜色
        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        mTextPaint!!.textSize = textSize
        mTextPaint!!.color = textColor
        // 创建滚动器用于实现平滑滚动效果
        mScroller = Scroller(context)
    }

    /**
     * 把真实数值转换成绘制数值
     * 为了防止float的精度丢失，把minValue、maxValue、currentValue、gradationUnit都放大10倍
     * 转换为整数进行计算，提高精度和性能
     */
    private fun convertValue2Number() {
        // 将浮点值放大10倍转为整数
        mMinNumber = (minValue * 10).toInt()
        mMaxNumber = (maxValue * 10).toInt()
        mCurrentNumber = (currentValue * 10).toInt()
        mNumberUnit = (gradationUnit * 10).toInt()

        recalculateDistances()
    }


    /**
     * 测量控件尺寸
     * 处理wrap_content和padding情况
     * 计算可用宽度和高度
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 计算实际宽高
        mWidth = calculateSize(true, widthMeasureSpec)
        mHeight = calculateSize(false, heightMeasureSpec)

        // 考虑padding，计算实际可用宽度和高度
        mWidth = mWidth - paddingLeft - paddingRight
        mHeight = mHeight - paddingTop - paddingBottom

        // 计算半宽，用于中心点定位
        mHalfWidth = mWidth shr 1

        // 重新计算刻度在屏幕上对应的像素距离
//        recalculateDistances()

        // 设置测量尺寸时需要加上padding
        setMeasuredDimension(
            mWidth + paddingLeft + paddingRight,
            mHeight + paddingTop + paddingBottom
        )
    }

    /**
     * 计算宽度或高度的真实大小
     * 处理不同测量模式下的尺寸计算
     *
     * 宽或高为wrap_content时，父控件的测量模式无论是EXACTLY还是AT_MOST，
     * 默认给的测量模式都是AT_MOST，测量大小为父控件的size
     * 所以，我们宽度不做特殊处理，只处理高度，默认设为90dp
     *
     * @param isWidth 是不是宽度
     * @param spec 测量规则
     * @return 真实的大小（像素）
     */
    private fun calculateSize(isWidth: Boolean, spec: Int): Int {
        // 获取测量模式和测量大小
        val mode = MeasureSpec.getMode(spec)
        val size = MeasureSpec.getSize(spec)

        var realSize = size
        when (mode) {
            MeasureSpec.EXACTLY -> {}
            MeasureSpec.AT_MOST ->                 // 仅处理高度的wrap_content情况，宽度保持原样
                if (!isWidth) {
                    val defaultContentSize = dp2px(90f)
                    realSize = min(realSize.toDouble(), defaultContentSize.toDouble()).toInt()
                }

            MeasureSpec.UNSPECIFIED -> {}
            else -> {}
        }


        // 记录日志
        logD("isWidth=%b, mode=%d, size=%d, realSize=%d", isWidth, mode, size, realSize)
        return realSize
    }


    /**
     * 处理触摸事件
     * 实现滑动、点击、惯性滑动等交互功能
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果已释放资源，不处理触摸事件
        if (mIsReleased) {
            return false
        }
        // 获取事件类型和坐标
        val action = event.action
        val x = event.x.toInt()
        val y = event.y.toInt()
        // 记录日志
        logD("onTouchEvent: action=%d", action)
        // 初始化或获取速度跟踪器
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        // 添加移动事件，用于计算速度
        mVelocityTracker!!.addMovement(event)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // 手指按下时，停止当前的滚动动画
                mScroller!!.forceFinished(true)
                // 记录按下位置
                mDownX = x
                // 重置移动标志
                isMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                // 计算X方向移动距离
                val dx = x - mLastX
                // 判断是否已经开始滑动（区分点击和滑动）
                if (!isMoved) {
                    // 计算Y方向移动距离
                    val dy = y - mLastY
                    // 滑动的触发条件：水平滑动大于垂直滑动；滑动距离大于阈值
                    if (abs(dx.toDouble()) < abs(dy.toDouble()) || abs((x - mDownX).toDouble()) < TOUCH_SLOP) {
                        return true
                    }
                    // 设置已开始滑动标志
                    isMoved = true
                }
                // 更新当前距离（注意dx取反，手指右滑，刻度左移）
                mCurrentDistance += -dx.toFloat()
                // 计算新的当前值
                calculateValue()
            }
            MotionEvent.ACTION_UP -> {
                // 处理滑动结束（手指抬起）
                // 计算滑动速度，用于惯性滑动
                mVelocityTracker!!.computeCurrentVelocity(1000, MAX_FLING_VELOCITY.toFloat())
                val xVelocity = mVelocityTracker!!.xVelocity.toInt()
                // 如果速度超过最小阈值，启动惯性滑动
                if (abs(xVelocity.toDouble()) >= MIN_FLING_VELOCITY) {
                    // 注意xVelocity取反，与滑动方向一致
                    mScroller!!.fling(
                        mCurrentDistance.toInt(), 0, -xVelocity, 0,
                        0, mNumberRangeDistance.toInt(), 0, 0
                    )
                    invalidate()
                } else {
                    // 速度较小，滑动结束，对齐到最近的刻度
                    scrollToGradation()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                // 处理取消事件，与UP事件相同
                if (!isMoved) {
                    // 这是一个点击被取消的事件，保持当前位置不变
                    return true
                } else {
                    // 处理滑动被取消（类似手指抬起）
                    mVelocityTracker!!.computeCurrentVelocity(1000, MAX_FLING_VELOCITY.toFloat())
                    val xVelocity = mVelocityTracker!!.xVelocity.toInt()
                    if (abs(xVelocity.toDouble()) >= MIN_FLING_VELOCITY) {
                        mScroller!!.fling(
                            mCurrentDistance.toInt(), 0, -xVelocity, 0,
                            0, mNumberRangeDistance.toInt(), 0, 0
                        )
                        invalidate()
                    } else {
                        // 速度较小，滑动结束，对齐到最近的刻度
                        scrollToGradation()
                    }
                }
            }
            else -> {}
        }
        // 更新上次触摸位置
        mLastX = x
        mLastY = y
        return true
    }

    /**
     * 将值对齐到最近的刻度
     * 
     * @param number 需要对齐的值（放大10倍的整数）
     * @return 对齐后的值（放大10倍的整数）
     */
    private fun alignToNearestGradation(number: Int): Int {
        // 计算偏移量，四舍五入到最近的刻度
        val remainder = number % mNumberUnit
        return if (remainder >= mNumberUnit / 2) {
            number + (mNumberUnit - remainder)
        } else {
            number - remainder
        }
    }

    /**
     * 滑动到最近的刻度线上
     * 当滑动结束或惯性滑动结束时调用
     * 确保指针总是对齐到刻度线上
     */
    private fun scrollToGradation() {
        // 保存旧值，用于比较是否变化
        val oldNumber = mCurrentNumber
        
        // 直接计算最近的刻度值
        val targetNumber = calculateNumberFromDistance(mCurrentDistance, true)
        
        // 计算对应的像素距离
        mCurrentDistance = calculateDistanceFromNumber(targetNumber)
        // 更新当前值
        mCurrentNumber = targetNumber
        // 转回浮点值
        currentValue = mCurrentNumber / 10f

        // 记录日志
        logD(
            "scrollToGradation: mCurrentDistance=%f, mCurrentNumber=%d, currentValue=%f",
            mCurrentDistance, mCurrentNumber, currentValue
        )

        // 只有当值改变时才触发回调
        if (mValueChangedListener != null && oldNumber != mCurrentNumber) {
            mValueChangedListener!!.onValueChanged(currentValue)
        }

        // 触发滑动停止回调
        mScrollStopListener?.onScrollStop(currentValue, formatValueToLabel(currentValue))

        // 重绘视图
        invalidate()
    }

    /**
     * 根据distance距离，计算当前刻度值
     * 并触发值变化回调
     */
    private fun calculateValue() {
        // 限定滑动范围：在最小值与最大值之间
        mCurrentDistance = min(
            max(mCurrentDistance.toDouble(), 0.0),
            mNumberRangeDistance.toDouble()
        ).toFloat()
        // 保存旧值，用于比较是否变化
        val oldNumber = mCurrentNumber
        // 根据当前距离计算刻度值（注意使用特定区间规则）
        mCurrentNumber = calculateNumberFromDistance(mCurrentDistance, false)
        // 转回浮点值
        currentValue = mCurrentNumber / 10f
        // 记录日志
        logD(
            "calculateValue: mCurrentDistance=%f, mCurrentNumber=%d, currentValue=%f",
            mCurrentDistance, mCurrentNumber, currentValue
        )
        // 如果有监听器，通知值变化
        if (mValueChangedListener != null && oldNumber != mCurrentNumber) {
            mValueChangedListener!!.onValueChanged(currentValue)
        }
        // 重绘视图
        invalidate()
    }

    /**
     * 计算滚动
     * 当View重绘时由系统调用，用于实现平滑滚动效果
     * 处理惯性滑动和平滑过渡到刻度的逻辑
     */
    override fun computeScroll() {
        // 检查滚动器是否还在滚动中
        if (mScroller!!.computeScrollOffset()) {
            // 如果当前位置与最终位置不同，继续滚动
            if (mScroller!!.currX != mScroller!!.finalX) {
                // 更新当前距离
                mCurrentDistance = mScroller!!.currX.toFloat()

                // 直接根据当前距离计算并更新当前值
                calculateValue()
            } else {
                // 滚动结束，对齐到最近的刻度
                scrollToGradation()
            }
        }
    }

    /**
     * 绘制视图
     * 按顺序绘制：背景、刻度和数字、指针
     *
     * @param canvas 画布
     */
    override fun onDraw(canvas: Canvas) {
        // 1 绘制背景色
        canvas.drawColor(bgColor)
        // 2 绘制刻度、数字
        drawGradation(canvas)
        // 3 绘制指针
        drawIndicator(canvas)
    }

    /**
     * 绘制刻度和刻度值
     * 计算可见范围内的刻度，并根据不同规则绘制长/短刻度和刻度值
     *
     * @param canvas 画布
     */
    private fun drawGradation(canvas: Canvas) {
        // 保存画布状态
        canvas.save()
        // 平移画布，处理padding
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        // 计算文字基线位置：从顶部开始，先绘制文字，再绘制刻度
        val textBaseline = textSize + gradationNumberGap

        // 使用规则绘制刻度
        if (mGradationGapRules.isNotEmpty()) {
            drawGradationWithRules(canvas, textBaseline)
            canvas.restore()
            return
        }
    }

    /**
     * 使用不同区间规则绘制刻度
     *
     * @param canvas 画布
     * @param textBaseline 文本基线位置
     */
    private fun drawGradationWithRules(canvas: Canvas, textBaseline: Float) {
        // 计算当前中心位置对应的值
        val centerValue = calculateNumberFromDistance(mCurrentDistance, false)

        // 计算左右可见区域对应的值范围
        val leftValue = calculateNumberFromDistance(max(0f, mCurrentDistance - mHalfWidth), false)
        val rightValue = calculateNumberFromDistance(min(mNumberRangeDistance, mCurrentDistance + mHalfWidth), false)

        // 扩展绘制范围，确保滑动时两侧有足够的刻度
//        val expendUnit = mNumberUnit shl EXTEND_UNIT_SHIFT
        val expendUnit = 0

        var startNum = max(mMinNumber, leftValue - expendUnit)
        var endNum = min(mMaxNumber, rightValue + expendUnit)
        
        // 依次绘制每个可见的刻度
        var currentNum = startNum
        while (currentNum <= endNum) {
            // 计算当前刻度对应的x坐标
            val distance = mHalfWidth + (calculateDistanceFromNumber(currentNum) - mCurrentDistance)
            
            // 判断是否是长刻度（整数倍刻度）
            val perUnitCount = mNumberUnit * numberPerCount
            if (currentNum % perUnitCount == 0) {
                // 长刻度：使用长刻度颜色和宽度
                mPaint!!.color = longLineColor
                mPaint!!.strokeWidth = longLineWidth
                
                // 从文字下方开始绘制刻度
                canvas.drawLine(
                    distance, textBaseline + textGradationGap,
                    distance, textBaseline + textGradationGap + longGradationLen, mPaint
                )

                // 绘制刻度值文本
                val fNum = currentNum / 10f
                var text = fNum.toString()
                // 去掉小数点后的.0
                if (text.endsWith(".0")) {
                    text = text.substring(0, text.length - 2)
                }
                // 添加单位标识
                text += "x"

                // 计算文本宽度
                val textWidth = mTextPaint!!.measureText(text)

                // 计算当前刻度与中心线的距离，用于文本透明度渐变
                val distanceToCenter = abs((distance - mHalfWidth).toDouble()).toFloat()
                // 设置文本透明度，当距离小于一定值时逐渐隐藏，避免文本重叠
                var alpha = MAX_ALPHA
                val fadeDistance = textWidth * TEXT_FADE_DISTANCE_FACTOR
                if (distanceToCenter < fadeDistance) {
                    // 使用平方函数使渐变更加平滑
                    val ratio = distanceToCenter / fadeDistance
                    alpha = (MAX_ALPHA * (ratio * ratio)).toInt()
                }
                mTextPaint!!.alpha = alpha

                // 在刻度上方居中绘制文字
                canvas.drawText(text, distance - textWidth * .5f, textBaseline, mTextPaint)
            } else {
                // 短刻度：不再根据自定义规则处理，始终显示所有刻度
                mPaint!!.color = shortLineColor
                mPaint!!.strokeWidth = shortLineWidth
                // 从文字下方开始绘制刻度
                canvas.drawLine(
                    distance, textBaseline + textGradationGap,
                    distance, textBaseline + textGradationGap + shortGradationLen, mPaint
                )
            }

            // 移动到下一个刻度
            currentNum += mNumberUnit
        }
        
        // 恢复文本画笔的透明度
        mTextPaint!!.alpha = 255
    }

    /**
     * 绘制指针
     * 绘制中间的垂直指示线，显示当前刻度位置
     *
     * @param canvas 画布
     */
    private fun drawIndicator(canvas: Canvas) {
        // 保存画布状态
        canvas.save()
        // 平移画布，处理padding
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())


        // 计算指示器的起始位置，与刻度底部对齐
        val startY =
            textSize + gradationNumberGap + textGradationGap + longGradationLen - indicatorLineLen


        // 设置指示器线的颜色和宽度
        mPaint!!.color = indicatorLineColor
        mPaint!!.strokeWidth = indicatorLineWidth
        // 设置圆头画笔，使指示线两端圆滑
        mPaint!!.strokeCap = Paint.Cap.ROUND
        // 在中心位置绘制指示器，长度为indicatorLineLen
        canvas.drawLine(
            mHalfWidth.toFloat(),
            startY,
            mHalfWidth.toFloat(),
            startY + indicatorLineLen,
            mPaint
        )
        // 重置为默认形状画笔
        mPaint!!.strokeCap = Paint.Cap.BUTT


        // 恢复画布状态
        canvas.restore()
    }

    /**
     * 将dp值转换为像素值
     *
     * @param dp 密度无关像素值
     * @return 实际像素值
     */
    private fun dp2px(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
            .toInt()
    }

    /**
     * 将sp值转换为像素值
     *
     * @param sp 缩放无关像素值
     * @return 实际像素值
     */
    private fun sp2px(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }

    /**
     * 输出调试日志
     * 仅在DEBUG模式下输出
     *
     * @param format 格式化字符串
     * @param args 参数列表
     */
    private fun logD(format: String, vararg args: Any) {
        if (LOG_ENABLE) {
            Log.d("GradationView", String.format("zjun@$format", *args))
        }
    }

    //=============================== 公共接口方法 ===============================
    /**
     * 设置新的当前值
     * 以动画方式滑动到指定值
     *
     * @param currentValue 目标值
     * @throws IllegalArgumentException 如果值超出范围
     */
    fun setCurrentValue(currentValue: Float) {
        // 检查值是否在有效范围内
        require(!(currentValue < minValue || currentValue > maxValue)) {
            String.format(
                "当前值 %f 超出有效范围: [%f, %f]",
                currentValue, minValue, maxValue
            )
        }

        // 强制结束当前滚动动画
        if (!mScroller!!.isFinished) {
            mScroller!!.forceFinished(true)
        }

        // 更新当前值
        this.currentValue = currentValue
        mCurrentNumber = (this.currentValue * 10).toInt()

        // 使用区间规则计算新位置
        val newDistance = calculateDistanceFromNumber(mCurrentNumber)
        val dx = (newDistance - mCurrentDistance).toInt()

        // 根据距离计算动画时长，最大2000ms
        // 使用比例计算，使动画时长与距离成正比
        val proportion = newDistance / mNumberRangeDistance
        val duration = min(
            (abs(dx.toDouble()) * MAX_VALUE_CHANGE_DURATION / mNumberRangeDistance).toDouble(),
            MAX_VALUE_CHANGE_DURATION.toDouble()
        ).toInt()

        // 启动滚动动画
        mScroller!!.startScroll(mCurrentDistance.toInt(), 0, dx, 0, duration)
        
        // 立即触发一次值变化回调，不等待动画完成
        mValueChangedListener?.onValueChanged(currentValue)
        mScrollStopListener?.onScrollStop(currentValue, formatValueToLabel(currentValue))
        
        // 请求重绘，触发动画
        postInvalidate()
    }

    /**
     * 设置不同区间的刻度间隔
     * 允许在不同的值范围内使用不同的像素间距
     *
     * @param gapRules 刻度间隔规则列表，每个规则定义一个区间和对应的像素间距
     * @param initialValue 可选参数，设置初始刻度值。如果不指定，保持当前值
     */
    @JvmOverloads
    fun setGradationGapRules(gapRules: List<GradationGapRule>, initialValue: Float? = null) {
        // 确保规则列表不为空
        if (gapRules.isEmpty()) {
            return
        }

        // 验证规则列表覆盖了整个值范围
        val sortedRules = gapRules.sortedBy { it.startValue }
        
        // 更新视图的最小值和最大值
        this.minValue = sortedRules.first().startValue
        this.maxValue = sortedRules.last().endValue
        
        // 如果指定了初始值，使用它；否则确保当前值在新的范围内
        if (initialValue != null) {
            currentValue = min(max(initialValue, minValue), maxValue)
        } else if (currentValue < minValue) {
            currentValue = minValue
        } else if (currentValue > maxValue) {
            currentValue = maxValue
        }
        
        // 检查第一个规则的起始值是否等于最小值
        require(abs(sortedRules.first().startValue - minValue) < FLOAT_PRECISION) {
            "第一个规则的起始值(${sortedRules.first().startValue})必须等于最小值($minValue)"
        }
        
        // 检查最后一个规则的结束值是否等于最大值
        require(abs(sortedRules.last().endValue - maxValue) < FLOAT_PRECISION) {
            "最后一个规则的结束值(${sortedRules.last().endValue})必须等于最大值($maxValue)"
        }
        
        // 检查规则是否连续且不重叠
        for (i in 0 until sortedRules.size - 1) {
            require(abs(sortedRules[i].endValue - sortedRules[i + 1].startValue) < FLOAT_PRECISION) {
                "规则必须连续且不重叠：规则${i}的结束值(${sortedRules[i].endValue})必须等于规则${i+1}的起始值(${sortedRules[i + 1].startValue})"
            }
        }
        
        // 保存规则列表
        mGradationGapRules = sortedRules.toMutableList()
        
        // 转换值并重新计算内部参数
        convertValue2Number()

        // 通知值变化
        if (mValueChangedListener != null) {
            mValueChangedListener!!.onValueChanged(currentValue)
        }
        
        postInvalidate()
    }

    /**
     * 重新计算基于规则的距离值
     */
    private fun recalculateDistances() {
        if (mGradationGapRules == null || mGradationGapRules.isEmpty()) {

            return
        }

        // 重新计算总距离
        mNumberRangeDistance = 0f
        for (rule in mGradationGapRules) {
            val startNum = (rule.startValue * 10).toInt()
            val endNum = (rule.endValue * 10).toInt()
            val rangeNumber = endNum - startNum
            mNumberRangeDistance += rangeNumber / mNumberUnit.toFloat() * rule.gapPx
        }
        
        // 重新计算当前距离
        mCurrentDistance = 0f
        var currentNum = mMinNumber
        for (rule in mGradationGapRules) {
            val startNum = (rule.startValue * 10).toInt()
            val endNum = (rule.endValue * 10).toInt()
            
            if (mCurrentNumber <= endNum) {
                // 当前值在这个规则范围内
                mCurrentDistance += (mCurrentNumber - startNum) / mNumberUnit.toFloat() * rule.gapPx
                break
            } else {
                // 累加这个规则范围的全部距离
                mCurrentDistance += (endNum - startNum) / mNumberUnit.toFloat() * rule.gapPx
                currentNum = endNum
            }
        }
    }
    
    /**
     * 根据当前距离计算对应的值，并返回刻度值
     * 考虑不同区间的刻度间隔
     *
     * @param distance 当前距离
     * @param roundMode 取整模式: true表示四舍五入到最近刻度, false表示向下取整
     * @return 对应的刻度值（放大10倍的整数）
     */
    private fun calculateNumberFromDistance(distance: Float, roundMode: Boolean = true): Int {
        if (mGradationGapRules.isEmpty()) {
            return 0
        }
        
        var remainingDistance = distance
        var currentNum = mMinNumber
        
        for (rule in mGradationGapRules) {
            val startNum = (rule.startValue * 10).toInt()
            val endNum = (rule.endValue * 10).toInt()
            val rangeNumber = endNum - startNum
            val ruleDistance = rangeNumber / mNumberUnit.toFloat() * rule.gapPx
            
            if (remainingDistance <= ruleDistance) {
                // 在当前规则范围内
                // 计算在当前规则下的精确刻度位置
                val exactScale = remainingDistance / rule.gapPx
                val scaleNumber = if (roundMode) {
                    Math.round(exactScale)  // 四舍五入到最近的刻度
                } else {
                    Math.floor(exactScale.toDouble()).toInt()  // 向下取整到最近的刻度
                }
                val exactNumber = startNum + (scaleNumber * mNumberUnit)
                
                // 确保结果在当前规则范围内
                return min(max(exactNumber, startNum), endNum)
            } else {
                // 超过当前规则范围
                remainingDistance -= ruleDistance
                currentNum = endNum
            }
        }
        
        // 如果超出范围，返回最大值
        return mMaxNumber
    }

    /**
     * 根据值计算对应的距离
     * 考虑不同区间的刻度间隔
     *
     * @param number 值（放大10倍的整数）
     * @return 对应的距离
     */
    private fun calculateDistanceFromNumber(number: Int): Float {
        if (mGradationGapRules.isEmpty()) {
            // 如果没有规则，使用默认计算方式
            // 计算默认间距
            val totalScaleCount = ((maxValue - minValue) / gradationUnit).toInt() + 1
            val defaultGapPx = if (totalScaleCount > 1 && mWidth > 0) {
                mWidth.toFloat() / (totalScaleCount - 1)
            } else {
                1f // 防止除以零
            }
            
            return (number - mMinNumber) / mNumberUnit.toFloat() * defaultGapPx
        }
        
        var distance = 0f
        var currentNum = mMinNumber
        
        for (rule in mGradationGapRules) {
            val startNum = (rule.startValue * 10).toInt()
            val endNum = (rule.endValue * 10).toInt()
            
            if (number <= endNum) {
                // 在当前规则范围内
                distance += (number - startNum) / mNumberUnit.toFloat() * rule.gapPx
                return distance
            } else {
                // 累加这个规则范围的全部距离
                distance += (endNum - startNum) / mNumberUnit.toFloat() * rule.gapPx
                currentNum = endNum
            }
        }
        
        // 如果超出范围，返回最大值对应的距离
        return mNumberRangeDistance
    }

    private val contextSafely: Context?
        /**
         * 安全获取Context，避免内存泄漏
         * 使用弱引用防止长期持有Context
         *
         * @return Context实例，可能为null
         */
        get() {
            if (mContextRef != null) {
                return mContextRef!!.get()
            }
            return null
        }

    /**
     * 视图可见性变化时的回调
     * 在视图不可见时停止动画，减少资源消耗
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE && mScroller != null) {
            // 视图不可见时停止动画
            mScroller!!.abortAnimation()
        }
    }

    /**
     * 视图从窗口分离时的回调
     * 释放资源，避免内存泄漏
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 释放资源
        release()
    }

    /**
     * 释放资源方法，防止内存泄漏
     * 可在Activity/Fragment的onDestroy中主动调用
     */
    fun release() {
        // 避免重复释放
        if (mIsReleased) {
            return
        }
        // 设置释放标志
        mIsReleased = true
        // 停止所有动画
        if (mScroller != null) {
            mScroller!!.abortAnimation()
        }
        // 释放速度跟踪器
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
        // 移除所有回调
        mValueChangedListener = null
        mScrollStopListener = null
        // 移除context引用
        if (mContextRef != null) {
            mContextRef!!.clear()
            mContextRef = null
        }
        // 最后一次绘制
        invalidate()
    }

    companion object {
        /** 是否启用日志输出，仅在DEBUG模式下输出  */
        private val LOG_ENABLE = BuildConfig.DEBUG

        //=============================== 默认颜色值 ===============================
        /** 默认背景颜色：浅绿色  */
        private const val DEFAULT_BG_COLOR = "#f5f8f5"
        /** 默认指示器颜色：绿色  */
        private const val DEFAULT_INDICATOR_COLOR = "#48b975"

        //=============================== 默认尺寸（dp）===============================
        /** 默认短刻度线宽度，单位dp  */
        private const val DEFAULT_SHORT_LINE_WIDTH_DP = 1f
        /** 默认短刻度线长度，单位dp  */
        private const val DEFAULT_SHORT_GRADATION_LEN_DP = 16f
        /** 默认指示器线宽度，单位dp  */
        private const val DEFAULT_INDICATOR_LINE_WIDTH_DP = 3f
        /** 默认指示器线长度，单位dp  */
        private const val DEFAULT_INDICATOR_LINE_LEN_DP = 35f
        /** 默认刻度与数字间距，单位dp  */
        private const val DEFAULT_GRADATION_NUMBER_GAP_DP = 8f
        /** 默认文字与刻度间距，单位dp  */
        private const val DEFAULT_TEXT_GRADATION_GAP_DP = 5f
        /** 默认内容高度，单位dp  */
        private const val DEFAULT_CONTENT_HEIGHT_DP = 90

        //=============================== 默认文本大小（sp）===============================
        /** 默认文本大小，单位sp  */
        private const val DEFAULT_TEXT_SIZE_SP = 14f

        //=============================== 默认数值参数 ===============================
        /** 默认最小值  */
        private const val DEFAULT_MIN_VALUE = 0f
        /** 默认最大值  */
        private const val DEFAULT_MAX_VALUE = 100f
        /** 默认当前值  */
        private const val DEFAULT_CURRENT_VALUE = 50f
        /** 默认刻度单位值  */
        private const val DEFAULT_GRADATION_UNIT = 0.1f
        /** 默认每个主刻度包含的子刻度数量  */
        private const val DEFAULT_NUMBER_PER_COUNT = 10

        //=============================== 动画相关 ===============================
        /** 最大滚动动画时间，单位毫秒  */
        private const val MAX_SCROLL_DURATION = 800
        /** 值变化最大动画时间，单位毫秒  */
        private const val MAX_VALUE_CHANGE_DURATION = 2000
        /** 对齐动画时间，单位毫秒  */
        private const val ALIGN_ANIMATION_DURATION = 150
        /** 速度阈值系数，用于判断是否需要进行刻度对齐  */
        private const val VELOCITY_THRESHOLD_FACTOR = 0.8f
        /** 刻度检查范围，用于确定何时触发对齐动画  */
        private const val GRADATION_CHECK_RANGE = 3

        //=============================== 文本渐变相关 ===============================
        /** 文本渐变距离系数，控制文本在接近中心线时的渐变效果  */
        private const val TEXT_FADE_DISTANCE_FACTOR = 4.0f
        /** 最大透明度值  */
        private const val MAX_ALPHA = 255

        //=============================== 扩展单位相关 ===============================
        /** 扩展单位位移量，用于计算左右两侧额外绘制的刻度数量  */
        private const val EXTEND_UNIT_SHIFT = 1
        /** 浮点数比较精度  */
        private val FLOAT_PRECISION = 0.0001f
    }

    /**
     * 刻度间隔规则类
     * 定义一个值区间及其对应的像素间隔
     */
    data class GradationGapRule(
        val startValue: Float,  // 区间起始值
        val endValue: Float,    // 区间结束值
        val gapPx: Float        // 像素间隔
    )
    
    // 添加到类的成员变量
    /** 不同区间的刻度间隔规则  */
    private var mGradationGapRules: MutableList<GradationGapRule> = mutableListOf()

    /**
     * 设置当前值变化监听器
     *
     * @param listener 监听器实例
     */
    fun setOnValueChangedListener(listener: IOnValueChangedListener?) {
        this.mValueChangedListener = listener
    }

    /**
     * 设置滑动停止监听器
     *
     * @param listener 监听器实例
     */
    fun setOnScrollStopListener(listener: IOnScrollStopListener?) {
        this.mScrollStopListener = listener
    }

    /**
     * 格式化值为标签文本
     * @param value 需要格式化的值
     * @return 格式化后的标签文本
     */
    private fun formatValueToLabel(value: Float): String {
        var label = value.toString()
        // 去掉小数点后的.0
        if (label.endsWith(".0")) {
            label = label.substring(0, label.length - 2)
        }
        // 添加单位标识
        return "${label}x"
    }
}
