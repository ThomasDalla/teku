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

package tech.pegasys.teku.services.powchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertWith;

import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositSnapshotResourceLoaderTest {
  private static final String SNAPSHOT_RESOURCE = "snapshot.ssz";

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final DepositSnapshotResourceLoader loader = new DepositSnapshotResourceLoader();

  private String notFoundResource;

  @BeforeEach
  public void setup() {
    this.notFoundResource = dataStructureUtil.randomBytes32().toHexString();
  }

  @Test
  public void shouldReturnEmpty_whenNoResourcePathProvided() {
    assertThat(loader.loadDepositSnapshot(Optional.empty())).isEmpty();
  }

  @Test
  public void shouldThrowInvalidConfigurationException_whenNotFound() {
    assertThatThrownBy(() -> loader.loadDepositSnapshot(Optional.of(notFoundResource)))
        .isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  public void shouldLoadDepositSnapshot_whenResourceIsCorrect() {
    assertWith(
        loader.loadDepositSnapshot(Optional.of(getResourceFilePath(SNAPSHOT_RESOURCE))),
        depositTreeSnapshot -> {
          assertThat(depositTreeSnapshot.isPresent()).isTrue();
          assertThat(depositTreeSnapshot.get().getDepositCount()).isEqualTo(16646);
        });
  }

  private String getResourceFilePath(final String resource) {
    final URL resourceUrl = DepositSnapshotResourceLoader.class.getResource(resource);
    return resourceUrl.getFile();
  }
}
