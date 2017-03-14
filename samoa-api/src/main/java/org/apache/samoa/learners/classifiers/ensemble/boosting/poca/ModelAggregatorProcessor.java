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

import org.apache.commons.collections.ArrayStack;
import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.topology.Stream;

import java.util.Arrays;

public class ModelAggregatorProcessor implements Processor {
  private static final long serialVersionUID = -8340785601983207579L;
  private Stream outputStream;
  private Stream modelUpdateStream;
  private int boostingState = 0;
  private int round = 0;
  private int[] weakLearnerStates;

  @Override
  public boolean process(ContentEvent event) {

    POCALearnerEvent pocaLearnerEvent = (POCALearnerEvent) event;
    InstanceContentEvent inEvent = pocaLearnerEvent.getInstanceContentEvent();
    int sourceWLID = inEvent.getClassifierIndex();
    System.out.println(String.format("The event %d from WL %d has entered the ModelAggregatorProcessor",
        inEvent.getInstanceIndex(), sourceWLID));
    weakLearnerStates[sourceWLID] = pocaLearnerEvent.getWeakLearnerState();
    // Crude readiness check, can prolly maintain one boolean to do this instead of iterating over all every time
    boolean readyToSend = true;
    for (int wlState : weakLearnerStates) {
      if (wlState == -1) {
        readyToSend = false;
        break;
      }
    }
    if (readyToSend) {
      for (int i = 1; i <= weakLearnerStates.length; i++) {
        // "Reverse-engineer" the shuffling mechanism
        int destinationID = i % weakLearnerStates.length;
        int weakLearnerState = weakLearnerStates[destinationID];
        boostingState += weakLearnerState;
        modelUpdateStream.put(new POCABoostingEvent(destinationID, boostingState, round));
      }
      Arrays.fill(weakLearnerStates, -1);
      round++;
    }

    return true;
  }

  public ModelAggregatorProcessor(int ensembleSize) {
    this.weakLearnerStates = new int[ensembleSize];
    // Initialize to -1, indicating missing value
    Arrays.fill(weakLearnerStates, -1);
  }

  public ModelAggregatorProcessor(ModelAggregatorProcessor other) {
    this.outputStream = other.outputStream;
    this.modelUpdateStream = other.modelUpdateStream;
    this.boostingState = other.boostingState;
    this.round = other.round;
    this.weakLearnerStates = Arrays.copyOf(other.weakLearnerStates, other.weakLearnerStates.length);
  }

  @Override
  public void onCreate(int id) {

  }

  @Override
  public Processor newProcessor(Processor oldProcessor) {
    ModelAggregatorProcessor oldLocalProcessor = (ModelAggregatorProcessor) oldProcessor;
    return new ModelAggregatorProcessor(oldLocalProcessor);
  }

  public Stream getModelUpdateStream() {
    return modelUpdateStream;
  }

  public Stream getOutputStream() {
    return outputStream;
  }

  public void setOutputStream(Stream outputStream) {
    this.outputStream = outputStream;
  }

  public void setModelUpdateStream(Stream modelUpdateStream) {
    this.modelUpdateStream = modelUpdateStream;
  }

  public void setWeakLearnerStates(int[] weakLearnerStates) {
    this.weakLearnerStates = weakLearnerStates;
  }
}
