package org.apache.samoa.learners.classifiers.trees;

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

import java.util.HashMap;
import java.util.Map;

import org.apache.samoa.learners.classifiers.ModelAggregator;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.classifiers.ensemble.BoostMAProcessor;
import org.apache.samoa.moa.classifiers.core.AttributeSplitSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActiveLearningNode extends LearningNode {
  /**
	 *
	 */
  public enum SplittingOption {THROW_AWAY, KEEP_WO_BUFFER, KEEP_W_BUFFER};

  private static final long serialVersionUID = -2892102872646338908L;
  private static final Logger logger = LoggerFactory.getLogger(ActiveLearningNode.class);

  private final SplittingOption splittingOption;

  private double weightSeenAtLastSplitEvaluation;

  private final Map<Integer, String> attributeContentEventKeys;

  private AttributeSplitSuggestion bestSuggestion;
  private AttributeSplitSuggestion secondBestSuggestion;

  private final long id;
  private final int parallelismHint;
  private int suggestionCtr;
  private int thrownAwayInstance;

  private int ensembleId; //faye boostVHT

  private boolean isSplitting;

  public ActiveLearningNode(double[] classObservation, int parallelismHint, SplittingOption splitOption) {
    super(classObservation);
    this.weightSeenAtLastSplitEvaluation = this.getWeightSeen();
    this.id = VerticalHoeffdingTree.LearningNodeIdGenerator.generate(); //todo (faye) :: ask if this could affect the singleton property.
    this.attributeContentEventKeys = new HashMap<>();
    this.isSplitting = false;
    this.parallelismHint = parallelismHint;
    this.splittingOption = splitOption;
  }

  long getId() {
    return id;
  }

  public AttributeBatchContentEvent[] attributeBatchContentEvent;

  public AttributeBatchContentEvent[] getAttributeBatchContentEvent() {
    return this.attributeBatchContentEvent;
  }

  public void setAttributeBatchContentEvent(AttributeBatchContentEvent[] attributeBatchContentEvent) {
    this.attributeBatchContentEvent = attributeBatchContentEvent;
  }

  @Override
  public void learnFromInstance(Instance inst, ModelAggregator proc) {
    if (isSplitting) {
      switch (this.splittingOption) {
        case THROW_AWAY:
          logger.trace("node {} is splitting, throw away the instance",
              this.id); // throw all instance will splitting
          this.thrownAwayInstance++;
          return;
        case KEEP_WO_BUFFER:
          logger.trace("Keep instance without buffer, continue sending to local stats");
          // do nothing here
          break;
        case KEEP_W_BUFFER:
          // TODO: create the buffer
          break;
        default:
          logger.error("Invalid splittingOption option: {}",
              this.splittingOption);
          break;
      }
    }

    this.observedClassDistribution.addToValue((int) inst.classValue(),
        inst.weight());
    // done: parallelize by sending attributes one by one
    // TODO: meanwhile, we can try to use the ThreadPool to execute it
    // separately
    // DONE: parallelize by sending in batch, i.e. split the attributes into
    // chunk instead of send the attribute one by one
    for (int i = 0; i < inst.numAttributes() - 1; i++) {
      int instAttIndex = modelAttIndexToInstanceAttIndex(i, inst);
      Integer obsIndex = i;
      String key = attributeContentEventKeys.get(obsIndex);

      if (key == null) {
        key = this.generateKey(i);
        attributeContentEventKeys.put(obsIndex, key);
      }
      AttributeContentEvent ace = new AttributeContentEvent.Builder(
          this.id, i, key)
          .attrValue(inst.value(instAttIndex))
          .classValue((int) inst.classValue())
          .weight(inst.weight())
          .isNominal(inst.attribute(instAttIndex).isNominal())
          .build();
      if (this.attributeBatchContentEvent == null) {
        this.attributeBatchContentEvent = new AttributeBatchContentEvent[inst.numAttributes() - 1];
      }
      if (this.attributeBatchContentEvent[i] == null) {
        this.attributeBatchContentEvent[i] = new AttributeBatchContentEvent.Builder(
            this.id, i, key)
            // .attrValue(inst.value(instAttIndex))
            // .classValue((int) inst.classValue())
            // .weight(inst.weight()]
            .isNominal(inst.attribute(instAttIndex).isNominal())
            .build();
      }
      this.attributeBatchContentEvent[i].add(ace);
      // proc.sendToAttributeStream(ace);
    }
  }

  @Override
  public double[] getClassVotes(Instance inst, ModelAggregator map) {
    return this.observedClassDistribution.getArrayCopy();
  }

  public double getWeightSeen() {
    return this.observedClassDistribution.sumOfValues();
  }

  public void setWeightSeenAtLastSplitEvaluation(double weight) {
    this.weightSeenAtLastSplitEvaluation = weight;
  }

  public double getWeightSeenAtLastSplitEvaluation() {
    return this.weightSeenAtLastSplitEvaluation;
  }

  public void requestDistributedSuggestions(long splitId, ModelAggregator modelAggrProc) {
    this.isSplitting = true;
    this.suggestionCtr = 0;
    this.thrownAwayInstance = 0;

    ComputeContentEvent cce = new ComputeContentEvent(splitId, this.id,
        this.getObservedClassDistribution());
    if (modelAggrProc instanceof  ModelAggregatorProcessor){
      ModelAggregatorProcessor map = (ModelAggregatorProcessor)modelAggrProc;
      map.sendToControlStream(cce);
    } else if(modelAggrProc instanceof BoostMAProcessor) {
      cce.setEnsembleId(this.ensembleId); //faye boostVHT
      BoostMAProcessor bmap = (BoostMAProcessor)modelAggrProc;
      bmap.sendToControlStream(cce);
    }
  }

  public void addDistributedSuggestions(AttributeSplitSuggestion bestSuggestion, AttributeSplitSuggestion secondBestSuggestion) {
    // starts comparing from the best suggestion
    if (bestSuggestion != null) {
      if ((this.bestSuggestion == null) || (bestSuggestion.compareTo(this.bestSuggestion) > 0)) {
        this.secondBestSuggestion = this.bestSuggestion;
        this.bestSuggestion = bestSuggestion;

        if (secondBestSuggestion != null) {

          if ((this.secondBestSuggestion == null) || (secondBestSuggestion.compareTo(this.secondBestSuggestion) > 0)) {
            this.secondBestSuggestion = secondBestSuggestion;
          }
        }
      } else {
        if ((this.secondBestSuggestion == null) || (bestSuggestion.compareTo(this.secondBestSuggestion) > 0)) {
          this.secondBestSuggestion = bestSuggestion;
        }
      }
    }

    // TODO: optimize the code to use less memory
    this.suggestionCtr++;
  }

  public boolean isSplitting() {
    return this.isSplitting;
  }

  public void endSplitting() {
    this.isSplitting = false;
    logger.trace("wasted instance: {}", this.thrownAwayInstance);
    this.thrownAwayInstance = 0;
    this.bestSuggestion = null;
    this.secondBestSuggestion = null;
  }

  public AttributeSplitSuggestion getDistributedBestSuggestion() {
    return this.bestSuggestion;
  }

  public AttributeSplitSuggestion getDistributedSecondBestSuggestion() {
    return this.secondBestSuggestion;
  }

  public boolean isAllSuggestionsCollected() {
    return (this.suggestionCtr == this.parallelismHint);
  }

  private static int modelAttIndexToInstanceAttIndex(int index, Instance inst) {
    return inst.classIndex() > index ? index : index + 1;
  }

  private String generateKey(int obsIndex) {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (this.id ^ (this.id >>> 32));
    result = prime * result + obsIndex;
    return Integer.toString(result);
  }
  
  //-----------------faye boostVHT
  public int getEnsembleId() {
    return ensembleId;
  }
  
  public void setEnsembleId(int ensembleId) {
    this.ensembleId = ensembleId;
  }
}
