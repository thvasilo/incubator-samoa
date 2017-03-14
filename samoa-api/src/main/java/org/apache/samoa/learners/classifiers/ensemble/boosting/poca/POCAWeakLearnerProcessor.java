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
import org.apache.samoa.core.DoubleVector;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.learners.classifiers.LocalLearner;
import org.apache.samoa.topology.Stream;

public class POCAWeakLearnerProcessor implements Processor{
  // TODO: Can probably just extend BoostLocalProcessor

  private static final long serialVersionUID = -4897291821301014811L;
  private int processorId;
  private final int ensembleSize;

  // POCA state variables
  private double q, s;
  private DoubleVector epsilon;


  // This is the local learner instance that we will be training.
  private LocalLearner localLearner;

  // The output stream is directed either at the next learner in the boosting pipeline, or the BoostModelProcessor
  private Stream learnerOutputStream;


  public POCAWeakLearnerProcessor(int ensembleSize, LocalLearner localLearner) {
    this.localLearner = localLearner;
    this.ensembleSize = ensembleSize;
  }


  @Override
  public boolean process(ContentEvent event) {
    InstanceContentEvent inEvent = (InstanceContentEvent) event;
    System.out.println(String.format("The event %d has entered WeakProcessor %d",
        inEvent.getInstanceIndex(), processorId));
    Instance instanceCopy = inEvent.getInstance().copy();
    InstanceContentEvent outEvent = new InstanceContentEvent(inEvent.getInstanceIndex(), instanceCopy,
        inEvent.isTraining(), inEvent.isTesting());
    outEvent.setClassifierIndex(processorId);
    learnerOutputStream.put(outEvent);
    return true;
  }

  @Override
  public void onCreate(int id) {
    localLearner.resetLearning();
    processorId = id;
  }

  @Override
  public Processor newProcessor(Processor oldProcessor) {
    POCAWeakLearnerProcessor oldLocalProcessor = (POCAWeakLearnerProcessor) oldProcessor;
    POCAWeakLearnerProcessor newProcessor = new POCAWeakLearnerProcessor(
        oldLocalProcessor.getEnsembleSize(),
        oldLocalProcessor.getLocalLearner());
    newProcessor.getLocalLearner().resetLearning();
    newProcessor.setLearnerOutputStream(oldLocalProcessor.getLearnerOutputStream());
    return newProcessor;
  }

  public int getProcessorId() {
    return processorId;
  }

  public int getEnsembleSize() {
    return ensembleSize;
  }

  public LocalLearner getLocalLearner() {
    return localLearner;
  }

  public Stream getLearnerOutputStream() {
    return learnerOutputStream;
  }

  public void setLearnerOutputStream(Stream learnerOutputStream) {
    this.learnerOutputStream = learnerOutputStream;
  }
}
