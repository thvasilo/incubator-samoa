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
import org.apache.samoa.learners.InstanceContentEvent;

public class POCALearnerEvent implements ContentEvent {
  private static final long serialVersionUID = 3053528101329529816L;
  private final InstanceContentEvent instanceContentEvent;
  private final int weakLearnerState;

  public POCALearnerEvent(InstanceContentEvent instanceContentEvent, int wlState) {
    this.instanceContentEvent = instanceContentEvent;
    this.weakLearnerState = wlState;
  }

  @Override
  public String getKey() {
    return String.valueOf(instanceContentEvent.getClassifierIndex());
  }

  @Override
  public void setKey(String key) {

  }

  public InstanceContentEvent getInstanceContentEvent() {
    return instanceContentEvent;
  }

  public int getWeakLearnerState() {
    return weakLearnerState;
  }


  @Override
  public boolean isLastEvent() {
    return false;
  }
}
