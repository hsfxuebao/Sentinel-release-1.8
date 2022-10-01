package com.sentinel.demo.cluster;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

public class EmbeddedClient {

    public static void main(String[] args) {
        initClusterFlowRule();
        initServer();
        initClusterClientRule();
        setToClient();
        while (true){
            try{
                Thread.sleep(100);
            } catch (Exception e){
                e.printStackTrace();
            }
            try {
                Entry entry= SphU.entry("a");
                System.out.println(LocalDateTime.now() +" "+"pass");
            } catch (Exception e){
                System.out.println(LocalDateTime.now()+" "+"block");
            }
        }
    }

    private static void initClusterFlowRule(){
        List<FlowRule> flowRules = new ArrayList<FlowRule>();
        FlowRule flowRule = new FlowRule();
        flowRule.setResource("a");
        ClusterFlowConfig clusterFlowConfig=new ClusterFlowConfig();
        clusterFlowConfig.setFallbackToLocalWhenFail(true);
        clusterFlowConfig.setFlowId(1L);
        clusterFlowConfig.setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL);
        flowRule.setClusterMode(true);
        flowRule.setClusterConfig(clusterFlowConfig);
        flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        flowRule.setCount(10);
        flowRule.setStrategy(RuleConstant.STRATEGY_DIRECT);
        flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flowRules.add(flowRule);
        FlowRuleManager.loadRules(flowRules);
        ClusterFlowRuleManager.registerPropertyIfAbsent("1-name");
        ClusterFlowRuleManager.loadRules("1-name", flowRules);
    }

    private static void initClusterClientRule(){
        ClusterClientConfig clusterClientConfig =  new ClusterClientConfig();
        clusterClientConfig.setRequestTimeout(1000);
        ClusterClientConfigManager.applyNewConfig(clusterClientConfig);
        ClusterClientAssignConfig clusterClientAssignConfig = new ClusterClientAssignConfig("127.0.0.1",18730);
        ClusterClientConfigManager.applyNewAssignConfig(clusterClientAssignConfig);
    }




    private static void initServer(){
        ServerTransportConfig serverTransportConfig = new ServerTransportConfig(18731, 600);
        ClusterServerConfigManager.loadGlobalTransportConfig(serverTransportConfig);
        Set<String> nameSpaceSet = new HashSet<String>();
        nameSpaceSet.add("1-name");
        ClusterServerConfigManager.loadServerNamespaceSet(nameSpaceSet);
    }

    private static void setToClient(){
        ClusterStateManager.setToClient();
    }



}