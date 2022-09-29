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
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.node.Node;

/**
 * @author jialiang.linjl
 */
public class RateLimiterController implements TrafficShapingController {

    // 最大等待超时时间
    private final int maxQueueingTimeMs;
    // 限流阈值
    private final double count;

    // 最新的一次通过时间，这是一个原子变量
    private final AtomicLong latestPassedTime = new AtomicLong(-1);

    public RateLimiterController(int timeOut, double count) {
        this.maxQueueingTimeMs = timeOut;
        this.count = count;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // Pass when acquire count is less or equal than 0.
        // 当获取计数小于或等于0时通过。
        if (acquireCount <= 0) {
            return true;
        }
        // Reject when count is less or equal than 0.
        // Otherwise,the costTime will be max of long and waitTime will overflow in some cases.
        // 当count小于或等于0时拒绝。否则，costTime将为long的最大值，并且waitTime在某些情况下将溢出。
        if (count <= 0) {
            return false;
        }

        long currentTime = TimeUtil.currentTimeMillis();
        // Calculate the interval between every two requests.
        // 计算每个请求通过的平均时间，也是说把请求平均分配到1000毫秒上
        long costTime = Math.round(1.0 * (acquireCount) / count * 1000);

        // Expected pass time of this request.
        // 此请求的预计通过时间。
        long expectedTime = costTime + latestPassedTime.get();

        // 判断预计通过时间是否大于当前时间，
        // 如果小于当前时间则通过，如果大于当前时间则表明请求频繁，需要进行排队等待
        if (expectedTime <= currentTime) {
            // Contention may exist here, but it's okay.
            latestPassedTime.set(currentTime);
            return true;
        } else {
            // Calculate the time to wait.
            // 计算等待时间，这里的算法是：预计通过时间+加上次请求通过时间-当前时间；
            long waitTime = costTime + latestPassedTime.get() - TimeUtil.currentTimeMillis();
            // 判断等待时间是否大于最大超时时间，如果大于则return false 拒绝该请求。
            if (waitTime > maxQueueingTimeMs) {
                return false;
            } else {
                // 更新最新一条请求通过时间并获取。
                long oldTime = latestPassedTime.addAndGet(costTime);
                try {
                    // 重新计算计算等待时间。
                    waitTime = oldTime - TimeUtil.currentTimeMillis();
                    // 判断等待时间是否大于timeout时间，如果大于则还原最新一条请求通过时间，并return false 拒绝该请求。
                    if (waitTime > maxQueueingTimeMs) {
                        latestPassedTime.addAndGet(-costTime);
                        return false;
                    }
                    // in race condition waitTime may <= 0
                    // 判断是否大于0，排队睡眠。
                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                    }
                    return true;
                } catch (InterruptedException e) {
                }
            }
        }
        return false;
    }

}
