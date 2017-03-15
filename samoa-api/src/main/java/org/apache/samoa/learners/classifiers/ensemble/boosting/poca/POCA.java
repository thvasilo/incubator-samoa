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

import com.github.javacliparser.*;
import com.google.common.collect.ImmutableSet;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instances;
import org.apache.samoa.learners.Learner;
import org.apache.samoa.learners.classifiers.SimpleClassifierAdapter;
import org.apache.samoa.moa.classifiers.Classifier;
import org.apache.samoa.moa.classifiers.functions.MajorityClass;
import org.apache.samoa.moa.classifiers.trees.DecisionStump;
import org.apache.samoa.topology.Stream;
import org.apache.samoa.topology.TopologyBuilder;

import java.util.Objects;
import java.util.Set;

public class POCA implements Learner, Configurable {
  private static final long serialVersionUID = 6250360713967443231L;

  private ModelAggregatorProcessor modelProcessor;
  private InputProcessor inputProcessor;

  public MultiChoiceOption engineOption = new MultiChoiceOption("engine", 'e', "The engine being used",
      new String[]{"local", "threads"}, new String[]{"local", "threads"}, 0);

  public IntOption ensembleSizeOption = new IntOption("ensembleSize", 's',
      "The number of models in the bag.", 5, 1, Integer.MAX_VALUE);

  public ClassOption baseLearnerOption = new ClassOption("baseLearner", 'l',
      "Classifier to train.", org.apache.samoa.moa.classifiers.Classifier.class, MajorityClass.class.getName());

  @Override
  public void init(TopologyBuilder topologyBuilder, Instances dataset, int parallelism) {
    int ensembleSize = ensembleSizeOption.getValue();
    boolean threadsEngine = Objects.equals(engineOption.getChosenLabel(), "threads");

    // Instantiate the input processor and add it to the ensemble
    inputProcessor = new InputProcessor();
    topologyBuilder.addProcessor(inputProcessor);

    // Instantiate the weak learner processors, and add them to the topology
    Classifier baseLearner = ((org.apache.samoa.moa.classifiers.Classifier) this.baseLearnerOption.getValue()).copy();
    SimpleClassifierAdapter localLearner = new SimpleClassifierAdapter(baseLearner, dataset);
    // We add a local learner instance and the learner id to the weak learner processor
    POCAWeakLearnerProcessor weakLearnerProcessor = new POCAWeakLearnerProcessor(
        ensembleSize, localLearner, threadsEngine);
    // Instantiate the weak learner processor, with parallelism == ensembleSize
    topologyBuilder.addProcessor(weakLearnerProcessor, ensembleSize);

    // Connect the input to the weak learner processors, we broadcast each element to all
    Stream inputStream = topologyBuilder.createStream(inputProcessor);
    topologyBuilder.connectInputAllStream(inputStream, weakLearnerProcessor);
    inputProcessor.setInputEventStream(inputStream);

    // Create the model processor that is used to aggregate the outcomes, and passes back boosting updates to the WLs
    modelProcessor = new ModelAggregatorProcessor(ensembleSize);
    topologyBuilder.addProcessor(modelProcessor);

    // We then gather the outputs of the learners in the model processor, from all WLs to the one model processor
    Stream weakLearnerStream = topologyBuilder.createStream(weakLearnerProcessor);
    topologyBuilder.connectInputAllStream(weakLearnerStream, modelProcessor);
    weakLearnerProcessor.setLearnerOutputStream(weakLearnerStream);

    // The model processor pushes updates back to the weak learners, using shuffling to route events
    Stream modelUpdateStream = topologyBuilder.createStream(modelProcessor);
    topologyBuilder.connectInputShuffleStream(modelUpdateStream, weakLearnerProcessor);
    modelProcessor.setModelUpdateStream(modelUpdateStream);
  }

  @Override
  public Processor getInputProcessor() {
    return inputProcessor;
  }

  @Override
  public Set<Stream> getResultStreams() {
    return ImmutableSet.of(modelProcessor.getOutputStream());
  }
}
