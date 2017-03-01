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

import org.apache.samoa.learners.InstanceContent;
import org.apache.samoa.learners.classifiers.LocalLearner;

public class ToyBoost implements BoostingModel {
  private int super_steps = 0;
  private int steps = 0;

  @Override
  public double[] predict(InstanceContent instance) {
    return new double[0];
  }

  @Override
  public void update(InstanceContent trainInstance, double[] votes) {
    super_steps++;
  }

  @Override
  public void updateWeak(InstanceContent trainInstance, LocalLearner weakLearner) {
    steps++;
    System.out.println("State of boosting model: " + steps);
  }
}
