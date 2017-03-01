package org.apache.samoa.learners.classifiers.ensemble.boosting;

import com.google.common.collect.ImmutableSet;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instances;
import org.apache.samoa.learners.ClassificationLearner;
import org.apache.samoa.learners.classifiers.SimpleClassifierAdapter;
import org.apache.samoa.moa.classifiers.trees.HoeffdingTree;
import org.apache.samoa.topology.Stream;
import org.apache.samoa.topology.TopologyBuilder;

import java.util.Set;

public class BoostLocal implements ClassificationLearner {

  private static final long serialVersionUID = -1853481790434442050L;

  // The ensemble of learners, each wrapped in a processor
  private BoostLocalProcessor[] localEnsemble;

  private int ensembleSize = 3;

  @Override
  public void init(TopologyBuilder topologyBuilder, Instances dataset, int parallelism) {

     localEnsemble = new BoostLocalProcessor[ensembleSize];

    // Instantiate learner processors, and add to topology
    for (int i = 0; i < ensembleSize; i++) {
      SimpleClassifierAdapter localLearner = new SimpleClassifierAdapter(new HoeffdingTree(), dataset);
      // We add a local learner instance and the ensemble id to the local processor
      BoostLocalProcessor boostLocalProcessor = new BoostLocalProcessor(i, ensembleSize, localLearner);
      topologyBuilder.addProcessor(boostLocalProcessor);
      localEnsemble[i] = boostLocalProcessor;
    }

    // These streams move events from learner to learner
    Stream[] ensembleStreams = new Stream[ensembleSize];

    // Instantiate the output streams
    for (int i = 0; i < ensembleSize; i++) {
      ensembleStreams[i] = topologyBuilder.createStream(localEnsemble[i]);
      localEnsemble[i].setOutputStream(ensembleStreams[i]);
    }

    for (int i = 1; i < ensembleSize; i++) {
      topologyBuilder.connectInputKeyStream(localEnsemble[i-1].getOutputStream(), localEnsemble[i]);
    }
  }

  @Override
  public Processor getInputProcessor() {
    // Connect the first learner to the input data
    return localEnsemble[0];
  }

  @Override
  public Set<Stream> getResultStreams() {
    // Have the output of the last learner as the output data
    return ImmutableSet.of(localEnsemble[ensembleSize - 1].getOutputStream());
  }
}
