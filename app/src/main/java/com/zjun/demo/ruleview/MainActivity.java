package com.zjun.demo.ruleview;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.zjun.widget.RuleView;

public class MainActivity extends AppCompatActivity {

    private TextView tvValue;
    private RuleView gvRule;
    private TextView tvRuleIndicator;
    private LinearLayout llRuleSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvValue = findViewById(R.id.tv_value);
        gvRule = findViewById(R.id.gv_1);
        tvRuleIndicator = findViewById(R.id.tv_rule_indicator);
        llRuleSettings = findViewById(R.id.ll_rule_settings);
        
        // 先使用常规方法初始化刻度尺（此时尚未获得控件宽度）
        gvRule.setValue(0, 10, 2, 0.1f, 10);

        // 等待布局完成后获取实际宽度，再计算刻度间距
//        gvRule.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//                // 只需执行一次
//                gvRule.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//
//                // 使用自动计算刻度间距的方法设置刻度尺
//                gvRule.setAutoGap(0, 4, 2, 0.1f, 10);
//            }
//        });
        
        tvValue.setText(Float.toString(gvRule.getCurrentValue()));
        gvRule.setOnValueChangedListener(new RuleView.OnValueChangedListener() {
            @Override
            public void onValueChanged(float value) {
                tvValue.setText(String.format("%.1fx", value));
            }
        });

        // 启用自定义刻度显示模式
        gvRule.setCustomGradationMode(true);
        // 设置分割值（在此值之前显示偶数刻度，之后显示所有刻度）
        gvRule.setGradationSplitValue(2.0f);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_rule_indicator:
                toggleSettingsShow(R.id.ll_rule_settings);
                break;
            case R.id.btn_50:
                gvRule.setCurrentValue(2);
                break;
            case R.id.btn_change:
                toggleValue();
                break;
            default: break;
        }
    }

    private void toggleSettingsShow(@IdRes int layoutId) {
        LinearLayout llSettings = findViewById(layoutId);
        llSettings.setVisibility(llSettings.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
    }

    int i = 0;
    private void toggleValue() {
        i++;
        gvRule.setCurrentValue((i % 2 == 0) ? 1.4f: 2.5f);
    }
}
