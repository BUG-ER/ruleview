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
 * 滑动方向枚举
 * 用于表示刻度尺滑动和吸附的方向
 */
enum class SlideDirection {
    NONE, // 无方向/静止
    LEFT, // 向左滑动（手指右滑，刻度左移）
    RIGHT; // 向右滑动（手指左滑，刻度右移）

    companion object {
        /**
         * 根据dx值确定滑动方向
         * @param dx 水平方向上的位移差值
         * @param threshold 判断有效移动的阈值，默认为1
         * @return 对应的滑动方向
         */
        fun fromDelta(dx: Int, threshold: Int = 1): SlideDirection {
            return when {
                dx > threshold -> RIGHT   // 手指向右移动，刻度向左滑
                dx < -threshold -> LEFT   // 手指向左移动，刻度向右滑
                else -> NONE  // 无明确方向或移动幅度太小
            }
        }
    }
}

/**
 * 特殊长刻度规则类
 * 定义需要显示为长刻度的特殊值
 */
data class SpecialGradationRule(
    val value: Float,  // 特殊刻度值
    val showText: Boolean = true  // 是否显示文本，默认显示
)

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
    private var mNumberUnit = 1

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

    /** 特殊长刻度规则列表  */
    private var mSpecialGradations: MutableList<SpecialGradationRule> = mutableListOf()

    /** 当前是否处于吸附状态 */
    private var isSnapped = false
    
    /** 吸附时的刻度值 */
    private var mSnappedValue = 0
    
    /** 吸附时的滑动方向 */
    private var mSnapDirection: SlideDirection = SlideDirection.NONE
    
    /** 脱离吸附需要的最小距离（像素） */
    private var mSnapEscapeDistance = 0f

    /** 吸附的检测阈值（像素） */
    private var mSnapTriggerDistance = 0f

    /** 上一次移动的位置，用于判断方向 */
    private var mLastMoveX = 0f


    /** 记录按下时的时间戳 */
    private var mTouchDownTime: Long = 0
    
    /** 记录吸附开始时的时间戳 */
    private var mLastSnappedTime: Long = 0
    
    /** 记录吸附开始时的吸附值 */
    private var mLastSnappedValue: Int = 0

    /** 所有可能的吸附点集合，缓存起来避免重复计算 */
    private var mAllSnapPoints: MutableMap<Int, Int> = mutableMapOf()

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
        
        // 计算吸附相关的距离，8dp和5dp转为像素
        mSnapEscapeDistance = dp2px(17f).toFloat()
        mSnapTriggerDistance = dp2px(3f).toFloat()
        
        Log.d("GradationView", "初始化 - 吸附脱离距离: ${mSnapEscapeDistance}px (12dp), 吸附触发距离: ${mSnapTriggerDistance}px (3dp)")
        
    }

    /**
     * 初始化所有可能的吸附点
     * 预先计算所有长刻度和特殊刻度的位置，并保存到缓存中
     */
    private fun initAllSnapPoints() {
        // 清空现有的吸附点缓存
        mAllSnapPoints.clear()
        
        // 只有在有效范围内才计算
        if (mMinNumber >= mMaxNumber || mNumberUnit <= 0) {
            return
        }
        
        // 计算相邻两条长刻度线之间的刻度数量
        val perUnitCount = mNumberUnit * numberPerCount
        
        // 添加所有长刻度点（整数刻度）
        var currentNum = (mMinNumber / perUnitCount) * perUnitCount
        while (currentNum <= mMaxNumber) {
            if (currentNum >= mMinNumber) {
                mAllSnapPoints[currentNum] = currentNum
            }
            currentNum += perUnitCount
        }
        
        // 添加所有特殊刻度点
        for (special in mSpecialGradations) {
            val specialNum = (special.value * 10).toInt()
            // 只添加有效范围内的特殊刻度
            if (specialNum >= mMinNumber && specialNum <= mMaxNumber) {
                mAllSnapPoints[specialNum] = specialNum
            }
        }
        
        Log.d("GradationView", "初始化吸附点 - 共计算${mAllSnapPoints.size}个可能的吸附点")
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
        
        // 当值范围或单位改变时，需要重新计算所有可能的吸附点
        initAllSnapPoints()
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
                // 记录按下时间
                mTouchDownTime = System.currentTimeMillis()
                // 重置移动标志
                isMoved = false
                // 重置初始移动位置
                Log.d("GradationView", "按下事件 - 初始位置: x=$x")
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
                        mLastX = x
                        mLastY = y
                        return true
                    }
                    // 设置已开始滑动标志
                    isMoved = true
                }
                
                // 添加日志，输出滑动信息
                Log.d("GradationView", "滑动事件 - 偏移量: dx=$dx, 绝对位置: x=$x, mLastX=$mLastX, 当前距离: mCurrentDistance=$mCurrentDistance")

                // 处理方向性吸附
                if (isSnapped) {
                    // 计算当前滑动方向（只考虑有效的移动）
                    val currentDirection = SlideDirection.fromDelta(dx)

                    // 如果有明确的滑动方向，并且与吸附方向相反，立即解除吸附
                    if (currentDirection != SlideDirection.NONE && currentDirection != mSnapDirection) {
                        isSnapped = false
                        Log.d("GradationView", "滑动事件 - 方向改变，解除吸附: 新方向=$currentDirection, 原方向=$mSnapDirection, 起始位置=$mLastMoveX,   mSnappedValue=${mSnappedValue}  当前位置=$x")
                        
                        // 保存当前的吸附值，防止立即再次吸附到同一位置
                        val lastSnappedValue = mSnappedValue
                        mSnapDirection = SlideDirection.NONE
                        
                        // 添加临时锁定逻辑，防止立即再次吸附
                        mLastSnappedTime = System.currentTimeMillis()
                        mLastSnappedValue = lastSnappedValue
                    } else {
                        // 同方向滑动，检查从原始吸附位置移动的总距离
                        val movedDistance = abs(x - mLastMoveX)
                        
                        // 添加日志，输出吸附状态信息
                        Log.d("GradationView", "滑动事件 - 吸附状态:   dx ${dx} currentDirection  ${currentDirection}  movedDistance=$movedDistance, 阈值=$mSnapEscapeDistance, 吸附值=${mSnappedValue/10f}, 起始位置=$mLastMoveX, 当前位置=$x, 方向=$currentDirection")
                        
                        // 如果移动距离未超过阈值，保持在吸附位置
                        if (movedDistance < mSnapEscapeDistance) {
                            mLastX = x
                            mLastY = y
                            return true
                        } else {
                            // 超过阈值，解除吸附
                            isSnapped = false
                            mSnapDirection = SlideDirection.NONE
                            
                            // 保存当前的吸附值，防止立即再次吸附到同一位置
                            val lastSnappedValue = mSnappedValue
                            
                            Log.d("GradationView", "滑动事件 - 移动距离超过阈值，解除吸附: movedDistance=$movedDistance, 吸附值=${lastSnappedValue/10f}, 起始位置=$mLastMoveX")
                            
                            // 添加临时锁定逻辑，防止立即再次吸附
                            mLastSnappedTime = System.currentTimeMillis()
                            mLastSnappedValue = lastSnappedValue
                        }
                    }
                }

                // 更新当前距离（注意dx取反，手指右滑，刻度左移）
                val oldDistance = mCurrentDistance
                mCurrentDistance += -dx.toFloat()
                
                // 添加日志，输出距离变化
                Log.d("GradationView", "滑动事件 - 距离更新: 旧距离=$oldDistance, 新距离=$mCurrentDistance, 变化=${-dx.toFloat()}")
                
                // 检查是否需要吸附到长刻度
                checkAndSnapToLongGradation(dx)
                
                // 计算新的当前值
                calculateValue()
                
                // 只有在非吸附状态下才更新最后移动的位置
                if (!isSnapped) {
                    mLastMoveX = x.toFloat()
                    Log.d("GradationView", "更新最后移动位置: $mLastMoveX (非吸附状态)")
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isMoved) {
                    // 计算触摸持续时间
                    val touchDuration = System.currentTimeMillis() - mTouchDownTime
                    // 只有在触摸时间小于阈值时才处理点击事件
                    return if (touchDuration <= MAX_CLICK_DURATION) {
                        handleClickEvent(x)
                    } else {
                        true
                    }
                }

                // 重置吸附状态
                isSnapped = false
                mSnapDirection = SlideDirection.NONE

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
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                // 处理取消事件，与UP事件相同
                if (!isMoved) {
                    // 这是一个点击被取消的事件，保持当前位置不变
                    return true
                }

                // 重置吸附状态
                isSnapped = false
                mSnapDirection = SlideDirection.NONE

                // 处理滑动被取消（类似手指抬起）
                mVelocityTracker!!.computeCurrentVelocity(1000, MAX_FLING_VELOCITY.toFloat())
                val xVelocity = mVelocityTracker!!.xVelocity.toInt()
                if (abs(xVelocity.toDouble()) >= MIN_FLING_VELOCITY) {
                    mScroller!!.fling(
                        mCurrentDistance.toInt(), 0, -xVelocity, 0,
                        0, mNumberRangeDistance.toInt(), 0, 0
                    )
                    invalidate()
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
     * 处理点击事件
     * 计算点击位置对应的值并滚动到该位置
     *
     * @param x 点击的x坐标
     * @return true表示事件已处理
     */
    private fun handleClickEvent(x: Int): Boolean {
        val clickX = x - paddingLeft
        val clickDistance = (clickX - mHalfWidth) + mCurrentDistance
        // 限定点击范围在有效区间内
        val validDistance = min(max(clickDistance, 0f), mNumberRangeDistance)

        logD("点击事件 - 原始坐标: x=%s, clickX=%s, mHalfWidth=%d, paddingLeft=%d",
            x.toString(), clickX.toString(), mHalfWidth, paddingLeft)
        logD("点击事件 - 距离计算: clickDistance=%s, validDistance=%s, mCurrentDistance=%s",
            clickDistance.toString(), validDistance.toString(), mCurrentDistance.toString())
        
        // 记录当前值和最大值信息，便于对比
        Log.d("GradationView", "点击事件 - 系统状态: 当前值=$currentValue, 最大值=$maxValue, 整数当前值=$mCurrentNumber, 整数最大值=$mMaxNumber")
        Log.d("GradationView", "点击事件 - 距离范围: 当前距离=$mCurrentDistance, 最大距离=$mNumberRangeDistance")

        // 计算目标值并滚动
        var targetNumber = calculateNumberFromDistance(validDistance, true)
        Log.d("GradationView", "点击事件 - 初步计算: validDistance=$validDistance -> targetNumber=$targetNumber (${targetNumber/10f}x)")

        // 获取点击位置附近的特殊点和长刻度点
        val nearbyPoints = getSnapPoints(targetNumber)
        Log.d("GradationView", "点击事件 - 附近刻度点: ${nearbyPoints.map { it/10f }}")

        // 如果有点击位置附近的特殊点，判断是否应该直接跳转到特殊点
        val perUnitCount = mNumberUnit * numberPerCount
        
        // 在刻度图上查找最近的刻度
        for (point in nearbyPoints) {
            val pointDistance = calculateDistanceFromNumber(point)
            val distanceToPoint = abs(validDistance - pointDistance)
            
            // 如果点击位置非常接近某个刻度（特别是长刻度或特殊刻度），优先选择该刻度
            // 这里使用屏幕像素距离而不是值距离，更准确地反映用户的点击意图
            Log.d("GradationView", "点击事件 - 检查点 ${point/10f}x: 点距离=$pointDistance, 到点距离=$distanceToPoint, 触发阈值=${mSnapTriggerDistance * 2}")
            if (distanceToPoint < mSnapTriggerDistance * 2) {
                Log.d("GradationView", "点击事件 - 检测到接近的特殊刻度: ${point/10f}x, 距离=$distanceToPoint")
                targetNumber = point
                break
            }
        }

        logD("点击事件 - 目标计算: targetNumber=%d (值=%s)",
            targetNumber, (targetNumber / 10f).toString())

        Log.d("GradationView", "点击事件 - 最终确定目标: targetNumber=$targetNumber, 值=${targetNumber/10f}x")

        // 确保严格使用计算出的值，防止精度丢失
        val exactValue = targetNumber / 10f
        Log.d("GradationView", "点击事件 - 转换为精确值: $exactValue")
        
        currentValue = exactValue
        mCurrentNumber = targetNumber

        // 输出目标值的最终确认信息
        Log.d("GradationView", "点击事件 - 调用setCurrentValue前: exactValue=$exactValue, targetNumber=$targetNumber")
        
        // 直接使用 setCurrentValue，它会处理平滑滚动和回调
        setCurrentValue(exactValue)
        
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
        // 转回浮点值 - 保持精确的转换，不引入新的精度损失
        val newValue = mCurrentNumber / 10f

        // 确保当前值和整数的一致性
        currentValue = newValue

        // 记录日志
        logD(
            "calculateValue: mCurrentDistance=%f, mCurrentNumber=%d, currentValue=%f",
            mCurrentDistance, mCurrentNumber, currentValue
        )

        // 添加更详细的日志输出
        Log.d("GradationView", "值计算 - 当前距离=$mCurrentDistance, 当前值=$currentValue, 整数值=$mCurrentNumber, 原始值=$oldNumber")

        // 如果有监听器，通知值变化
        if (mValueChangedListener != null && oldNumber != mCurrentNumber) {
            // 添加额外日志，确保传递给回调的值是准确的
            Log.d("GradationView", "触发值变化回调 - 当前值=$currentValue, 整数值=$mCurrentNumber, 原始值=$oldNumber")
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
                val prevDistance = mCurrentDistance
                mCurrentDistance = mScroller!!.currX.toFloat()
                
                // 记录滚动进度
                Log.d("GradationView", "滚动进行中 - 上次距离=$prevDistance, 当前距离=$mCurrentDistance, 最终距离=${mScroller!!.finalX}")

                // 直接根据当前距离计算并更新当前值
                calculateValue()
                
                // 重绘视图
                invalidate()
            } else if (!mScroller!!.isFinished) {
                // 滚动结束，但动画未完成，主动结束
                Log.d("GradationView", "滚动到达终点 - 距离=${mScroller!!.currX}, 强制结束")
                mScroller!!.forceFinished(true)
                
                // 确保最终值是准确的
                val finalNumber = calculateNumberFromDistance(mCurrentDistance, true)
                val finalValue = finalNumber / 10f
                
                // 如果最终值与预期不符，记录一下
                if (abs(finalValue - currentValue) > 0.001f) {
                    Log.d("GradationView", "滚动结束值不匹配 - 计算得到=$finalValue, 当前值=$currentValue")
                    
                    // 可以考虑在这里纠正值
                    currentValue = finalValue
                    mCurrentNumber = finalNumber
                    
                    // 触发最终值的回调
                    mValueChangedListener?.onValueChanged(finalValue)
                    mScrollStopListener?.onScrollStop(finalValue, formatValueToLabel(finalValue))
                }
                
                // 重绘最后一次
                invalidate()
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
        val expendUnit = mNumberUnit shl EXTEND_UNIT_SHIFT
        val startNum = max(mMinNumber, leftValue - expendUnit)
        val endNum = min(mMaxNumber, rightValue + expendUnit)

        // 计算相邻两条长刻度线之间的刻度数量
        val perUnitCount = mNumberUnit * numberPerCount

        // 依次绘制每个可见的刻度
        var currentNum = startNum
        while (currentNum <= endNum) {
            // 计算当前刻度对应的x坐标
            val distance = mHalfWidth + (calculateDistanceFromNumber(currentNum) - mCurrentDistance)

            // 判断是否是长刻度（整数倍刻度）或特殊刻度
            val currentValue = currentNum / 10f
            val isSpecialGradation = mSpecialGradations.any { it.value == currentValue }
            val isLongGradation = currentNum % perUnitCount == 0 || isSpecialGradation

            if (isLongGradation) {
                // 长刻度：使用长刻度颜色和宽度
                mPaint!!.color = longLineColor
                mPaint!!.strokeWidth = longLineWidth

                // 从文字下方开始绘制刻度
                canvas.drawLine(
                    distance, textBaseline + textGradationGap,
                    distance, textBaseline + textGradationGap + longGradationLen, mPaint
                )

                // 判断是否需要绘制文本
                val shouldShowText = currentNum % perUnitCount == 0 ||
                    mSpecialGradations.find { it.value == currentValue }?.showText == true

                if (shouldShowText) {
                    // 绘制刻度值文本
                    val fNum = currentValue
                    val text = buildString {
                        append(fNum.toString())
                        // 去掉小数点后的.0
                        if (endsWith(".0")) {
                            setLength(length - 2)
                        }
                        // 添加单位标识
                        append("x")
                    }

                    // 计算文本宽度
                    val textWidth = mTextPaint!!.measureText(text)

                    // 计算当前刻度与中心线的距离，用于文本透明度渐变
                    val distanceToCenter = abs((distance - mHalfWidth).toDouble()).toFloat()
                    // 设置文本透明度
                    val alpha = if (distanceToCenter < textWidth * TEXT_FADE_DISTANCE_FACTOR) {
                        val ratio = distanceToCenter / (textWidth * TEXT_FADE_DISTANCE_FACTOR)
                        (MAX_ALPHA * (ratio * ratio)).toInt()
                    } else {
                        MAX_ALPHA
                    }
                    mTextPaint!!.alpha = alpha

                    // 在刻度上方居中绘制文字
                    canvas.drawText(text, distance - textWidth * .5f, textBaseline, mTextPaint)
                }
            } else {
                // 短刻度
                mPaint!!.color = shortLineColor
                mPaint!!.strokeWidth = shortLineWidth
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

        // 保存原始传入值，用于对比
        val inputValue = currentValue
        
        // 特殊处理整数值和边界值，提高精度
        val targetValue = when {
            // 如果是最大值或非常接近最大值，使用精确的最大值
            currentValue > maxValue - 0.05f -> {
                Log.d("GradationView", "setCurrentValue - 检测到接近最大值: $currentValue -> $maxValue")
                maxValue
            }
            // 如果是最小值或非常接近最小值，使用精确的最小值
            currentValue < minValue + 0.05f -> {
                Log.d("GradationView", "setCurrentValue - 检测到接近最小值: $currentValue -> $minValue")
                minValue
            }
            // 如果是整数值(如3.0、4.0)，确保使用精确的整数值
            abs(currentValue - Math.round(currentValue).toFloat()) < 0.05f -> {
                val intValue = Math.round(currentValue).toFloat()
                Log.d("GradationView", "setCurrentValue - 检测到接近整数值: $currentValue -> $intValue")
                intValue
            }
            // 其他情况正常处理
            else -> currentValue
        }

        // 更新当前值
        this.currentValue = targetValue
        val oldNumber = mCurrentNumber
        mCurrentNumber = (this.currentValue * 10).toInt()

        Log.d("GradationView", "setCurrentValue - 值处理: 输入值=$inputValue, 目标值=$targetValue, 整数表示前=$oldNumber, 后=$mCurrentNumber")

        // 使用区间规则计算新位置
        val newDistance = calculateDistanceFromNumber(mCurrentNumber)
        
        // 对于最大值和最小值，确保使用精确的像素距离
        val targetDistance = when {
            mCurrentNumber >= mMaxNumber - 1 -> {
                Log.d("GradationView", "setCurrentValue - 使用最大距离: $mNumberRangeDistance")
                mNumberRangeDistance
            }
            mCurrentNumber <= mMinNumber + 1 -> {
                Log.d("GradationView", "setCurrentValue - 使用最小距离: 0")
                0f
            }
            else -> newDistance
        }
        
        val dx = (targetDistance - mCurrentDistance).toInt()

        // 根据距离计算动画时长，最大2000ms
        // 使用比例计算，使动画时长与距离成正比
        val proportion = targetDistance / mNumberRangeDistance
        val duration = min(
            (abs(dx.toDouble()) * MAX_VALUE_CHANGE_DURATION / mNumberRangeDistance).toDouble(),
            MAX_VALUE_CHANGE_DURATION.toDouble()
        ).toInt()

        Log.d("GradationView", "setCurrentValue - 滚动设置: 当前距离=$mCurrentDistance, 目标距离=$targetDistance, 差值=$dx, 持续时间=$duration")

        // 启动滚动动画
        mScroller!!.startScroll(mCurrentDistance.toInt(), 0, dx, 0, duration)
        
        // 立即触发一次值变化回调，不等待动画完成
        if (mValueChangedListener != null) {
            Log.d("GradationView", "setCurrentValue - 触发回调: $targetValue")
            mValueChangedListener!!.onValueChanged(targetValue)
        }
        if (mScrollStopListener != null) {
            mScrollStopListener!!.onScrollStop(targetValue, formatValueToLabel(targetValue))
        }
        
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

        /** 有效点击的最大时长（毫秒） */
        private const val MAX_CLICK_DURATION = 80L
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

    /**
     * 设置特殊长刻度
     * 这些刻度会像主刻度一样显示为长刻度，并可选择是否显示文本
     *
     * @param specialValues 特殊刻度值列表
     */
    fun setSpecialGradations(specialValues: List<SpecialGradationRule>) {
        mSpecialGradations = specialValues.toMutableList()
        // 特殊刻度改变时，需要重新计算所有可能的吸附点
        initAllSnapPoints()
        invalidate()
    }

    /**
     * 获取当前位置附近所有可能的吸附点
     * 从缓存中筛选出当前位置附近的吸附点
     *
     * @param currentNum 当前值（放大10倍的整数）
     * @return 可能的吸附点列表
     */
    private fun getSnapPoints(currentNum: Int): List<Int> {
        val snapPoints = mutableListOf<Int>()
        
        // 如果缓存为空，先初始化
        if (mAllSnapPoints.isEmpty()) {
            initAllSnapPoints()
        }
        
        // 计算相邻两条长刻度线之间的刻度数量
        val perUnitCount = mNumberUnit * numberPerCount
        
        // 从缓存中筛选出当前位置附近的吸附点
        for (snapPoint in mAllSnapPoints.values) {
            // 只考虑当前位置附近的吸附点（在一个周期范围内）
            if (abs(currentNum - snapPoint) < perUnitCount) {
                snapPoints.add(snapPoint)
            }
        }
        
        return snapPoints
    }

    /**
     * 检查并吸附到长刻度
     * 当滑动经过长刻度点时，会有吸附效果
     * 
     * @param dx 当前X方向移动的距离
     */
    private fun checkAndSnapToLongGradation(dx: Int) {
        // 如果已经处于吸附状态，不需要检查
        if (isSnapped) {
            return
        }
        
        // 计算当前值对应的刻度
        val currentNum = calculateNumberFromDistance(mCurrentDistance, true)
        
        // 判断最近一次解除吸附的刻度值，避免在短时间内反复吸附同一刻度
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSnap = currentTime - mLastSnappedTime
        
        // 如果距离上次吸附时间过短且当前值接近上次吸附值，则跳过吸附
        if (timeSinceLastSnap < 300 && abs(currentNum - mLastSnappedValue) <= mNumberUnit) {
            Log.d("GradationView", "忽略吸附 - 刚刚解除吸附: 时间差=$timeSinceLastSnap ms, 当前值=$currentNum, 上次吸附值=$mLastSnappedValue")
            return
        }
        
        // 获取所有可能的吸附点
        val snapPoints = getSnapPoints(currentNum)
        
        // 记录日志，输出可能的吸附点
        Log.d("GradationView", "可能的吸附点: ${snapPoints.map { it/10f }}")
        
        // 寻找最近的吸附点
        var nearestSnapPoint: Int? = null
        var minDistance = Float.MAX_VALUE
        
        for (snapPoint in snapPoints) {
            val targetDistance = calculateDistanceFromNumber(snapPoint)
            val distance = abs(targetDistance - mCurrentDistance)
            
            if (distance < minDistance && distance < mSnapTriggerDistance) {
                minDistance = distance
                nearestSnapPoint = snapPoint
            }
        }
        
        // 如果找到最近的吸附点且距离足够近，触发吸附
        if (nearestSnapPoint != null) {
            val targetDistance = calculateDistanceFromNumber(nearestSnapPoint)
            
            Log.d("GradationView", "找到最近吸附点 - 值=${nearestSnapPoint/10f}, 目标距离=$targetDistance, 当前距离=$mCurrentDistance, 差值=$minDistance")
            
            // 设置吸附状态
            isSnapped = true
            mSnappedValue = nearestSnapPoint
            mSnapDirection = SlideDirection.fromDelta(dx)
            
            // 记录吸附开始时的触摸位置
            mLastMoveX = mLastX.toFloat()
            
            // 直接将当前距离设为目标距离（吸附）
            mCurrentDistance = targetDistance
            
            Log.d("GradationView", "触发吸附 - 吸附值=${nearestSnapPoint/10f}, 方向=$mSnapDirection, 吸附位置=$mCurrentDistance, 初始吸附手指位置=$mLastMoveX")
        }
    }
}
// 测试内容
