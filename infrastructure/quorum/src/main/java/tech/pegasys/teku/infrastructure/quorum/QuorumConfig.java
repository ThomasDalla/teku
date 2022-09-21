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

package tech.pegasys.teku.infrastructure.quorum;

public class QuorumConfig {
  public String connectionString() {
    return "";
  }

  public String getPath() {
    return "";
  }

  public String getParticipantId() {
    return "";
  }

  public int sessionTimeoutMs() {
    return 0;
  }

  public int connectionTimeoutMs() {
    return 0;
  }

  public int baseSleepTimeMs() {
    return 0;
  }

  public int maxRetries() {
    return 0;
  }
}
