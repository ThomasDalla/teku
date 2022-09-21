/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import tech.pegasys.teku.infrastructure.quorum.QuorumConfig;
import tech.pegasys.teku.services.ServiceControllerFacade;

public class QuorumNode implements Node, LeaderLatchListener {
  private final Node node; // reference to the actual node we run when we are the leader
  private final CuratorFramework client; // the Zookeeper client
  private final LeaderLatch latch; // the latch that notifies us when we're leader or not

  public QuorumNode(Node node, QuorumConfig config) {
    this.node = node;
    this.client =
        CuratorFrameworkFactory.newClient(
            config.connectionString(),
            config.sessionTimeoutMs(),
            config.connectionTimeoutMs(),
            new ExponentialBackoffRetry(config.baseSleepTimeMs(), config.maxRetries()));
    this.latch = new LeaderLatch(client, config.getPath(), config.getParticipantId());
  }

  /** LeaderLatchListener.isLeader() | We acquired leadership, so let's start the node */
  @Override
  public void isLeader() {
    this.node.start();
  }

  /** LeaderLatchListener.notLeader() | We lost leadership, stop the node! */
  @Override
  public void notLeader() {
    this.node.stop();
  }

  /** Node.start() | Kick-off the process */
  @Override
  public void start() {
    this.client.start();
    try {
      this.latch.start();
    } catch (Exception e) {
      throw new UnsupportedOperationException(e);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
  }

  /** Node.stop() | Exit the node (release the latch and stop the quorum client) */
  @Override
  public void stop() {
    this.node.stop();
    CloseableUtils.closeQuietly(this.latch);
    CloseableUtils.closeQuietly(this.client);
  }

  @Override
  public ServiceControllerFacade getServiceController() {
    return this.node.getServiceController();
  }
}
