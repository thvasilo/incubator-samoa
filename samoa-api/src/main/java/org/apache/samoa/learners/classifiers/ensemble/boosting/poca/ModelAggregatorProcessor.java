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
import org.apache.samoa.core.Processor;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.topology.Stream;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The model aggregator gathers events from the weak learners, to update the boosting model and outputs predictions.
 *
 * In the current implementation we use a concurrent map to gather events mapped from instance id to weak learner state.
 * This way we can learn from multiple instances happen in parallel, although I think we might want to block
 * sending the updates back to WLs, until all the previous instances have finished.
 *
 * What I mean here: When working in the threaded model, an instance event could pass through some the WLs before the
 * instances preceding it do. For example, the event 2 might enter the aggregator before all WLs have sent their stats
 * for event 1. We should still gather the updates for event 2, but ideally we want to wait for the boosting updates
 * resulting from event 1 to make their way back to the WLs, updating their state, before sending the update for
 * event 2 as well.
 *
 * It might be the case that this approach is wrong as well. What happens when event 2 passes through the WLs, when
 * the boosting model updates from event 1 have not happened yet? Then event 2 will be processed with "stale" WLs and
 * passed on to the model aggregator. Say event 2 passes through WL 2, which is in round 1 still, on to the aggregator.
 * In the meantime, we send the boosting update resulting from event 1 to the WLs. Now WLs are (?) in round 2, and event
 * 2 arrives at WL 1. The prediction will now be wrong no? The boosting model has changed between WL 1 and WL 2, but we
 * have already sent WL 2's errors to the aggregator. Seems like a blocking mechanism is needed here as well.
 */
public class ModelAggregatorProcessor implements Processor {
  private static final long serialVersionUID = -8340785601983207579L;
  private final int ensembleSize;
  private Stream outputStream;
  private Stream modelUpdateStream;
  private int boostingState = 0;
  private int round = 0;
  // This is a map from instance index to weaklearner states
  private ConcurrentHashMap<Long, int[]> weakLearnerStates;

  @Override
  public boolean process(ContentEvent event) {

    POCALearnerEvent pocaLearnerEvent = (POCALearnerEvent) event;
    InstanceContentEvent inEvent = pocaLearnerEvent.getInstanceContentEvent();
    int sourceWLID = inEvent.getClassifierIndex();
    System.out.println(String.format("The event %d from WL %d has entered the ModelAggregatorProcessor",
        inEvent.getInstanceIndex(), sourceWLID));
    Long instanceIndex = pocaLearnerEvent.getInstanceContentEvent().getInstanceIndex();
    if (!weakLearnerStates.containsKey(instanceIndex)) {
      int[] wlState = new int[ensembleSize];
      // Initialize with -1 to indicate missing values
      Arrays.fill(wlState, -1);
      weakLearnerStates.put(instanceIndex, wlState);
    }
    int[] states = weakLearnerStates.get(instanceIndex);
    states[sourceWLID] = pocaLearnerEvent.getWeakLearnerState();
    // Crude readiness check, can prolly maintain one boolean to do this instead of iterating over all every time
    boolean readyToSend = true;
    for (int wlState : states) {
      if (wlState == -1) {
        readyToSend = false;
        break;
      }
    }
    if (readyToSend) {
      for (int i = 1; i <= states.length; i++) {
        // "Reverse-engineer" the shuffling mechanism
        int destinationID = i % states.length;
        int weakLearnerState = states[destinationID];
        boostingState += weakLearnerState;
        System.out.printf("Sending boosting update for instance %d to WL %d from ModelAggregatorProcessor%n",
            instanceIndex, destinationID);
        modelUpdateStream.put(new POCABoostingEvent(destinationID, boostingState, round));
      }
      // We are done with this instance, so we can safely remove it from the map
      weakLearnerStates.remove(instanceIndex);
      round++;
    }

    return true;
  }

  public ModelAggregatorProcessor(int ensembleSize) {
    this.ensembleSize = ensembleSize;
    int initialCapacity = 16; // 16 is the Java default
    this.weakLearnerStates = new ConcurrentHashMap<>(initialCapacity, 0.9f, 1);
    // Initialize to -1, indicating missing value
    for (long i = 0; i < initialCapacity; i++) {
      int[] wlState = new int[ensembleSize];
      // Initialize with -1 to indicate missing values
      Arrays.fill(wlState, -1);
      weakLearnerStates.put(i, wlState);
    }
  }

  public ModelAggregatorProcessor(ModelAggregatorProcessor other) {
    this.ensembleSize = other.ensembleSize;
    this.outputStream = other.outputStream;
    this.modelUpdateStream = other.modelUpdateStream;
    this.boostingState = other.boostingState;
    this.round = other.round;
    this.weakLearnerStates = other.weakLearnerStates; // Be aware of the shallow copy here
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
}
