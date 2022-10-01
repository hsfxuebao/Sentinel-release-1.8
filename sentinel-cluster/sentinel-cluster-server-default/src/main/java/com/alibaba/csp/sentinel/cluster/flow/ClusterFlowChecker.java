/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.cluster.flow;

import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.statistic.ClusterMetricStatistics;
import com.alibaba.csp.sentinel.cluster.flow.statistic.limit.GlobalRequestLimiter;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.flow.statistic.data.ClusterFlowEvent;
import com.alibaba.csp.sentinel.cluster.flow.statistic.metric.ClusterMetric;
import com.alibaba.csp.sentinel.cluster.server.log.ClusterServerStatLogUtil;
import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;

/**
 * Flow checker for cluster flow rules.
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
final class ClusterFlowChecker {

    private static double calcGlobalThreshold(FlowRule rule) {
        double count = rule.getCount();
        switch (rule.getClusterConfig().getThresholdType()) {
            case ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL:
                return count;
            case ClusterRuleConstant.FLOW_THRESHOLD_AVG_LOCAL:
            default:
                int connectedCount = ClusterFlowRuleManager.getConnectedCount(rule.getClusterConfig().getFlowId());
                return count * connectedCount;
        }
    }

    static boolean allowProceed(long flowId) {
        String namespace = ClusterFlowRuleManager.getNamespace(flowId);
        return GlobalRequestLimiter.tryPass(namespace);
    }

    static TokenResult acquireClusterToken(/*@Valid*/ FlowRule rule, int acquireCount, boolean prioritized) {
        Long id = rule.getClusterConfig().getFlowId();
        // 是否继续，根据RuleId，获取NameSpace，根据nameSpace，判断nameSpace限流是否通过
        if (!allowProceed(id)) {
            return new TokenResult(TokenResultStatus.TOO_MANY_REQUEST);
        }

        ClusterMetric metric = ClusterMetricStatistics.getMetric(id);
        if (metric == null) {
            return new TokenResult(TokenResultStatus.FAIL);
        }
        // 获取Metric，滑动窗口实现，这里获取的是通过的请求平均值
        // 根据限流的总数 - 已经发放token数 - 当前申请的 token 数
        double latestQps = metric.getAvg(ClusterFlowEvent.PASS);
        //获取全局阀值 根据规则判断是否为全局限流还是平均分摊，并获取总的阀值
        double globalThreshold = calcGlobalThreshold(rule) * ClusterServerConfigManager.getExceedCount();
        //判断剩余请求数
        double nextRemaining = globalThreshold - latestQps - acquireCount;

        // 剩余 token 数大于0说明可以通过
        if (nextRemaining >= 0) {
            // TODO: checking logic and metric operation should be separated.
            //记录请求数量
            metric.add(ClusterFlowEvent.PASS, acquireCount);
            metric.add(ClusterFlowEvent.PASS_REQUEST, 1);
            if (prioritized) {
                // Add prioritized pass.
                metric.add(ClusterFlowEvent.OCCUPIED_PASS, acquireCount);
            }
            // Remaining count is cut down to a smaller integer.
            return new TokenResult(TokenResultStatus.OK)
                .setRemaining((int) nextRemaining)
                .setWaitInMs(0);
        } else {
            if (prioritized) {
                // Try to occupy incoming buckets.
                double occupyAvg = metric.getAvg(ClusterFlowEvent.WAITING);
                if (occupyAvg <= ClusterServerConfigManager.getMaxOccupyRatio() * globalThreshold) {
                    int waitInMs = metric.tryOccupyNext(ClusterFlowEvent.PASS, acquireCount, globalThreshold);
                    // waitInMs > 0 indicates pre-occupy incoming buckets successfully.
                    if (waitInMs > 0) {
                        ClusterServerStatLogUtil.log("flow|waiting|" + id);
                        return new TokenResult(TokenResultStatus.SHOULD_WAIT)
                            .setRemaining(0)
                            .setWaitInMs(waitInMs);
                    }
                    // Or else occupy failed, should be blocked.
                }
            }
            // Blocked.
            // 进行限流
            metric.add(ClusterFlowEvent.BLOCK, acquireCount);
            metric.add(ClusterFlowEvent.BLOCK_REQUEST, 1);
            ClusterServerStatLogUtil.log("flow|block|" + id, acquireCount);
            ClusterServerStatLogUtil.log("flow|block_request|" + id, 1);
            if (prioritized) {
                // Add prioritized block.
                metric.add(ClusterFlowEvent.OCCUPIED_BLOCK, acquireCount);
                ClusterServerStatLogUtil.log("flow|occupied_block|" + id, 1);
            }

            return blockedResult();
        }
    }

    private static TokenResult blockedResult() {
        return new TokenResult(TokenResultStatus.BLOCKED)
            .setRemaining(0)
            .setWaitInMs(0);
    }

    private ClusterFlowChecker() {}
}
