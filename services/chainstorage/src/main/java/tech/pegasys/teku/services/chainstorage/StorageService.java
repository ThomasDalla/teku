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

package tech.pegasys.teku.services.chainstorage;

import static tech.pegasys.teku.spec.config.Constants.STORAGE_QUERY_CHANNEL_PARALLELISM;

import java.util.Optional;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.server.BatchingVoteUpdateChannel;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.CombinedStorageChannelSplitter;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.RetryingStorageUpdateChannel;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

public class StorageService extends Service implements StorageServiceFacade {
  private final StorageConfiguration config;
  private volatile ChainStorage chainStorage;
  private final ServiceConfig serviceConfig;
  private volatile Database database;
  private volatile BatchingVoteUpdateChannel batchingVoteUpdateChannel;

  public StorageService(
      final ServiceConfig serviceConfig, final StorageConfiguration storageConfiguration) {
    this.serviceConfig = serviceConfig;
    this.config = storageConfiguration;
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.fromRunnable(
        () -> {
          final AsyncRunner storageAsyncRunner =
              serviceConfig.createAsyncRunner("storageAsyncRunner");
          final VersionedDatabaseFactory dbFactory =
              new VersionedDatabaseFactory(
                  serviceConfig.getMetricsSystem(),
                  serviceConfig.getDataDirLayout().getBeaconDataDirectory(),
                  Optional.of(storageAsyncRunner),
                  config);
          database = dbFactory.createDatabase();

          database.migrate();

          final EventChannels eventChannels = serviceConfig.getEventChannels();
          chainStorage =
              ChainStorage.create(
                  database,
                  Optional.of(
                      eventChannels.getPublisher(ExecutionLayerChannel.class, storageAsyncRunner)),
                  config.getSpec());
          final DepositStorage depositStorage =
              DepositStorage.create(eventChannels.getPublisher(Eth1EventsChannel.class), database);

          batchingVoteUpdateChannel =
              new BatchingVoteUpdateChannel(
                  chainStorage,
                  new AsyncRunnerEventThread(
                      "batch-vote-updater", serviceConfig.getAsyncRunnerFactory()));

          if (config.isAsyncStorageEnabled()) {
            eventChannels.subscribe(
                CombinedStorageChannel.class,
                new CombinedStorageChannelSplitter(
                    serviceConfig.createAsyncRunner(
                        "storage_query", STORAGE_QUERY_CHANNEL_PARALLELISM),
                    new RetryingStorageUpdateChannel(chainStorage, serviceConfig.getTimeProvider()),
                    chainStorage));
          } else {
            eventChannels
                .subscribe(StorageUpdateChannel.class, chainStorage)
                .subscribeMultithreaded(
                    StorageQueryChannel.class, chainStorage, STORAGE_QUERY_CHANNEL_PARALLELISM);
          }

          eventChannels
              .subscribe(Eth1DepositStorageChannel.class, depositStorage)
              .subscribe(Eth1EventsChannel.class, depositStorage)
              .subscribe(VoteUpdateChannel.class, batchingVoteUpdateChannel);
        });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.fromRunnable(database::close);
  }

  @Override
  public ChainStorage getChainStorage() {
    return chainStorage;
  }
}
