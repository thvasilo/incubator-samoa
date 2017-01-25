package org.apache.samoa.learners.classifiers.ensemble;/*
 Copyright (C) 2015 Daniel Gillblad, Olof Gornerup , Theodoros Vasiloudis (dgi@sics.se,
 olofg@sics.se, tvas@sics.se).

 Licensed under the Apache License, Version 2.0 (the "License")
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.instances.Instances;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.learners.InstancesContentEvent;
import org.apache.samoa.learners.classifiers.trees.ModelAggregatorProcessor;
import org.apache.samoa.moa.classifiers.core.splitcriteria.SplitCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BoostVHTModelAggregatorProcessor implements Processor {

  private static final long serialVersionUID = -1685875718300564886L;
  private static final Logger logger = LoggerFactory.getLogger(ModelAggregatorProcessor.class);

  private int processorId;

  private final int ensembleSize;
  private final Instances dataset;

  private ModelAggregatorProcessor[] modelAggregatorProcessors;

  public BoostVHTModelAggregatorProcessor(int ensembleSize, Instances dataset) {
    this.ensembleSize = ensembleSize;
    this.dataset = dataset;
  }

  @Override
  public boolean process(ContentEvent event) {
    if (event instanceof InstanceContentEvent) {
      InstanceContentEvent instanceEvent = (InstanceContentEvent) event;
      Instance inst = instanceEvent.getInstance();
      for (int i = 0; i < ensembleSize; i++) {
        double[] votes = modelAggregatorProcessors[i].getVotesForInstance(inst, false);
      }
    }
    return false;
  }

  @Override
  public void onCreate(int id) {
    this.processorId = id;

    for (int i = 0; i < ensembleSize; i++) {
      modelAggregatorProcessors[i] = new ModelAggregatorProcessor.Builder(dataset)
          .build();
    }
  }

  @Override
  public Processor newProcessor(Processor processor) {
    return null;
  }
}
