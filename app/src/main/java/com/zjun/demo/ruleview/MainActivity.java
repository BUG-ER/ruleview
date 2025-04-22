package com.zjun.demo.ruleview;

import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zjun.widget.IOnScrollStopListener;
import com.zjun.widget.IOnValueChangedListener;
import com.zjun.widget.RuleView;
import com.zjun.widget.SpecialGradationRule;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvGvValue;
    private RuleView gvRule;
    private TextView tvRuleIndicator;
    private LinearLayout llRuleSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvGvValue = findViewById(R.id.tv_value);
        gvRule = findViewById(R.id.gv_1);
        tvRuleIndicator = findViewById(R.id.tv_rule_indicator);
        llRuleSettings = findViewById(R.id.ll_rule_settings);
        
        // 使用新的设置不同区间刻度间隔的方法
        setRule();

        gvRule.setOnValueChangedListener(new IOnValueChangedListener() {
            @Override
            public void onValueChanged(float value) {
                String str = String.format("%.1f", value);
                if (str.endsWith(".0")) {
                    str = str.substring(0, str.length() - 2);
                }
                tvGvValue.setText(str + "x");
            }
        });
        gvRule.setOnScrollStopListener(new IOnScrollStopListener() {
            @Override
            public void onScrollStop(float value, @NotNull String label) {

            }
        });
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
        gvRule.setCurrentValue((i % 2 == 0) ? 0.4f: 0.8f);
    }

    private void setRule() {
        // 设置初始值和规则
        // gvRule.setValue(0, 4, 2, 0.1f, 10);
        List<SpecialGradationRule> specialGradationRules = Arrays.asList(
                new SpecialGradationRule(0.1f, true),  // 0.1x 将显示为长刻度并显示文本
                new SpecialGradationRule(0.5f, true)   // 0.5x 将显示为长刻度并显示文本
        );
        gvRule.setSpecialGradations(specialGradationRules);

        // 创建刻度间隔规则列表
        List<RuleView.GradationGapRule> rules = new ArrayList<>();
        
        // 规则1: 0到1.0之间刻度间隔为40px
        rules.add(new RuleView.GradationGapRule(0.1f, 1.0f, 100.0f));
        
        // 规则2: 1.0到4.0之间刻度间隔为20px
        rules.add(new RuleView.GradationGapRule(1.0f, 10.0f, 20.0f));
        
        // 设置刻度间隔规则
        gvRule.setGradationGapRules(rules, 5.0f);


        
        // 以下自定义刻度显示模式的设置已移除
        // gvRule.setCustomGradationMode(true);
        // gvRule.setGradationSplitValue(2.0f);
    }
}
