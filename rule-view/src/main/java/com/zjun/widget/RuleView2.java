package com.zjun.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;


/**
 * GradationView
 * 刻度卷尺控件
 *
 * 思路：
 *  1. 把float类型数据，乘10，转为int类型。之所以把它放在第一位，因为踩过这个坑：不转的话，由于精度问题，会导致计算异常复杂
 *  2. 绘制刻度：
 *     - 根据中间指针位置的数值，来计算最小值位置与中间指针位置的距离
 *     - 为了绘制性能，只绘制控件宽度范围内的刻度。但不出现数值突变（两侧刻度出现突然显示或不显示），两侧各增加2个单位
 *  3. 滑动时，通过移动最小位置与中间指针位置的距离，逆向推算当前刻度值
 *  4. 滑动停止后，自动调整到最近的刻度：使用滑动器Scroller，需要计算出最终要抵达的位置
 *  5. 惯性滑动：使用速度跟踪器VelocityTracker
 *
 * Author: Ralap
 * Description:
 * Date 2018/7/29
 */
public class RuleView2 extends View {
    private static final boolean LOG_ENABLE = BuildConfig.DEBUG;

    // 默认颜色值
    private static final String DEFAULT_BG_COLOR = "#f5f8f5";
    private static final String DEFAULT_INDICATOR_COLOR = "#48b975";

    // 默认尺寸（dp）
    private static final float DEFAULT_SHORT_LINE_WIDTH_DP = 1f;
    private static final float DEFAULT_SHORT_GRADATION_LEN_DP = 16f;
    private static final float DEFAULT_INDICATOR_LINE_WIDTH_DP = 3f;
    private static final float DEFAULT_INDICATOR_LINE_LEN_DP = 35f;
    private static final float DEFAULT_GRADATION_GAP_DP = 10f;
    private static final float DEFAULT_GRADATION_NUMBER_GAP_DP = 8f;
    private static final float DEFAULT_TEXT_GRADATION_GAP_DP = 5f;
    private static final int DEFAULT_CONTENT_HEIGHT_DP = 90;

    // 默认文本大小（sp）
    private static final float DEFAULT_TEXT_SIZE_SP = 14f;

    // 默认数值
    private static final float DEFAULT_MIN_VALUE = 0f;
    private static final float DEFAULT_MAX_VALUE = 100f;
    private static final float DEFAULT_CURRENT_VALUE = 50f;
    private static final float DEFAULT_GRADATION_UNIT = 0.1f;
    private static final int DEFAULT_NUMBER_PER_COUNT = 10;
    private static final float DEFAULT_SPLIT_VALUE = 2.0f;

    // 震动相关
    private static final long VIBRATION_DURATION = 20L;  // 震动持续时间（毫秒）

    // 动画相关
    private static final int MAX_SCROLL_DURATION = 800;  // 最大滚动动画时间（毫秒）
    private static final int MAX_VALUE_CHANGE_DURATION = 2000;  // 值变化最大动画时间（毫秒）
    private static final int ALIGN_ANIMATION_DURATION = 150;  // 对齐动画时间（毫秒）
    private static final float VELOCITY_THRESHOLD_FACTOR = 0.8f;  // 速度阈值系数
    private static final int GRADATION_CHECK_RANGE = 3;  // 刻度检查范围

    // 文本渐变相关
    private static final float TEXT_FADE_DISTANCE_FACTOR = 4.0f;  // 文本渐变距离系数
    private static final int MAX_ALPHA = 255;  // 最大透明度值

    // 数值转换相关
    private static final int NUMBER_MAGNIFICATION = 10;  // 数值放大倍数

    // 扩展单位相关
    private static final int EXTEND_UNIT_SHIFT = 1;  // 扩展单位位移量

    /**
     * 滑动阈值
     */
    private final int TOUCH_SLOP;
    /**
     * 惯性滑动最小、最大速度
     */
    private final int MIN_FLING_VELOCITY;
    private final int MAX_FLING_VELOCITY;

    /**
     * 背景色
     */
    private int bgColor;
    /**
     * 刻度颜色
     */
    private int gradationColor;
    /**
     * 短刻度线宽度
     */
    private float shortLineWidth;
    /**
     * 长刻度线宽度
     * 默认 = 2 * shortLineWidth
     */
    private float longLineWidth ;
    /**
     * 短刻度长度
     */
    private float shortGradationLen;
    /**
     * 长刻度长度
     * 默认为短刻度的2倍
     */
    private float longGradationLen;
    /**
     * 刻度字体颜色
     */
    private int textColor;
    /**
     * 刻度字体大小
     */
    private float textSize;
    /**
     * 中间指针线颜色
     */
    private int indicatorLineColor;
    /**
     * 中间指针线宽度
     */
    private float indicatorLineWidth;
    /**
     * 中间指针线长度
     */
    private float indicatorLineLen;
    /**
     * 最小值
     */
    private float minValue;
    /**
     * 最大值
     */
    private float maxValue;
    /**
     * 当前值
     */
    private float currentValue;
    /**
     * 刻度最小单位
     */
    private float gradationUnit;
    /**
     * 需要绘制的数值
     */
    private int numberPerCount;
    /**
     * 刻度间距离
     */
    private float gradationGap;
    /**
     * 刻度与文字的间距
     */
    private float gradationNumberGap;
    /**
     * 刻度条与刻度文字的间距
     */
    private float textGradationGap;

