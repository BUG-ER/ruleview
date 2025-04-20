package com.zjun.demo.ruleview;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zjun.demo.ruleview.R;
import com.zjun.widget.RuleView1;

import java.util.ArrayList;
import java.util.List;

public class MainActivity1 extends AppCompatActivity {

    private TextView tvValue;
    private RuleView1 gvRule;
    private TextView tvRuleIndicator;
    private LinearLayout llRuleSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main1);

        tvValue = findViewById(R.id.tv_value);
        gvRule = findViewById(R.id.gv_1);
        tvRuleIndicator = findViewById(R.id.tv_rule_indicator);
        llRuleSettings = findViewById(R.id.ll_rule_settings);
        
        // 先使用常规方法初始化刻度尺（此时尚未获得控件宽度）
//        gvRule.setValue(0, 4, 2, 0.1f, 10);

        // 等待布局完成后获取实际宽度，再计算刻度间距
        gvRule.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // 只需执行一次
                gvRule.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // 创建刻度规则列表
                List<RuleView1.GradationRule> rules = new ArrayList<>();

                // 添加规则：0-2x 范围内每 0.2 显示一个刻度
                rules.add(new RuleView1.GradationRule(0f, 2.0f, 0.2f));

                // 添加规则：2-4x 范围内每 0.1 显示一个刻度
                rules.add(new RuleView1.GradationRule(2.0f, 4.0f, 0.1f));

                // 设置规则
                gvRule.setGradationRules(rules);

                // 设置基本参数
//                gvRule.setAutoGap(0, 4, 2, 0.1f, 10);
            }
        });
        
        tvValue.setText(Float.toString(gvRule.getCurrentValue()));
        gvRule.setOnValueChangedListener(new RuleView1.OnValueChangedListener() {
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
//                toggleValue();
                break;
            default: break;
        }
    }

    private void toggleSettingsShow(@IdRes int layoutId) {
        LinearLayout llSettings = findViewById(layoutId);
        llSettings.setVisibility(llSettings.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
    }

    private void toggleValue() {
        if (gvRule.getMinValue() == 0) {
            // 切换到1-3范围，使用自动计算刻度间距
            gvRule.setAutoGap(1, 3, 2, 0.1f, 10);
        } else {
            // 切换回0-4范围，使用自动计算刻度间距
            gvRule.setAutoGap(0, 4, 2, 0.1f, 10);
        }
    }
}
