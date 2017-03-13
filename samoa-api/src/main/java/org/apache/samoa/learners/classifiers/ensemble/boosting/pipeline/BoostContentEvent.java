package org.apache.samoa.learners.classifiers.ensemble.boosting.pipeline;

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
import org.apache.samoa.moa.core.DoubleVector;
import org.apache.samoa.learners.InstanceContentEvent;

/**
 * A BoostContentEvent is used to encapsulate an InstanceContentEvent, the state of the boosting model,
 * and the current aggregate prediction. As the event moves down the boosting pipeline, the boosting model and
 * the aggregate prediction are updated in place.
 */
final public class BoostContentEvent implements ContentEvent {

  private static final long serialVersionUID = -7355070269691265304L;

  private final InstanceContentEvent instanceContentEvent;
  private final BoostingModel boostingModel;
  private final DoubleVector predictionSum;

  public BoostContentEvent(
      InstanceContentEvent instanceContentEvent, BoostingModel boostingModel, DoubleVector predictionSum) {
    this.instanceContentEvent = instanceContentEvent;
    this.boostingModel = boostingModel;
    this.predictionSum = predictionSum;
  }
  @Override
  public String getKey() {
    return instanceContentEvent.getKey();
  }

  @Override
  public void setKey(String key) {
    instanceContentEvent.setKey(key);
  }

  @Override
  public boolean isLastEvent() {
    return instanceContentEvent.isLastEvent();
  }

  public InstanceContentEvent getInstanceContentEvent() {
    return instanceContentEvent;
  }

  public BoostingModel getBoostingModel() {
    return boostingModel;
  }

  public DoubleVector getPredictionSum() {
    return predictionSum;
  }
}