    /**
     * 最小数值，放大10倍：minValue * 10
     */
    private int mMinNumber;
    /**
     * 最大数值，放大10倍：maxValue * 10
     */
    private int mMaxNumber;
    /**
     * 当前数值
     */
    private int mCurrentNumber;
    /**
     * 最大数值与最小数值间的距离：(mMaxNumber - mMinNumber) / mNumberUnit * gradationGap
     */
    private float mNumberRangeDistance;
    /**
     * 刻度数值最小单位：gradationUnit * 10
     */
    private int mNumberUnit;
    /**
     * 当前数值与最小值的距离：(mCurrentNumber - minValue) / mNumberUnit * gradationGap
     */
    private float mCurrentDistance;
    /**
     * 控件宽度所占有的数值范围：mWidth / gradationGap * mNumberUnit
     */
    private int mWidthRangeNumber;

    /**
     * 普通画笔
     */
    private Paint mPaint;
    /**
     * 文字画笔
     */
    private TextPaint mTextPaint;
    /**
     * 滑动器
     */
    private Scroller mScroller;
    /**
     * 速度跟踪器
     */
    private VelocityTracker mVelocityTracker;
    /**
     * 尺寸
     */
    private int mWidth, mHalfWidth, mHeight;

    private int mDownX;
    private int mLastX, mLastY;
    private boolean isMoved;

    private OnValueChangedListener mValueChangedListener;

    /**
     * 长刻度颜色
     */
    private int longLineColor;

    /**
     * 短刻度颜色
     */
    private int shortLineColor;

    private boolean mUseCustomGradation = false;  // 是否使用自定义刻度显示
    private float mGradationSplitValue = 2.0f;   // 分割值，默认2.0x

    private Vibrator mVibrator;  // 震动管理器
    private int mLastVibrationValue;  // 上次震动时的刻度值

    /**
     * 当前值变化监听器
     */
    public interface OnValueChangedListener{
        void onValueChanged(float value);
    }

    /**
     * 刻度规则类，用于定义刻度的显示规则
     */
    public static class GradationRule {
        private float startValue;  // 起始值
        private float endValue;    // 结束值
        private float step;        // 步进值

        public GradationRule(float startValue, float endValue, float step) {
            this.startValue = startValue;
            this.endValue = endValue;
            this.step = step;
        }

        public float getStartValue() {
            return startValue;
        }

        public float getEndValue() {
            return endValue;
        }

        public float getStep() {
            return step;
        }
    }

    private List<GradationRule> mGradationRules = new ArrayList<>();

    /**
     * 设置刻度规则
     * @param rules 刻度规则列表
     */
    public void setGradationRules(List<GradationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("Rules list cannot be null or empty");
        }

        // 验证规则的有效性
        float lastEndValue = rules.get(0).startValue;
        for (GradationRule rule : rules) {
            if (rule.startValue > rule.endValue) {
                throw new IllegalArgumentException("Start value must be less than end value");
            }
            if (rule.step <= 0) {
                throw new IllegalArgumentException("Step must be greater than 0");
            }
            if (rule.startValue != lastEndValue) {
                throw new IllegalArgumentException("Rules must be continuous");
            }
            lastEndValue = rule.endValue;
        }

        // 更新控件的基本参数
        this.minValue = rules.get(0).startValue;
        this.maxValue = rules.get(rules.size() - 1).endValue;

        // 如果当前值超出新范围，重置为中间值
        if (this.currentValue < this.minValue || this.currentValue > this.maxValue) {
            this.currentValue = (this.minValue + this.maxValue) / 2;
        }

        // 找出最小步进值作为基本刻度单位
        float minStep = Float.MAX_VALUE;
        for (GradationRule rule : rules) {
            minStep = Math.min(minStep, rule.step);
        }
        // 使用最小步进值作为基本单位
        this.gradationUnit = minStep;

        mGradationRules.clear();
        mGradationRules.addAll(rules);

        // 如果控件尚未测量，先使用常规值设置
        if (mWidth <= 0) {
            setValue(minValue, maxValue, currentValue, gradationUnit, numberPerCount);
            return;
        }

        // 计算总刻度数（使用最小步进值）
        float totalScales = (maxValue - minValue) / minStep + 1;

        // 计算刻度间距（以像素为单位）
        float calculatedGapPx = mWidth / (totalScales - 1);
        this.gradationGap = calculatedGapPx;

        // 记录日志以便调试
        logD("setGradationRules: minValue=%f, maxValue=%f, totalScales=%f, mWidth=%d, calculatedGapPx=%f, minStep=%f",
                minValue, maxValue, totalScales, mWidth, calculatedGapPx, minStep);

        // 更新内部计算值
        convertValue2Number();

        if (mValueChangedListener != null) {
            mValueChangedListener.onValueChanged(currentValue);
        }

