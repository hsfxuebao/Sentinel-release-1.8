package com.sentinel.demo;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

/**
 * 限流-单机
 */
public class MainClass {


    public static void main(String[] args) {
        initFlowRules();
        initAuthRules();
        while (true) {
            try {
                ContextUtil.enter("default", "ip");
                Entry entry = SphU.entry("A");
                System.out.println("A pass!");
                entry.exit();
                ContextUtil.exit();
            } catch (BlockException ex) {
                // 处理被流控的逻辑
                System.out.println("A blocked!");
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                Entry entry = SphU.entry("B");
                System.out.println("B pass!");
                entry.exit();
            } catch (BlockException ex) {
                // 处理被流控的逻辑
                System.out.println("B blocked!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    private static void initFlowRules(){
        List<FlowRule> rules = new ArrayList<FlowRule>();
        FlowRule rule = new FlowRule();
        rule.setResource("A");
        rule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        // Set limit QPS to 20.
        rule.setCount(1);
        rules.add(rule);

        FlowRule rul2 = new FlowRule();
        rule.setResource("B");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // Set limit QPS to 20.
        rule.setCount(10);
        rules.add(rul2);
        FlowRuleManager.loadRules(rules);


    }

    private static void initAuthRules(){
        List<AuthorityRule> rules = new ArrayList<AuthorityRule>();
        AuthorityRule authorityRule = new AuthorityRule();
        authorityRule.setResource("A");
        authorityRule.setLimitApp("ip");
        authorityRule.setStrategy(RuleConstant.AUTHORITY_BLACK);
        rules.add(authorityRule);

        AuthorityRule authorityRuleB = new AuthorityRule();
        authorityRuleB.setResource("B");
        authorityRuleB.setLimitApp("ip");
        authorityRuleB.setStrategy(RuleConstant.AUTHORITY_BLACK);
        rules.add(authorityRuleB);
        AuthorityRuleManager.loadRules(rules);
    }


}