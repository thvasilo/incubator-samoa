package org.apache.samoa.learners.classifiers.ensemble.boosting.poca;
/*
 * #%L
 * SAMOA
 * %%
 * Copyright (C) 2014 - 2015 Apache Software Foundation
 * %%
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
 * #L%
 */

import org.apache.samoa.core.ContentEvent;

public class POCABoostingEvent implements ContentEvent {
  private static final long serialVersionUID = 5090038998841941990L;
  // Set both as final so we don't mess about with shared objects
  private final int boostingState;
  private final int weakLearnerID;
  private final int round;

  public POCABoostingEvent(int weakLearnerID, int state, int round) {
    this.weakLearnerID = weakLearnerID;
    this.boostingState = state;
    this.round = round;
  }

  public POCABoostingEvent(POCABoostingEvent other) {
    this.weakLearnerID = other.weakLearnerID;
    this.boostingState = other.boostingState;
    this.round = other.round;
  }

  @Override
  public String getKey() {
    return String.valueOf(weakLearnerID);
  }

  @Override
  public void setKey(String key) {

  }

  @Override
  public boolean isLastEvent() {
    // TODO: Figure out what to do here.
    return false;
  }

  public int getBoostingState() {
    return boostingState;
  }

  public int getWeakLearnerID() {
    return weakLearnerID;
  }

  public int getRound() {
    return round;
  }
}
