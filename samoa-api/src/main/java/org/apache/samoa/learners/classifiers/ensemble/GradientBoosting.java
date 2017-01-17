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
package org.apache.samoa.learners.classifiers.ensemble;


import com.github.javacliparser.ClassOption;
import com.github.javacliparser.Configurable;
import com.github.javacliparser.IntOption;
import com.google.common.collect.ImmutableSet;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instances;
import org.apache.samoa.learners.ClassificationLearner;
import org.apache.samoa.learners.Learner;
import org.apache.samoa.learners.classifiers.trees.VerticalHoeffdingTree;
import org.apache.samoa.topology.Stream;
import org.apache.samoa.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * The Gradient Boosting Classifier topology.
 */
public class GradientBoosting implements ClassificationLearner, Configurable {
  
  /**
   * The Constant serialVersionUID.
   */
  private static final long serialVersionUID = -2971850264864952099L;
  private static final Logger logger = LoggerFactory.getLogger(GradientBoosting.class);
  
  /**
   * The base learner option.
   */
  public ClassOption baseLearnerOption = new ClassOption("baseLearner", 'l',
          "Classifier to train.", Learner.class, VerticalHoeffdingTree.class.getName());
  
  /**
   * The ensemble size option.
   */
  public IntOption ensembleSizeOption = new IntOption("ensembleSize", 's',
          "The number of models in the bag.", 10, 1, Integer.MAX_VALUE);
  
  /**
   * The gradient boosting distributor processor.
   */
  private GradientBoostingDistributorProcessor gradientDistributorP;
  
  /**
   * The result stream.
   */
  protected Stream resultStream;
  
  /**
   * The input testing streams for the boosting ensemble, one per member.
   */
  private Stream[] ensembleStreams;
  
  /**
   * The input prediction streams for the boosting ensemble, one per member.
   */
  private Stream[] predictionStreams;
  
  /**
   * The dataset.
   */
  private Instances dataset;
  
  protected Learner[] ensembleClassifiers;
  
  protected int parallelism;
  
  /**
   * Sets the layout.
   */
  protected void setLayout() {
    
    int sizeEnsemble = this.ensembleSizeOption.getValue();
    
    gradientDistributorP = new GradientBoostingDistributorProcessor();
    gradientDistributorP.setEnsembleSize(sizeEnsemble);
    this.builder.addProcessor(gradientDistributorP, 1);
    
    // instantiate classifier
    ensembleClassifiers = new Learner[sizeEnsemble];
    for (int i = 0; i < sizeEnsemble; i++) {
      try {
        ensembleClassifiers[i] = (Learner) ClassOption.createObject(baseLearnerOption.getValueAsCLIString(),
                baseLearnerOption.getRequiredType());
      } catch (Exception e) {
        logger.error("Unable to create members of the ensemble. Please check your CLI parameters");
        e.printStackTrace();
        throw new IllegalArgumentException(e);
      }
      ensembleClassifiers[i].init(builder, this.dataset, 1); // sequential
    }
    
    BoostingPredictionCombinerProcessor predictionCombinerP = new BoostingPredictionCombinerProcessor(); //to change
    predictionCombinerP.setEnsembleSize(sizeEnsemble);
    this.builder.addProcessor(predictionCombinerP, 1);
    
    // Prediction Combiner output Stream
    resultStream = this.builder.createStream(predictionCombinerP);
    predictionCombinerP.setOutputStream(resultStream);
    
    // connect VHT result streams as input to GB_PredictionCombinerProcessor
    for (Learner classifier : ensembleClassifiers) {
      for (Stream subResultStream : classifier.getResultStreams()) {
        this.builder.connectInputKeyStream(subResultStream, predictionCombinerP);
      }
    }
    
    /* The (testing) ensemble streams from GB_DistributorProcessor to each ensemble member. */
    ensembleStreams = new Stream[sizeEnsemble];
    for (int i = 0; i < sizeEnsemble; i++) {
      ensembleStreams[i] = builder.createStream(gradientDistributorP);
      builder.connectInputKeyStream(ensembleStreams[i], ensembleClassifiers[i].getInputProcessor()); // connect streams one-to-one with ensemble members
    }

//    /* The prediction streams from GB_DistributorProcessor to each ensemble member. */
//    predictionStreams = new Stream[sizeEnsemble];
//    for (int i = 0; i < sizeEnsemble; i++) {
//      predictionStreams[i] = builder.createStream(gradientDistributorP);
//      builder.connectInputKeyStream(predictionStreams[i], ensembleClassifiers[i]
// .getInputProcessor()); // connect streams one-to-one with ensemble members
//    }
    
    gradientDistributorP.setOutputStreams(ensembleStreams);
//    gradientDistributorP.setPredictionStreams(predictionStreams);
    
    
    // Addition to Bagging: stream to train
    /* The training stream from GB_PredictionCombinerProcessor to GBDistributorProcessor. */
    Stream trainingStream = this.builder.createStream(predictionCombinerP);
    predictionCombinerP.setTrainingStream(trainingStream);
    this.builder.connectInputKeyStream(trainingStream, gradientDistributorP);
    
  }
  
  /**
   * The builder.
   */
  private TopologyBuilder builder;

  /*
   * (non-Javadoc)
   * 
   * @see samoa.classifiers.Classifier#init(samoa.engines.Engine,
   * samoa.core.Stream, weka.core.Instances)
   */
  
  @Override
  public void init(TopologyBuilder builder, Instances dataset, int parallelism) {
    this.builder = builder;
    this.dataset = dataset;
    this.parallelism = parallelism;
    this.setLayout();
  }
  
  @Override
  public Processor getInputProcessor() {
    return gradientDistributorP;
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see samoa.learners.Learner#getResultStreams()
   */
  @Override
  public Set<Stream> getResultStreams() {
    return ImmutableSet.of(this.resultStream);
  }
}
