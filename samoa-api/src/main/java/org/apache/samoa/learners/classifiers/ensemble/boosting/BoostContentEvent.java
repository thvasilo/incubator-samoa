package org.apache.samoa.learners.classifiers.ensemble.boosting;

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


final public class BoostContentEvent implements ContentEvent {

  private static final long serialVersionUID = -7355070269691265304L;

  public BoostContentEvent(InstanceContentEvent instanceContentEvent, BoostingModel boostingModel) {
    this.instanceContentEvent = instanceContentEvent;
    this.boostingModel = boostingModel;
  }
  // These could be declared final and we do copies instead. Dunno which is better re. serialization.
  private InstanceContentEvent instanceContentEvent;
  private BoostingModel boostingModel;

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

  public void setInstanceContentEvent(InstanceContentEvent instanceContentEvent) {
    this.instanceContentEvent = instanceContentEvent;
  }

  public void setBoostingModel(BoostingModel boostingModel) {
    this.boostingModel = boostingModel;
  }
}