        postInvalidate();
    }

    /**
     * 根据当前值获取对应的刻度规则
     * @param value 当前值
     * @return 对应的刻度规则，如果没有找到则返回null
     */
    private GradationRule getGradationRuleForValue(float value) {
        for (GradationRule rule : mGradationRules) {
            if (value >= rule.startValue && value <= rule.endValue) {
                return rule;
            }
        }
        return null;
    }

    public RuleView2(Context context) {
        this(context, null);
    }

    public RuleView2(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RuleView2(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttrs(context, attrs);

        // 初始化final常量，必须在构造中赋初值
        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        TOUCH_SLOP = viewConfiguration.getScaledTouchSlop();
        MIN_FLING_VELOCITY = viewConfiguration.getScaledMinimumFlingVelocity();
        MAX_FLING_VELOCITY = viewConfiguration.getScaledMaximumFlingVelocity();

        // 初始化震动管理器
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mLastVibrationValue = mCurrentNumber;

        convertValue2Number();
        init(context);
    }

    private void initAttrs(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.RuleView);
        bgColor = ta.getColor(R.styleable.RuleView_zjun_bgColor, Color.parseColor(DEFAULT_BG_COLOR));
        gradationColor = ta.getColor(R.styleable.RuleView_zjun_gradationColor, Color.LTGRAY);
        shortLineWidth = ta.getDimension(R.styleable.RuleView_gv_shortLineWidth, dp2px(DEFAULT_SHORT_LINE_WIDTH_DP));
        shortGradationLen = ta.getDimension(R.styleable.RuleView_gv_shortGradationLen, dp2px(DEFAULT_SHORT_GRADATION_LEN_DP));
        longGradationLen = ta.getDimension(R.styleable.RuleView_gv_longGradationLen, shortGradationLen * 2);
        longLineWidth = ta.getDimension(R.styleable.RuleView_gv_longLineWidth, shortLineWidth * 2);
        longLineColor = ta.getColor(R.styleable.RuleView_gv_longLineColor, gradationColor);
        shortLineColor = ta.getColor(R.styleable.RuleView_gv_shortLineColor, gradationColor);
        textColor = ta.getColor(R.styleable.RuleView_zjun_textColor, Color.BLACK);
        textSize = ta.getDimension(R.styleable.RuleView_zjun_textSize, sp2px(DEFAULT_TEXT_SIZE_SP));
        indicatorLineColor = ta.getColor(R.styleable.RuleView_zjun_indicatorLineColor, Color.parseColor(DEFAULT_INDICATOR_COLOR));
        indicatorLineWidth = ta.getDimension(R.styleable.RuleView_zjun_indicatorLineWidth, dp2px(DEFAULT_INDICATOR_LINE_WIDTH_DP));
        indicatorLineLen = ta.getDimension(R.styleable.RuleView_gv_indicatorLineLen, dp2px(DEFAULT_INDICATOR_LINE_LEN_DP));
        minValue = ta.getFloat(R.styleable.RuleView_gv_minValue, DEFAULT_MIN_VALUE);
        maxValue = ta.getFloat(R.styleable.RuleView_gv_maxValue, DEFAULT_MAX_VALUE);
        currentValue = ta.getFloat(R.styleable.RuleView_gv_currentValue, DEFAULT_CURRENT_VALUE);
        gradationUnit = ta.getFloat(R.styleable.RuleView_gv_gradationUnit, DEFAULT_GRADATION_UNIT);
        numberPerCount = ta.getInt(R.styleable.RuleView_gv_numberPerCount, DEFAULT_NUMBER_PER_COUNT);
        gradationGap = ta.getDimension(R.styleable.RuleView_gv_gradationGap, dp2px(DEFAULT_GRADATION_GAP_DP));
        gradationNumberGap = ta.getDimension(R.styleable.RuleView_gv_gradationNumberGap, dp2px(DEFAULT_GRADATION_NUMBER_GAP_DP));
        textGradationGap = ta.getDimension(R.styleable.RuleView_gv_textGradationGap, dp2px(DEFAULT_TEXT_GRADATION_GAP_DP));
        ta.recycle();
    }

    /**
     * 初始化
     */
    private void init(Context context) {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStrokeWidth(shortLineWidth);

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextSize(textSize);
        mTextPaint.setColor(textColor);

        mScroller = new Scroller(context);
    }

    /**
     * 把真实数值转换成绘制数值
     * 为了防止float的精度丢失，把minValue、maxValue、currentValue、gradationUnit都放大10倍
     */
    private void convertValue2Number() {
        mMinNumber = (int) (minValue * 10);
        mMaxNumber = (int) (maxValue * 10);
        mCurrentNumber = (int) (currentValue * 10);
        mNumberUnit = (int) (gradationUnit * 10);
        mCurrentDistance = (mCurrentNumber - mMinNumber) / mNumberUnit * gradationGap;
        mNumberRangeDistance = (mMaxNumber - mMinNumber) / mNumberUnit * gradationGap;
        if (mWidth != 0) {
            // 初始化时，在onMeasure()里计算
            mWidthRangeNumber = (int) (mWidth / gradationGap * mNumberUnit);
        }
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mWidth = calculateSize(true, widthMeasureSpec);
        mHeight = calculateSize(false, heightMeasureSpec);
        // 考虑padding，计算实际可用宽度
        mWidth = mWidth - getPaddingLeft() - getPaddingRight();
        mHeight = mHeight - getPaddingTop() - getPaddingBottom();
        mHalfWidth = mWidth >> 1;
        if (mWidthRangeNumber == 0) {
            mWidthRangeNumber = (int) (mWidth / gradationGap * mNumberUnit);
        }
        // 设置测量尺寸时需要加上padding
        setMeasuredDimension(mWidth + getPaddingLeft() + getPaddingRight(),
                           mHeight + getPaddingTop() + getPaddingBottom());
    }

    /**
     * 计算宽度或高度的真实大小
     *
     * 宽或高为wrap_content时，父控件的测量模式无论是EXACTLY还是AT_MOST，默认给的测量模式都是AT_MOST，测量大小为父控件的size
     * 所以，我们宽度不管，只处理高度，默认80dp
     * @see ViewGroup#getChildMeasureSpec(int, int, int)
     *
     * @param isWidth 是不是宽度
     * @param spec    测量规则
     * @return 真实的大小
     */
    private int calculateSize(boolean isWidth, int spec) {
        final int mode = MeasureSpec.getMode(spec);
        final int size = MeasureSpec.getSize(spec);

        int realSize = size;
        switch (mode) {
            // 精确模式：已经确定具体数值：layout_width为具体值，或match_parent
            case MeasureSpec.EXACTLY:
                break;
            // 最大模式：最大不能超过父控件给的widthSize：layout_width为wrap_content
            case MeasureSpec.AT_MOST:
                if (!isWidth) {
                    int defaultContentSize = dp2px(90);
                    realSize = Math.min(realSize, defaultContentSize);
                }
                break;
            // 未指定尺寸模式：一般父控件是AdapterView
            case MeasureSpec.UNSPECIFIED:
            default:

        }
        logD("isWidth=%b, mode=%d, size=%d, realSize=%d", isWidth, mode, size, realSize);
        return realSize;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished(true);
                mDownX = x;
                isMoved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                final int dx = x - mLastX;

                // 判断是否已经滑动
                if (!isMoved) {
                    final int dy = y - mLastY;
                    // 滑动的触发条件：水平滑动大于垂直滑动；滑动距离大于阈值
                    if (Math.abs(dx) < Math.abs(dy) || Math.abs(x - mDownX) < TOUCH_SLOP) {
                        break;
                    }
                    isMoved = true;
                }

                mCurrentDistance += -dx;
                calculateValue();
                break;
            case MotionEvent.ACTION_UP:
                if (!isMoved) {
                    // 这是一个点击事件
                    float clickDistance = x - getPaddingLeft();
                    float centerDistance = mHalfWidth;
                    float offset = clickDistance - centerDistance;

                    // 计算点击位置对应的值
                    float targetDistance = mCurrentDistance + offset;
                    // 确保在有效范围内
                    targetDistance = Math.min(Math.max(targetDistance, 0), mNumberRangeDistance);

                    // 计算目标刻度值
                    float exactPosition = targetDistance / gradationGap;
                    int targetNumber = mMinNumber + Math.round(exactPosition) * mNumberUnit;

                    // 在0-2x范围内确保只能停在偶数刻度上
                    if (mUseCustomGradation && targetNumber <= (mGradationSplitValue * 10)) {
                        // 计算最近的偶数刻度
                        int remainder = targetNumber % (mNumberUnit * 2);
                        if (remainder != 0) {
                            // 计算与前后两个偶数刻度的距离
                            int prevEven = targetNumber - remainder;
                            int nextEven = prevEven + (mNumberUnit * 2);

                            // 使用实际点击位置来决定目标值
                            float clickedValue = targetNumber / 10f;
                            float prevValue = prevEven / 10f;
                            float nextValue = nextEven / 10f;

                            // 根据点击位置选择更近的偶数刻度
                            targetNumber = Math.abs(clickedValue - prevValue) <= Math.abs(clickedValue - nextValue) ?
                                         prevEven : nextEven;
                        }
                    }

                    // 确保目标值在范围内
                    targetNumber = Math.min(Math.max(targetNumber, mMinNumber), mMaxNumber);

                    // 计算最终目标距离
                    float targetDistance2 = (targetNumber - mMinNumber) / (float)mNumberUnit * gradationGap;

                    // 计算滚动距离和时间
                    int dx2 = (int)(targetDistance2 - mCurrentDistance);
                    int duration = Math.min(Math.abs(dx2) * 2, 800); // 最大800ms

                    // 开始滚动动画
                    mScroller.startScroll((int)mCurrentDistance, 0, dx2, 0, duration);
                    invalidate();
                    break;
                }

                // 处理滑动结束 - 优化惯性滑动体验
                mVelocityTracker.computeCurrentVelocity(1000, MAX_FLING_VELOCITY);
                int xVelocity = (int) mVelocityTracker.getXVelocity();
                
                if (Math.abs(xVelocity) >= MIN_FLING_VELOCITY) {
                    // 计算摩擦系数，使滑动更丝滑
                    float friction = getContext().getResources().getDisplayMetrics().density * 0.84f;
                    if (Math.abs(xVelocity) > MIN_FLING_VELOCITY * 8) {
                        // 高速滑动时稍微减小摩擦，让滑动更远更自然
                        friction *= 0.7f;
                    }
                    
                    // 设置Scroller的摩擦系数
                    mScroller.setFriction(friction);
                    
                    // 使用fling启动惯性滑动
                    mScroller.fling(
                        (int)mCurrentDistance,  // startX
                        0,                      // startY
                        -xVelocity,             // velocityX
                        0,                      // velocityY
                        0,                      // minX
                        (int)mNumberRangeDistance, // maxX
                        0,                      // minY
                        0                       // maxY
                    );
                    invalidate();
                } else {
                    // 速度低时，直接滚动到最近刻度
                    scrollToGradation();
                }
                
                // 释放速度跟踪器
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
                
            case MotionEvent.ACTION_CANCEL:
                // 释放速度跟踪器
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
                
            default:
                break;
        }
        mLastX = x;
        mLastY = y;
        return true;
    }

    /**
     * 检查是否需要震动
     */
    private void checkVibration(int oldNumber, int newNumber) {
        // 确保震动器可用
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            return;
        }

        // 获取当前的移动速度
        float velocity = 0;
        if (mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity(1000);
            velocity = Math.abs(mVelocityTracker.getXVelocity());
        }

        // 只有在非常慢速滑动时才震动，避免快速滑动时的频繁震动导致卡顿感
        if (velocity < MIN_FLING_VELOCITY * 0.5f) {
            // 在0-2x范围内只在偶数刻度处震动
            if (mUseCustomGradation && newNumber <= (mGradationSplitValue * NUMBER_MAGNIFICATION)) {
                if (newNumber % (mNumberUnit * 2) == 0 && oldNumber != newNumber &&
                    Math.abs(newNumber - mLastVibrationValue) >= mNumberUnit * 2) {
                    mVibrator.vibrate(VIBRATION_DURATION);
                    mLastVibrationValue = newNumber;
                }
            } else {
                // 2x以上范围，只在整数刻度处震动，更适度地提供反馈
                if (newNumber % 10 == 0 && oldNumber != newNumber &&
                    Math.abs(newNumber - mLastVibrationValue) >= 10) {
                    mVibrator.vibrate(VIBRATION_DURATION);
                    mLastVibrationValue = newNumber;
                }
            }
        }
    }

    /**
     * 根据distance距离，计算数值
     */
    private void calculateValue() {
        // 限定范围：在最小值与最大值之间
        mCurrentDistance = Math.min(Math.max(mCurrentDistance, 0), mNumberRangeDistance);
        int oldNumber = mCurrentNumber;

        // 使用精确的位置，避免整数舍入造成的跳动
        float exactPosition = mCurrentDistance / gradationGap;
        mCurrentNumber = mMinNumber + Math.round(exactPosition * mNumberUnit);
        float oldValue = currentValue;
        currentValue = mCurrentNumber / 10f;

        // 获取当前值对应的规则
        GradationRule rule = getGradationRuleForValue(currentValue);
        if (rule != null) {
            // 判断当前值是否是规则定义的刻度值
            float stepMultiple = currentValue / rule.step;
            boolean isValidScale = Math.abs(Math.round(stepMultiple) - stepMultiple) < 0.001f;

            // 检查是否需要震动，但仅在有效刻度且速度极慢时
            if (isValidScale) {
                // 获取当前的移动速度
                float velocity = 0;
                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    velocity = Math.abs(mVelocityTracker.getXVelocity());
                }
                
                // 只有在非常慢速滑动时才触发震动，提高速度阈值，使高速滑动时更加丝滑
                if (velocity < MIN_FLING_VELOCITY * 0.3f) {
                    checkVibration(oldNumber, mCurrentNumber);
                }

                // 只有在是有效刻度值且值发生变化时才触发回调
                if (Math.abs(oldValue - currentValue) > 0.001f) {
                    if (mValueChangedListener != null) {
                        mValueChangedListener.onValueChanged(currentValue);
                    }
                }
            }
        }

        invalidate();
    }

    /**
     * 滑动到最近的刻度线上
     */
    private void scrollToGradation() {
        // 先计算当前实际位置对应的数值
        float exactPosition = mCurrentDistance / gradationGap;
        float tempNumber = mMinNumber + exactPosition * mNumberUnit;
        float tempValue = tempNumber / 10f;

        // 获取当前的移动速度
        float velocity = 0;
        if (mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity(1000);
            velocity = Math.abs(mVelocityTracker.getXVelocity());
        }

        // 获取当前值对应的规则
        GradationRule rule = getGradationRuleForValue(tempValue);
        if (rule != null) {
            float targetValue; // 目标值
            
            // 处理0-2x范围内的特殊规则（只停在偶数刻度）
            if (mUseCustomGradation && tempValue <= mGradationSplitValue) {
                // 计算当前值最接近的刻度值
                float stepMultiple = tempValue / rule.step;
                float roundedMultiple = Math.round(stepMultiple);
                float nearestScaleValue = roundedMultiple * rule.step;
                
                // 计算最近的偶数刻度
                float remainder = nearestScaleValue % (0.2f * 2);
                if (Math.abs(remainder) > 0.001f) {
                    // 找到前后两个偶数刻度
                    float prevEven = nearestScaleValue - remainder;
                    float nextEven = prevEven + (0.2f * 2);
                    
                    // 选择最近的偶数刻度
                    targetValue = (Math.abs(tempValue - prevEven) <= Math.abs(tempValue - nextEven)) ? 
                                prevEven : nextEven;
                } else {
                    targetValue = nearestScaleValue;
                }
            } else {
                // 非0-2x范围的处理
                // 计算最接近的刻度值
                float stepMultiple = tempValue / rule.step;
                float roundedMultiple = Math.round(stepMultiple);
                targetValue = roundedMultiple * rule.step;
            }
            
            // 确保在有效范围内
            targetValue = Math.min(Math.max(targetValue, minValue), maxValue);
            
            // 计算目标整数值
            int targetNumber = (int)(targetValue * 10);
            
            // 如果目标值与当前值不同，使用平滑滚动过渡
            if (Math.abs(targetNumber - mCurrentNumber) > 0.001f) {
                float targetDistance = (targetNumber - mMinNumber) / (float)mNumberUnit * gradationGap;
                float distanceToTarget = Math.abs(targetDistance - mCurrentDistance);
                
                // 计算动画持续时间，速度越快，持续时间越短
                int duration;
                if (velocity > MIN_FLING_VELOCITY) {
                    // 高速滚动后的对齐，使用较短时间
                    duration = Math.min(150, (int)(distanceToTarget * 0.5f));
                } else {
                    // 低速或手动拖动后的对齐，使用适中时间
                    duration = Math.min(300, (int)(distanceToTarget * 1.2f));
                }
                duration = Math.max(100, duration); // 确保最小动画时间
                
                // 启动平滑滚动
                mScroller.startScroll(
                    (int)mCurrentDistance,
                    0,
                    (int)(targetDistance - mCurrentDistance),
                    duration
                );
                invalidate();
                return;
            }
        }
        
        // 如果没有找到对应规则或不需要滚动，直接更新值
        mCurrentNumber = Math.min(Math.max(Math.round(tempNumber), mMinNumber), mMaxNumber);
        float oldValue = currentValue;
        currentValue = mCurrentNumber / 10f;
        mCurrentDistance = (mCurrentNumber - mMinNumber) / (float)mNumberUnit * gradationGap;
        
        // 只有在值发生变化时才触发回调
        if (Math.abs(oldValue - currentValue) > 0.001f) {
            if (mValueChangedListener != null) {
                mValueChangedListener.onValueChanged(currentValue);
            }
        }
        
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            if (mScroller.getCurrX() != mScroller.getFinalX()) {
                mCurrentDistance = mScroller.getCurrX();
                
                // 计算当前值
                float exactPosition = mCurrentDistance / gradationGap;
                int tempNumber = mMinNumber + (int)(exactPosition) * mNumberUnit;
                float tempValue = tempNumber / 10f;
                
                // 获取当前值对应的规则
                GradationRule rule = getGradationRuleForValue(tempValue);
                if (rule != null && mUseCustomGradation && tempNumber <= (mGradationSplitValue * 10)) {
                    // 计算最近的偶数刻度
                    int remainder = tempNumber % (mNumberUnit * 2);
                    if (remainder != 0) {
                        // 计算与前后两个偶数刻度的距离
                        int prevEven = tempNumber - remainder;
                        int nextEven = prevEven + (mNumberUnit * 2);
                        
                        // 计算实际位置与前后偶数刻度的距离
                        float currentPos = exactPosition * mNumberUnit;
                        float prevPos = prevEven;
                        float nextPos = nextEven;
                        
                        // 获取当前滚动速度
                        float velocity = Math.abs(mScroller.getCurrVelocity());
                        float minVelocity = MIN_FLING_VELOCITY * VELOCITY_THRESHOLD_FACTOR;
                        
                        // 如果速度较低，开始考虑对齐到偶数刻度
                        if (velocity < minVelocity) {
                            // 根据实际距离选择目标刻度
                            int targetNumber = (Math.abs(currentPos - prevPos) <= Math.abs(currentPos - nextPos)) ? 
                                             prevEven : nextEven;
                            
                            // 计算新的目标距离
                            float targetDistance = (targetNumber - mMinNumber) / mNumberUnit * gradationGap;
                            
                            // 如果已经接近终点，平滑过渡到目标刻度
                            if (Math.abs(mScroller.getFinalX() - mScroller.getCurrX()) < gradationGap * GRADATION_CHECK_RANGE) {
                                mScroller.forceFinished(true);
                                mScroller.startScroll(
                                    (int)mCurrentDistance,
                                    0,
                                    (int)(targetDistance - mCurrentDistance),
                                    ALIGN_ANIMATION_DURATION
                                );
                                invalidate();
                                return;
                            }
                        }
                    }
                } else if (rule != null) {
                    // 非0-2x范围或不使用自定义刻度显示模式时的处理
                    // 计算最接近的刻度值
                    float stepMultiple = tempValue / rule.step;
                    float roundedMultiple = Math.round(stepMultiple);
                    float nearestScaleValue = roundedMultiple * rule.step;
                    
                    // 计算目标距离
                    float targetDistance = (nearestScaleValue * 10 - mMinNumber) / mNumberUnit * gradationGap;
                    
                    // 获取当前滚动速度
                    float velocity = Math.abs(mScroller.getCurrVelocity());
                    float minVelocity = MIN_FLING_VELOCITY * VELOCITY_THRESHOLD_FACTOR;
                    
                    // 如果速度较低且接近终点，平滑过渡到目标刻度
                    if (velocity < minVelocity && 
                        Math.abs(mScroller.getFinalX() - mScroller.getCurrX()) < gradationGap * GRADATION_CHECK_RANGE) {
                        
                        mScroller.forceFinished(true);
                        mScroller.startScroll(
                            (int)mCurrentDistance,
                            0,
                            (int)(targetDistance - mCurrentDistance),
                            ALIGN_ANIMATION_DURATION
                        );
                        invalidate();
                        return;
                    }
                }
                
                calculateValue();
            } else {
                scrollToGradation();
            }
            
            invalidate();  // 确保继续刷新
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 1 绘制背景色
        canvas.drawColor(bgColor);
        // 2 绘制刻度、数字
        drawGradation(canvas);
        // 3 绘制指针
        drawIndicator(canvas);
    }

    /**
     * 绘制刻度
     */
    private void drawGradation(Canvas canvas) {
        // 保存画布状态
        canvas.save();
        // 平移画布，处理padding
        canvas.translate(getPaddingLeft(), getPaddingTop());

        // 计算文字基线位置：从顶部开始，先绘制文字，再绘制刻度
        float textBaseline = textSize + gradationNumberGap;

        // 计算当前值对应的规则
        GradationRule currentRule = getGradationRuleForValue(currentValue);
        if (currentRule == null) {
            canvas.restore();
            return;
        }

        // 计算开始绘制的刻度值
        float startValue = currentValue - (mHalfWidth / gradationGap) * gradationUnit;
        startValue = Math.max(startValue, minValue);

        // 计算结束绘制的刻度值
        float endValue = currentValue + (mHalfWidth / gradationGap) * gradationUnit;
        endValue = Math.min(endValue, maxValue);

        // 从起始值开始绘制刻度
        float currentDrawValue = startValue;
        while (currentDrawValue <= endValue) {
            // 获取当前值对应的规则
            GradationRule rule = getGradationRuleForValue(currentDrawValue);
            if (rule == null) {
                currentDrawValue += gradationUnit;
                continue;
            }

            // 计算当前刻度的x坐标
            float distance = mHalfWidth - ((currentValue - currentDrawValue) / gradationUnit * gradationGap);

            // 判断是否应该绘制这个刻度
            // 如果当前值是规则步进值的整数倍，则绘制
            float stepMultiple = currentDrawValue / rule.step;
            boolean shouldDrawScale = Math.abs(Math.round(stepMultiple) - stepMultiple) < 0.001f;

            if (shouldDrawScale) {
                // 判断是否是长刻度（整数值）
                boolean isLongScale = Math.abs(Math.round(currentDrawValue) - currentDrawValue) < 0.001f;

                if (isLongScale) {
                    // 绘制长刻度
                    mPaint.setColor(longLineColor);
                    mPaint.setStrokeWidth(longLineWidth);
                    canvas.drawLine(distance, textBaseline + textGradationGap,
                                  distance, textBaseline + textGradationGap + longGradationLen, mPaint);

                    // 绘制刻度值
                    String text = String.format("%.1fx", currentDrawValue);
                    if (text.endsWith(".0x")) {
                        text = text.substring(0, text.length() - 3) + "x";
                    }

                    float textWidth = mTextPaint.measureText(text);
                    canvas.drawText(text, distance - textWidth * 0.5f, textBaseline, mTextPaint);
                } else {
                    // 绘制短刻度
                    mPaint.setColor(shortLineColor);
                    mPaint.setStrokeWidth(shortLineWidth);
                    canvas.drawLine(distance, textBaseline + textGradationGap,
                                  distance, textBaseline + textGradationGap + shortGradationLen, mPaint);
                }
            }

            currentDrawValue += gradationUnit;
        }

        // 恢复画布状态
        canvas.restore();
    }

    /**
     * 绘制指针
     */
    private void drawIndicator(Canvas canvas) {
        // 保存画布状态
        canvas.save();
        // 平移画布，处理padding
        canvas.translate(getPaddingLeft(), getPaddingTop());
        
        // 计算指示器的起始位置，与刻度底部对齐，使用新的间距值
        float startY = textSize + gradationNumberGap + textGradationGap + longGradationLen - indicatorLineLen;
        
        mPaint.setColor(indicatorLineColor);
        mPaint.setStrokeWidth(indicatorLineWidth);
        // 圆头画笔
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        // 绘制指示器，从计算的位置开始，长度为indicatorLineLen
        canvas.drawLine(mHalfWidth, startY, mHalfWidth, startY + indicatorLineLen, mPaint);
        // 默认形状画笔
        mPaint.setStrokeCap(Paint.Cap.BUTT);
        
        // 恢复画布状态
        canvas.restore();
    }

    private int dp2px(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private int sp2px(float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    @SuppressWarnings("all")
    private void logD(String format, Object... args) {
        if (LOG_ENABLE) {
            Log.d("GradationView", String.format("zjun@" + format, args));
        }
    }

    /**
     * 设置新值
     */
    public void setCurrentValue(float currentValue) {
        if (currentValue < minValue || currentValue > maxValue) {
            throw new IllegalArgumentException(String.format("The currentValue of %f is out of range: [%f, %f]",
                    currentValue, minValue, maxValue));
        }
        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
        }
        this.currentValue = currentValue;
        mCurrentNumber = (int) (this.currentValue * 10);
        final float newDistance = (mCurrentNumber - mMinNumber) / mNumberUnit * gradationGap;
        final int dx = (int) (newDistance - mCurrentDistance);
        // 最大2000ms
        final int duration = dx * MAX_VALUE_CHANGE_DURATION / (int)mNumberRangeDistance;
        // 滑动到目标值
        mScroller.startScroll((int) mCurrentDistance, 0, dx, duration);
        postInvalidate();
    }

    public float getMinValue() {
        return minValue;
    }

    public float getMaxValue() {
        return maxValue;
    }   

    /**
     * 获取当前值
     */
    public float getCurrentValue() {
        return this.currentValue;
    }

    /**
     * 重新配置参数
     *
     * @param minValue  最小值
     * @param maxValue  最大值
     * @param curValue  当前值
     * @param unit      最小单位所代表的值
     * @param perCount  相邻两条长刻度线之间被分成的隔数量
     */
    public void setValue(float minValue, float maxValue, float curValue, float unit, int perCount) {
        if (minValue > maxValue || curValue < minValue || curValue > maxValue) {
            throw new IllegalArgumentException(String.format("The given values are invalid, check firstly: " +
                    "minValue=%f, maxValue=%f, curValue=%s", minValue, maxValue, curValue));
        }
        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = curValue;
        this.gradationUnit = unit;
        this.numberPerCount = perCount;
        convertValue2Number();
        if (mValueChangedListener != null) {
            mValueChangedListener.onValueChanged(currentValue);
        }
        postInvalidate();
    }

    public void setOnValueChangedListener(OnValueChangedListener listener) {
        this.mValueChangedListener = listener;
    }

    /**
     * 根据控件宽度自动计算刻度间距
     * 
     * @param minValue  最小值
     * @param maxValue  最大值
     * @param curValue  当前值
     * @param unit      最小单位所代表的值
     * @param perCount  相邻两条长刻度线之间被分成的隔数量
     */
    public void setAutoGap(float minValue, float maxValue, float curValue, float unit, int perCount) {
        if (minValue > maxValue || curValue < minValue || curValue > maxValue) {
            throw new IllegalArgumentException(String.format("The given values are invalid, check firstly: " +
                    "minValue=%f, maxValue=%f, curValue=%s", minValue, maxValue, curValue));
        }
        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
        }
        
        // 如果控件尚未测量，先使用常规值设置
        if (mWidth <= 0) {
            setValue(minValue, maxValue, curValue, unit, perCount);
            return;
        }
        
        // 计算刻度线总数
        int totalScaleCount = (int)((maxValue - minValue) / unit) + 1;
        
        // 计算刻度间距（以像素为单位）
        float calculatedGapPx = (float) mWidth / (totalScaleCount - 1);
        
        // 重要修改：直接使用像素值，不再转换为dp
        // 以前的错误：this.gradationGap = calculatedGap / getResources().getDisplayMetrics().density;
        this.gradationGap = calculatedGapPx;
        
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = curValue;
        this.gradationUnit = unit;
        this.numberPerCount = perCount;
        
        // 记录日志以便调试
        logD("setAutoGap: minValue=%f, maxValue=%f, totalScaleCount=%d, mWidth=%d, calculatedGapPx=%f",
                minValue, maxValue, totalScaleCount, mWidth, calculatedGapPx);
        
        // 更新内部计算值
        convertValue2Number();
        
        if (mValueChangedListener != null) {
            mValueChangedListener.onValueChanged(currentValue);
        }
        
        postInvalidate();
    }

    /**
     * 设置刻度显示模式
     * @param useCustomGradation 是否使用自定义刻度显示（0-2x显示偶数刻度，2-4x显示所有刻度）
     */
    public void setCustomGradationMode(boolean useCustomGradation) {
        this.mUseCustomGradation = useCustomGradation;
        postInvalidate();
    }

    /**
     * 设置刻度分割值
     * @param splitValue 分割值，小于此值的区间显示偶数刻度，大于此值的区间显示所有刻度
     */
    public void setGradationSplitValue(float splitValue) {
        if (splitValue < minValue || splitValue > maxValue) {
            throw new IllegalArgumentException(String.format("The splitValue of %f is out of range: [%f, %f]",
                    splitValue, minValue, maxValue));
        }
        this.mGradationSplitValue = splitValue;
        postInvalidate();
    }
}
