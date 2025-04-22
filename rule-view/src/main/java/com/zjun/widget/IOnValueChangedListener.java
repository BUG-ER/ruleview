package com.zjun.widget;

/**
 * 当前值变化监听器接口
 * 从各个RuleView控件中提取出来作为共享接口，避免类名冲突
 */
public interface IOnValueChangedListener {
    void onValueChanged(float value);
} 