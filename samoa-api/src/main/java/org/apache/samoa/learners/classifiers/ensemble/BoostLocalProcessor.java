package org.apache.samoa.learners.classifiers.ensemble;
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
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.learners.ResultContentEvent;
import org.apache.samoa.learners.classifiers.LocalLearner;
import org.apache.samoa.topology.Stream;

public class BoostLocalProcessor implements Processor {

  private static final long serialVersionUID = -8744327519836673493L;
  private final int processorId;
  private final int ensembleSize;

  // This is the local learner instance that we will be training.
  private LocalLearner localLearner;

  private Stream outputStream;


  public BoostLocalProcessor(int processorId, int ensembleSize, LocalLearner localLearner) {
    this.processorId = processorId;
    this.localLearner = localLearner;
    this.ensembleSize = ensembleSize;
  }


  @Override
  public boolean process(ContentEvent event) {
    System.out.println("id: " + processorId + " event: " + event);

    InstanceContentEvent inEvent = (InstanceContentEvent) event;
    Instance instance = inEvent.getInstance();

    if (inEvent.getInstanceIndex() < 0) {
      // end learning
      ResultContentEvent outContentEvent = new ResultContentEvent(-1, instance, 0,
          new double[0], inEvent.isLastEvent());
      return false;
    }

    if (inEvent.isTesting()) {
      double[] votes = localLearner.getVotesForInstance(instance);
      ResultContentEvent outContentEvent = new ResultContentEvent(inEvent.getInstanceIndex(),
          instance, inEvent.getClassId(), votes, inEvent.isLastEvent());
    }

    if (inEvent.isTraining()) {
      localLearner.trainOnInstance(instance);
    }
    outputStream.put(event);
    return true;
  }

  @Override
  public void onCreate(int id) {
    localLearner.resetLearning();
  }

  @Override
  public Processor newProcessor(Processor oldProcessor) {
    BoostLocalProcessor oldLocalProcessor = (BoostLocalProcessor) oldProcessor;
    BoostLocalProcessor newProcessor = new BoostLocalProcessor(
        oldLocalProcessor.getProcessorId(),
        oldLocalProcessor.getEnsembleSize(),
        oldLocalProcessor.getLocalLearner());
    newProcessor.getLocalLearner().resetLearning();
    newProcessor.setOutputStream(oldLocalProcessor.getOutputStream());
    return newProcessor;
  }

  public void setOutputStream(Stream outputStream) {
    this.outputStream = outputStream;
  }

  public Stream getOutputStream() {
    return outputStream;
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
}
