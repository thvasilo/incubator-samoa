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

  private BoostModelProcessor boostModelProcessor;

  @Override
  public void init(TopologyBuilder topologyBuilder, Instances dataset, int parallelism) {

    int ensembleSize = 3;
    // Allocate an array for the local processors
    BoostLocalProcessor[] localEnsemble = new BoostLocalProcessor[ensembleSize];
    // Instantiate the model processor and add it to the topology
    boostModelProcessor = new BoostModelProcessor(new ToyBoost());
    topologyBuilder.addProcessor(boostModelProcessor);

    // Instantiate learner processors, and add them to the topology
    for (int i = 0; i < ensembleSize; i++) {
      SimpleClassifierAdapter localLearner = new SimpleClassifierAdapter(new HoeffdingTree(), dataset);
      // We add a local learner instance and the ensemble id to the local processor
      BoostLocalProcessor boostLocalProcessor = new BoostLocalProcessor(i, ensembleSize, localLearner);
      topologyBuilder.addProcessor(boostLocalProcessor);
      localEnsemble[i] = boostLocalProcessor;
    }

    // Connect the model processor to the first learner
    Stream learnerStream = topologyBuilder.createStream(boostModelProcessor);
    topologyBuilder.connectInputShuffleStream(learnerStream, localEnsemble[0]);
    boostModelProcessor.setLearnerStream(learnerStream);

    // These streams move events from learner to learner
    Stream[] ensembleStreams = new Stream[ensembleSize];
    // Instantiate the output streams for every learner
    for (int i = 0; i < ensembleSize; i++) {
      ensembleStreams[i] = topologyBuilder.createStream(localEnsemble[i]);
      localEnsemble[i].setOutputStream(ensembleStreams[i]);
    }
    // Connect each learner to the next
    for (int i = 1; i < ensembleSize; i++) {
      // Connect the output of the previous to the current learner
      topologyBuilder.connectInputKeyStream(localEnsemble[i - 1].getOutputStream(), localEnsemble[i]);
    }
    // Connect the last learner to the model processor, which handles the final output.
    topologyBuilder.connectInputShuffleStream(localEnsemble[ensembleSize - 1].getOutputStream(), boostModelProcessor);

    // Create the output stream from the model processor
    Stream outputStream = topologyBuilder.createStream(boostModelProcessor);
    boostModelProcessor.setOutputStream(outputStream);
  }

  @Override
  public Processor getInputProcessor() {
    // Connect the model processor to the input data
    return boostModelProcessor;
  }

  @Override
  public Set<Stream> getResultStreams() {
    // Have the output of the last learner as the output data
    return ImmutableSet.of(boostModelProcessor.getOutputStream());
  }
}
